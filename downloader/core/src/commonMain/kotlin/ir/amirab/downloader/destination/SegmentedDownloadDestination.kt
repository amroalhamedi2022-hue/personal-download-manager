package ir.amirab.downloader.destination

import ir.amirab.downloader.part.DownloadPart
import ir.amirab.downloader.utils.calcPercent
import okhttp3.internal.closeQuietly
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class SegmentedDownloadDestination(
    // a directory unique for this item!
    val tempDirectory: File,
    val getFileName: (DownloadPart) -> String,
    val getAllParts: () -> List<DownloadPart>,
    val appendMode: Boolean,
    outputFile: File,
) : DownloadDestination(outputFile) {
    private fun getFileOfPart(downloadPart: DownloadPart): File {
        val id = downloadPart.getID()
        return tempDirectory.resolve("$id")
    }

    override fun getWriterFor(part: DownloadPart): DestWriter {
        tempDirectory.mkdirs()
        val tFile = getFileOfPart(part)
        val writer = FileSystem.SYSTEM.openReadWrite(tFile.toOkioPath(), mustCreate = false, mustExist = false)
        val newSize = if (appendMode) {
            writer.size()
        } else {
            // part starts from the beginning
            0
        }
        writer.resize(newSize)
        val destWriter = DestWriter(
            id = part.getID(),
            file = tFile,
            seekPos = newSize,
            writer = writer,
        )
        synchronized(this) {
            fileParts.add(destWriter)
        }
        return destWriter
    }

    override fun onPartCancelled(part: DownloadPart) {
        super.onPartCancelled(part)
        val id = part.getID()
        synchronized(this) {
            fileParts
                .find { it.id == id }
                ?.writer
                ?.closeQuietly()
        }
    }


    override fun canGetFileWriter(): Boolean {
        return true
    }

    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) {

    }

    override suspend fun isDownloadedPartsIsValid(): Boolean {
        return tempDirectory.exists()
    }

    fun isDownloadPartValid(part: DownloadPart): Boolean {
        return getFileOfPart(part).exists()
    }

    override fun cleanUpJunkFiles() {
        runCatching {
            FileSystem.SYSTEM.deleteRecursively(tempDirectory.toOkioPath())
        }
    }

    override fun onAllPartsCompleted(onProgressUpdate: (Int?) -> Unit) {
        assemble(
            getAllParts()
                .sortedBy { it.getID() }
                .map { tempDirectory.resolve(getFileName(it)) },
            outputFile,
            onProgressUpdate
        )
        super.onAllPartsCompleted(onProgressUpdate)
    }

    /**
     * Assemble into an independent sibling first. Never truncate an unrelated
     * destination if the download name became occupied after it was queued.
     * The original media segments remain intact until publication succeeds.
     */
    fun assemble(
        sources: List<File>,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        require(sources.isNotEmpty()) { "Cannot publish an empty HLS segment list" }
        DownloadDestination.prepareDestinationFolder(destination)
        val expected = sources.sumOf { segment ->
            if (!segment.isFile) throw IOException("Missing HLS media segment: ${segment.name}")
            segment.length()
        }
        val target = destination.toPath()
        val staged = Files.createTempFile(target.parent, ".hyperget-assemble-", ".tmp")
        try {
            var totalWritten = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var progress = 0
            Files.newOutputStream(staged, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { dst ->
                sources.forEach { sourceFile ->
                    sourceFile.inputStream().use { src ->
                        while (true) {
                            val read = src.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            dst.write(buffer, 0, read)
                            totalWritten += read
                            val newPercent = calcPercent(totalWritten, expected)
                            if (newPercent != progress && newPercent < 100) {
                                progress = newPercent
                                onProgress(newPercent)
                            }
                        }
                    }
                }
            }
            if (totalWritten != expected) {
                throw IOException("HLS media segments changed during assembly")
            }
            // A hard-link makes the final file visible atomically and can never
            // replace an existing name. It works for normal Windows NTFS volumes.
            // Use no-replacement copy as compatibility fallback for file systems
            // that do not support hard links (e.g., FAT/external/network storage).
            try {
                Files.createLink(target, staged)
            } catch (alreadyExists: FileAlreadyExistsException) {
                throw alreadyExists
            } catch (unsupported: UnsupportedOperationException) {
                Files.copy(staged, target)
            } catch (unsupported: IOException) {
                if (Files.exists(target)) throw unsupported
                Files.copy(staged, target)
            }
            onProgress(100)
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    override fun flush() {
        synchronized(this) {
            fileParts.forEach {
                it.writer.flush()
            }
        }
    }
}
