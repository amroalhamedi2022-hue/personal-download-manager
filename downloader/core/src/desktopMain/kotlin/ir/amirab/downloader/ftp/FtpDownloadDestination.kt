package ir.amirab.downloader.ftp

import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.part.DownloadPart
import java.io.File
import java.nio.file.Files

/**
 * An FTP transfer writes through FtpTransferClient into a staged .ftp-part file.
 * The verified transport itself commits the staged file before the job finishes;
 * it must never be mapped onto the HTTP part writer or assembled a second time.
 */
class FtpDownloadDestination(file: File) : DownloadDestination(file) {
    private val partial: File get() = File(outputFile.path + ".ftp-part")
    private val identity: File get() = File(outputFile.path + ".ftp-part.properties")

    override fun getWriterFor(part: DownloadPart): DestWriter =
        throw UnsupportedOperationException("FTP is written by the streaming FTP transport")

    override fun canGetFileWriter(): Boolean = false

    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) =
        Unit

    override suspend fun isDownloadedPartsIsValid(): Boolean =
        outputFile.isFile || partial.isFile

    override fun flush() = Unit

    override fun cleanUpJunkFiles() {
        Files.deleteIfExists(partial.toPath())
        Files.deleteIfExists(identity.toPath())
    }

    override fun moveOutput(to: File) {
        val newOutput = to.canonicalFile.absoluteFile
        if (newOutput == outputFile) return
        DownloadDestination.prepareDestinationFolder(newOutput)
        val newPartial = File(newOutput.path + ".ftp-part")
        val newIdentity = File(newOutput.path + ".ftp-part.properties")
        require(!newOutput.exists() && !newPartial.exists() && !newIdentity.exists()) {
            "The FTP destination or partial file already exists"
        }
        // Moves occur only with the transfer paused. Keep the sidecar paired with its data.
        // A paused FTP download can move between different disks. Requiring
        // ATOMIC_MOVE rejects cross-volume paths on Windows. Keep the partial
        // and its resume identity together, and roll both back on failure.
        val moved = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()
        try {
            if (partial.exists()) {
                Files.move(partial.toPath(), newPartial.toPath())
                moved.add(partial.toPath() to newPartial.toPath())
            }
            if (identity.exists()) {
                Files.move(identity.toPath(), newIdentity.toPath())
                moved.add(identity.toPath() to newIdentity.toPath())
            }
            super.moveOutput(newOutput)
        } catch (failure: Exception) {
            for ((source, target) in moved.asReversed()) {
                try {
                    Files.move(target, source)
                } catch (rollback: Exception) {
                    failure.addSuppressed(rollback)
                }
            }
            throw failure
        }
    }
}
