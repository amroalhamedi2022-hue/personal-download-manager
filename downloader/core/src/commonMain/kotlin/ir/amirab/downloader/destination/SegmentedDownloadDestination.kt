package ir.amirab.downloader.destination

import ir.amirab.downloader.part.DownloadPart
import ir.amirab.downloader.part.MediaSegment
import ir.amirab.downloader.utils.calcPercent
import okhttp3.internal.closeQuietly
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

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
        val path = getFileOfPart(part).toPath()
        // A single NOFOLLOW_LINKS attribute read rejects symlinked stage paths
        // and avoids trusting mere existence or a truncated completed segment.
        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull() ?: return false
        return isStagedSegmentValid(
            isRegularFile = attributes.isRegularFile,
            actualSize = attributes.size(),
            expectedSize = (part as? MediaSegment)?.length,
        )
    }

    override fun cleanUpJunkFiles() {
        runCatching {
            FileSystem.SYSTEM.deleteRecursively(tempDirectory.toOkioPath())
        }
    }

    override fun onAllPartsCompleted(onProgressUpdate: (Int?) -> Unit) {
        val completedParts = getAllParts().sortedBy { it.getID() }
        // A persisted completion flag is not proof that the stage file still
        // exists or has the expected bytes at the moment of final publication.
        // Never open/truncate the user's destination before validating ALL parts.
        if (completedParts.isEmpty() || completedParts.any { !it.isCompleted || !isDownloadPartValid(it) }) {
            throw java.io.IOException("HLS staged segments are incomplete or invalid")
        }
        assemble(
            completedParts.map { tempDirectory.resolve(getFileName(it)) },
            outputFile,
            onProgressUpdate
        )
        super.onAllPartsCompleted(onProgressUpdate)
    }

    fun assemble(
        sources: List<File>,
        destination: File,
        onProgress: (Int) -> Unit
    ) {
        require(sources.isNotEmpty()) { "Cannot assemble an empty segment list" }
        DownloadDestination.prepareDestinationFolder(destination)
        val snapshots = sources.map { source ->
            val attributes = Files.readAttributes(
                source.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
            )
            if (!attributes.isRegularFile || attributes.size() <= 0L) {
                throw java.io.IOException("HLS staged segment is not a nonempty regular file")
            }
            attributes.size()
        }
        val totalLength = snapshots.sum()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalWritten = 0L
        var percent = 0
        // Assemble beside the destination and publish only after a complete copy.
        // A failed read must not leave a truncated, user-visible output file.
        val temporary = Files.createTempFile(
            destination.parentFile.toPath(), ".hyperget-hls-", ".assembling"
        )
        try {
            Files.newOutputStream(temporary).use { dst ->
                sources.forEachIndexed { index, sourceFile ->
                    var copied = 0L
                    sourceFile.inputStream().use { input ->
                        onProgress(percent)
                        while (true) {
                            val len = input.read(buffer)
                            if (len == -1) break
                            dst.write(buffer, 0, len)
                            copied += len
                            totalWritten += len
                            if (copied > snapshots[index]) {
                                throw java.io.IOException("HLS staged segment grew during assembly")
                            }
                            val newPercent = calcPercent(totalWritten, totalLength)
                            if (newPercent != percent) {
                                onProgress(newPercent)
                                percent = newPercent
                            }
                        }
                    }
                    if (copied != snapshots[index] || !isStagedSegmentValid(
                            isRegularFile = Files.readAttributes(
                                sourceFile.toPath(), BasicFileAttributes::class.java,
                                LinkOption.NOFOLLOW_LINKS
                            ).isRegularFile,
                            actualSize = Files.size(sourceFile.toPath()),
                            expectedSize = snapshots[index],
                        )) {
                        throw java.io.IOException("HLS staged segment changed during assembly")
                    }
                }
            }
            if (totalWritten != totalLength) {
                throw java.io.IOException("HLS assembly byte count mismatch")
            }
            // Do not silently overwrite an existing download on retry/rename.
            Files.move(temporary, destination.toPath())
            onProgress(100)
        } finally {
            Files.deleteIfExists(temporary)
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
