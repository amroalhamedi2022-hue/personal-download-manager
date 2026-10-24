package ir.amirab.downloader.ftp

import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Never follow an attacker-controlled .ftp-part symbolic link when measuring or
 * opening a resumable FTP download. The staged file must be a regular file.
 */
internal fun ftpStagedBytes(part: Path): Long {
    val attributes = try {
        Files.readAttributes(part, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        return 0L
    }
    if (!attributes.isRegularFile) {
        throw IOException("FTP staged path is not a regular file")
    }
    return attributes.size()
}

/** The file key identifies the opened stage for a later pre-publish check. */
internal class FtpStagedOutput internal constructor(
    private val delegate: OutputStream,
    val fileKey: Any?,
) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}

/**
 * Open the real staged file without following symbolic links. Recheck its
 * length and, when the file provider supports it, its file key around opening.
 * The returned stream owns the channel; keep its fileKey for final publication.
 */
internal fun openFtpStagedOutput(part: Path, expectedSize: Long): FtpStagedOutput {
    require(expectedSize >= 0L)
    val before = try {
        Files.readAttributes(part, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }
    if (before != null && !before.isRegularFile) {
        throw IOException("FTP staged path is not a regular file")
    }
    val options: Set<OpenOption> = setOf(
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND,
        LinkOption.NOFOLLOW_LINKS,
    )
    val channel = Files.newByteChannel(part, options)
    try {
        val after = Files.readAttributes(part, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!after.isRegularFile || channel.size() != expectedSize || after.size() != expectedSize) {
            throw IOException("FTP staged file changed while opening; refusing unsafe resume")
        }
        if (before?.fileKey() != null && before.fileKey() != after.fileKey()) {
            throw IOException("FTP staged file identity changed while opening")
        }
        return FtpStagedOutput(Channels.newOutputStream(channel), after.fileKey())
    } catch (failure: Throwable) {
        channel.close()
        throw failure
    }
}
