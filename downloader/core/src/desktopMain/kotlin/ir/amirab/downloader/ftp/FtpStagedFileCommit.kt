package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.FileSystemException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

/**
 * Publish a completed FTP part without silently replacing a file created by
 * another process while the network transfer was running.
 *
 * ATOMIC_MOVE does not promise no-replace: on some file systems it overwrites
 * an existing target even when REPLACE_EXISTING was not requested. Because the
 * part and destination share a directory, a hard link can publish the fully
 * written bytes atomically with an exclusive destination name. The fallback
 * supports file systems without hard links, using a no-replace Files.move;
 * that fallback does not promise cross-process atomicity on every provider.
 *
 * The pre-publish check rejects a staged path swapped after its stream was
 * opened. On providers without fileKey (including some Windows providers),
 * creationTime adds a best-effort identity check. A hostile actor able to
 * forge timestamps or race the final check is not fully prevented.
 */
internal fun publishFtpStagedFile(
    part: Path,
    destination: Path,
    expectedSize: Long? = null,
    expectedFileKey: Any? = null,
    expectedCreationTime: FileTime? = null,
) {
    val attributes = Files.readAttributes(part, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    if (!attributes.isRegularFile) {
        throw IOException("FTP staged path is not a regular file at publication")
    }
    if (expectedSize != null && attributes.size() != expectedSize) {
        throw IOException("FTP staged size changed before publication")
    }
    if (expectedCreationTime != null && attributes.creationTime() != expectedCreationTime) {
        throw IOException("FTP staged creation time changed before publication")
    }
    if (expectedFileKey != null && attributes.fileKey() != expectedFileKey) {
        throw IOException("FTP staged identity changed before publication")
    }
    try {
        Files.createLink(destination, part)
    } catch (alreadyExists: FileAlreadyExistsException) {
        throw alreadyExists
    } catch (_: UnsupportedOperationException) {
        Files.move(part, destination)
        return
    } catch (error: FileSystemException) {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(destination.toString())
        }
        // FAT/exFAT and some network shares cannot create hard links.
        // Never request REPLACE_EXISTING or ATOMIC_MOVE here.
        Files.move(part, destination)
        return
    }
    // The destination is now a complete file. A failed cleanup leaves an extra
    // staged link rather than destroying or rewriting the published bytes.
    Files.deleteIfExists(part)
}
