package ir.amirab.downloader.ftp

import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Integration tests, using a genuine local Apache FTP server (not a mocked protocol). */
class FtpTransferClientTest {
    @Test
    fun binaryFtpTransfersAllBytesAndCommitsOnlyCompletedFile() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("binary.bin")
        val client = FtpTransferClient()
        val seen = mutableListOf<Long>()
        val result = client.download(
            fixture.url("fixture.bin"), destination,
            username = "test", password = "secret",
            onProgress = { done, _ -> seen.add(done) },
        )
        assertEquals(fixture.bytes.size.toLong(), result.transferredBytes)
        assertEquals(0L, result.resumedFrom)
        assertContentEquals(fixture.bytes, Files.readAllBytes(destination))
        assertEquals(sha256(fixture.bytes), sha256(Files.readAllBytes(destination)))
        assertTrue(seen.isNotEmpty())
        assertFalse(Files.exists(fixture.downloadFolder.resolve("binary.bin.ftp-part")))
        assertFalse(Files.exists(fixture.downloadFolder.resolve("binary.bin.ftp-part.properties")))
    }

    @Test
    fun abortMidstreamLeavesPartialThenRestartsAtVerifiedRestOffset() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("resumed.bin")
        val first = FtpTransferClient()
        assertFailsWith<IOException> {
            first.download(fixture.url("fixture.bin"), destination, "test", "secret") { done, _ ->
                if (done >= 16 * 1024) throw IOException("Intentional test interruption")
            }
        }
        val part = fixture.downloadFolder.resolve("resumed.bin.ftp-part")
        val metadata = fixture.downloadFolder.resolve("resumed.bin.ftp-part.properties")
        assertTrue(Files.exists(part))
        assertTrue(Files.exists(metadata))
        val preserved = Files.size(part)
        assertTrue(preserved > 0)
        assertTrue(preserved < fixture.bytes.size.toLong())
        assertFalse(Files.exists(destination))

        val result = FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "secret")
        assertEquals(preserved, result.resumedFrom)
        assertEquals(fixture.bytes.size - preserved, result.transferredBytes)
        assertContentEquals(fixture.bytes, Files.readAllBytes(destination))
        assertFalse(Files.exists(part))
        assertFalse(Files.exists(metadata))
    }

    @Test
    fun remoteFileChangingBetweenInterruptAndResumeCannotCorruptLocalOutput() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("changed.bin")
        assertFailsWith<IOException> {
            FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "secret") { done, _ ->
                if (done >= 16 * 1024) throw IOException("Intentional interruption")
            }
        }
        val part = fixture.downloadFolder.resolve("changed.bin.ftp-part")
        val preserved = Files.readAllBytes(part)
        val altered = fixture.bytes + byteArrayOf(11, 12, 13)
        Files.write(fixture.root.resolve("fixture.bin"), altered)
        val error = assertFailsWith<IOException> {
            FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "secret")
        }
        assertTrue(error.message.orEmpty().contains("identity"))
        assertContentEquals(preserved, Files.readAllBytes(part))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun invalidCredentialsOrUnsupportedSchemeNeverCreateOutput() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("unauthorized.bin")
        assertFailsWith<IOException> {
            FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "wrong-password")
        }
        assertFalse(Files.exists(destination))
        assertFailsWith<IllegalArgumentException> {
            FtpTransferClient().download("https://127.0.0.1/no.bin", destination, "test", "secret")
        }
        assertFailsWith<IllegalArgumentException> {
            FtpTransferClient().download("ftp://test:secret@127.0.0.1/no.bin", destination)
        }
        assertFalse(Files.exists(destination))
    }

    private class Fixture(
        val root: Path,
        val downloadFolder: Path,
        val bytes: ByteArray,
        val port: Int,
        val server: org.apache.ftpserver.FtpServer,
    ) : AutoCloseable {
        fun url(filename: String) = "ftp://127.0.0.1:$port/$filename"
        override fun close() {
            server.stop()
            root.toFile().deleteRecursively()
            downloadFolder.toFile().deleteRecursively()
        }
    }

    private fun <T> withFixture(block: (Fixture) -> T): T {
        val root = Files.createTempDirectory("idm-real-ftp-root-")
        val downloadFolder = Files.createTempDirectory("idm-real-ftp-download-")
        val content = ByteArray(2 * 1024 * 1024) { ((it * 29 + 17) % 253).toByte() }
        Files.write(root.resolve("fixture.bin"), content)
        val port = ServerSocket(0).use { it.localPort }
        val listenerFactory = ListenerFactory().apply {
            serverAddress = "127.0.0.1"
            this.port = port
        }
        val serverFactory = FtpServerFactory()
        serverFactory.addListener("default", listenerFactory.createListener())
        val user = BaseUser().apply {
            name = "test"
            password = "secret"
            homeDirectory = root.toAbsolutePath().toString()
        }
        val userDbFile = root.resolve("ftp-test-users.properties")
        // FtpServer 1.2.1 requires the properties file to exist before manager construction.
        Files.createFile(userDbFile)
        val userManager = PropertiesUserManagerFactory().apply {
            // Never touch host/project credentials or any user-local settings.
            file = userDbFile.toFile()
        }.createUserManager()
        userManager.save(user)
        serverFactory.userManager = userManager
        val server = serverFactory.createServer()
        server.start()
        return Fixture(root, downloadFolder, content, port, server).use(block)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
