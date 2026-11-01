package ir.amirab.downloader.ftp

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtpDownloadDestinationTest {
    @Test
    fun movingPausedDestinationMovesPartAndIdentityTogether() {
        val dir = Files.createTempDirectory("ftp-destination-")
        try {
            val oldOutput = dir.resolve("old.bin")
            val newOutput = dir.resolve("new.bin")
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            Files.write(dir.resolve("old.bin.ftp-part"), bytes)
            Files.writeString(dir.resolve("old.bin.ftp-part.properties"), "endpoint=ftp://example.com:21/a.bin")
            val destination = FtpDownloadDestination(oldOutput.toFile())
            destination.moveOutput(newOutput.toFile())

            assertFalse(Files.exists(oldOutput))
            assertFalse(Files.exists(dir.resolve("old.bin.ftp-part")))
            assertFalse(Files.exists(dir.resolve("old.bin.ftp-part.properties")))
            assertContentEquals(bytes, Files.readAllBytes(dir.resolve("new.bin.ftp-part")))
            assertTrue(Files.readString(dir.resolve("new.bin.ftp-part.properties")).contains("endpoint="))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun movingPausedTransferToDifferentFolderKeepsFileAndMetadataPaired() {
        val root = Files.createTempDirectory("ftp-relocate-")
        try {
            val source = Files.createDirectory(root.resolve("source"))
            val target = Files.createDirectory(root.resolve("target"))
            val bytes = ByteArray(16384) { (it % 251).toByte() }
            val metadata = "endpoint=ftp://example.com:21/large.bin"
            Files.write(source.resolve("large.bin.ftp-part"), bytes)
            Files.writeString(source.resolve("large.bin.ftp-part.properties"), metadata)
            val old = source.resolve("large.bin")
            val moved = target.resolve("large.bin")
            FtpDownloadDestination(old.toFile()).moveOutput(moved.toFile())
            assertFalse(Files.exists(source.resolve("large.bin.ftp-part")))
            assertFalse(Files.exists(source.resolve("large.bin.ftp-part.properties")))
            assertContentEquals(bytes, Files.readAllBytes(target.resolve("large.bin.ftp-part")))
            assertEquals(metadata, Files.readString(target.resolve("large.bin.ftp-part.properties")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun targetConflictCannotOverwriteACompletedFileOrPartial() {
        val dir = Files.createTempDirectory("ftp-destination-conflict-")
        try {
            val oldOutput = dir.resolve("old.bin")
            val destination = FtpDownloadDestination(oldOutput.toFile())
            val oldPartial = dir.resolve("old.bin.ftp-part")
            Files.write(oldPartial, byteArrayOf(11, 22))
            val other = dir.resolve("existing.bin")
            Files.write(other, byteArrayOf(33, 44))
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                destination.moveOutput(other.toFile())
            }
            assertContentEquals(byteArrayOf(11, 22), Files.readAllBytes(oldPartial))
            assertContentEquals(byteArrayOf(33, 44), Files.readAllBytes(other))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun cleanupDeletesOnlyStagingNotCompletedFile() {
        val dir = Files.createTempDirectory("ftp-destination-cleanup-")
        try {
            val finished = dir.resolve("good.bin")
            Files.write(finished, byteArrayOf(7, 8, 9))
            val part = dir.resolve("good.bin.ftp-part")
            val sidecar = dir.resolve("good.bin.ftp-part.properties")
            Files.write(part, byteArrayOf(1))
            Files.writeString(sidecar, "metadata")
            FtpDownloadDestination(finished.toFile()).cleanUpJunkFiles()
            assertContentEquals(byteArrayOf(7, 8, 9), Files.readAllBytes(finished))
            assertFalse(Files.exists(part))
            assertFalse(Files.exists(sidecar))
            assertEquals(3L, Files.size(finished))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
