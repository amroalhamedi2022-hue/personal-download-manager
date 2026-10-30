package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.LinkOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source regression for staged-path replacement between RETR and final publish.
 * Actual Windows FTP integration and cross-process race testing remain separate gates.
 */
class FtpStagedIdentityTest {
    private inline fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("hyperget-ftp-stage-identity-junit-")
        try { block(dir) } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun completedStagePublishesExpectedBytesAndRemovesPartial() = withDir { dir ->
        val stage = dir.resolve("safe.bin.ftp-part")
        val destination = dir.resolve("safe.bin")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val opened = openFtpStagedOutput(stage, 0)
        opened.use { it.write(bytes) }
        publishFtpStagedFile(stage, destination, bytes.size.toLong(), opened.fileKey)
        assertContentEquals(bytes, Files.readAllBytes(destination))
        assertFalse(Files.exists(stage))
    }

    @Test
    fun truncatedStagedFileCannotBePublished() = withDir { dir ->
        val stage = dir.resolve("short.bin.ftp-part")
        val destination = dir.resolve("short.bin")
        Files.write(stage, byteArrayOf(1, 2))
        assertFailsWith<IOException> { publishFtpStagedFile(stage, destination, 3) }
        assertFalse(Files.exists(destination))
        assertTrue(Files.exists(stage))
    }

    @Test
    fun sameSizeReplacementCannotBePublishedWithPortableIdentity() = withDir { dir ->
        val stage = dir.resolve("swapped.bin.ftp-part")
        val destination = dir.resolve("swapped.bin")
        val movedOriginal = dir.resolve("original-stage.bin")
        val opened = openFtpStagedOutput(stage, 0)
        opened.use { it.write(byteArrayOf(1, 2, 3)) }
        Files.move(stage, movedOriginal)
        Files.write(stage, byteArrayOf(7, 8, 9))
        Files.setAttribute(stage, "basic:creationTime", FileTime.fromMillis(opened.creationTime.toMillis() - 60_000L))
        assertFailsWith<IOException> {
            publishFtpStagedFile(stage, destination, 3, opened.fileKey, opened.creationTime)
        }
        assertFalse(Files.exists(destination))
        assertContentEquals(byteArrayOf(7, 8, 9), Files.readAllBytes(stage))
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(movedOriginal))
    }

    @Test
    fun creationTimeFallbackRejectsSameSizeSwapWithoutFileKey() = withDir { dir ->
        val stage = dir.resolve("fallback.bin.ftp-part")
        val destination = dir.resolve("fallback.bin")
        val saved = dir.resolve("saved.bin")
        val opened = openFtpStagedOutput(stage, 0)
        opened.use { it.write(byteArrayOf(10, 20, 30)) }
        Files.move(stage, saved)
        Files.write(stage, byteArrayOf(40, 50, 60))
        Files.setAttribute(stage, "basic:creationTime", FileTime.fromMillis(opened.creationTime.toMillis() - 60_000L))
        val changed = Files.readAttributes(stage, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (changed.creationTime() != opened.creationTime) {
            assertFailsWith<IOException> {
                publishFtpStagedFile(stage, destination, 3, null, opened.creationTime)
            }
            assertFalse(Files.exists(destination))
            assertContentEquals(byteArrayOf(40, 50, 60), Files.readAllBytes(stage))
            assertContentEquals(byteArrayOf(10, 20, 30), Files.readAllBytes(saved))
        }
    }
}
