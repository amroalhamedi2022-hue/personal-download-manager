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

/**
 * Open only the real staged file, without following symbolic links. Recheck its
 * length at the point of opening so a changed/replaced partial cannot silently
 * append bytes at an incorrect REST offset. The returned stream owns the channel.
 */
internal fun openFtpStagedOutput(part: Path, expectedSize: Long): OutputStream {
    require(expectedSize >= 0L)
    val options: Set<OpenOption> = setOf(
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND,
        LinkOption.NOFOLLOW_LINKS,
    )
    val channel = Files.newByteChannel(part, options)
    try {
        if (channel.size() != expectedSize) {
            throw IOException("FTP staged file changed while opening; refusing unsafe resume")
        }
        return Channels.newOutputStream(channel)
    } catch (failure: Throwable) {
        channel.close()
        throw failure
    }
}
