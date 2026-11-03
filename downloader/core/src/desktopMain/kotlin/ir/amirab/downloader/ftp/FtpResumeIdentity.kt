package ir.amirab.downloader.ftp

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

/**
 * A resume sidecar is untrusted local input, not a reason to read arbitrary
 * files through symlinks or consume unbounded memory before a network RETR.
 * Return null only when it is genuinely absent; reject unsafe existing paths.
 */
internal fun loadFtpResumeIdentity(path: Path): Properties? {
    val before = try {
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        return null
    }
    if (!before.isRegularFile) throw IOException("FTP resume identity is not a regular file")
    val maxBytes = 16 * 1024
    if (before.size() > maxBytes) throw IOException("FTP resume identity exceeds size limit")
    val bytes = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
        it.readNBytes(maxBytes + 1)
    }
    if (bytes.size > maxBytes) throw IOException("FTP resume identity exceeds size limit")
    val after = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    if (!after.isRegularFile || (before.fileKey() != null && before.fileKey() != after.fileKey())) {
        throw IOException("FTP resume identity changed while reading")
    }
    return try {
        Properties().apply { ByteArrayInputStream(bytes).use(::load) }
    } catch (_: IllegalArgumentException) {
        throw IOException("FTP resume identity is malformed")
    }
}
