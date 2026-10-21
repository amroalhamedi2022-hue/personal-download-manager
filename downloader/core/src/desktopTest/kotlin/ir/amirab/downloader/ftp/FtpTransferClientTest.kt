package ir.amirab.downloader.ftp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.db.MemoryDownloadListDB
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test
    fun missingRemoteFileDoesNotLeaveMisleadingResumeMetadata() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("does-not-exist.bin")
        val exception = assertFailsWith<IOException> {
            FtpTransferClient().download(
                fixture.url("missing-remote.bin"), destination,
                username = "test", password = "secret"
            )
        }
        assertTrue(exception.message.orEmpty().contains("FTP RETR rejected"))
        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(fixture.downloadFolder.resolve("does-not-exist.bin.ftp-part")))
        assertFalse(Files.exists(fixture.downloadFolder.resolve("does-not-exist.bin.ftp-part.properties")))
    }

    @Test
    fun existingDestinationRemainsByteForByteUntouched() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("protected.bin")
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        Files.write(destination, original)
        assertFailsWith<IOException> {
            FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "secret")
        }
        assertContentEquals(original, Files.readAllBytes(destination))
        assertFalse(Files.exists(fixture.downloadFolder.resolve("protected.bin.ftp-part")))
    }

    @Test
    fun partialResumeSidecarNeverStoresLoginPassword() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("metadata.bin")
        assertFailsWith<IOException> {
            FtpTransferClient().download(fixture.url("fixture.bin"), destination, "test", "secret") { done, _ ->
                if (done >= 16 * 1024) throw IOException("Intentional interruption for sidecar audit")
            }
        }
        val metadata = fixture.downloadFolder.resolve("metadata.bin.ftp-part.properties")
        assertTrue(Files.isRegularFile(metadata))
        val contents = Files.readString(metadata)
        assertFalse(contents.contains("secret"), "FTP password must not be persisted in resume sidecars")
        assertFalse(contents.contains("password", ignoreCase = true), "FTP sidecar must contain only source identity")
    }

    @Test
    fun boundedFtpDownloadUsesSharedSpeedLimiterWithoutByteLoss() = withFixture { fixture ->
        val destination = fixture.downloadFolder.resolve("speed-limited.bin")
        val result = FtpTransferClient().download(
            url = fixture.url("fixture.bin"),
            output = destination,
            username = "test",
            password = "secret",
            speedLimitBytesPerSecond = 50L * 1024 * 1024,
        )
        assertEquals(fixture.bytes.size.toLong(), result.transferredBytes)
        assertContentEquals(fixture.bytes, Files.readAllBytes(destination))
        assertFailsWith<IllegalArgumentException> {
            FtpTransferClient().download(
                url = fixture.url("fixture.bin"),
                output = fixture.downloadFolder.resolve("invalid-speed.bin"),
                speedLimitBytesPerSecond = -1L,
            )
        }
        Unit // JUnit 4 requires an actual void @Test method, not a Throwable result.
    }

    @Test
    fun realFtpJobUsesManagerLifecycleAndTransientCredentials() = withFixture { fixture ->
        runBlocking {
            val item = FtpDownloadItem(
                id = 71L,
                folder = fixture.downloadFolder.toString(),
                name = "manager-downloaded.bin",
                link = fixture.url("fixture.bin"),
                username = "test",
                dateAdded = 71L,
            )
            val listDb = MemoryDownloadListDB()
            listDb.add(item)
            val provider = FtpCredentialsProvider { FtpSessionCredentials("test", "secret") }
            val registry = DownloaderRegistry().apply { add(FtpDownloader(provider)) }
            val manager = DownloadManager(
                dlListDb = listDb,
                partListDb = MemoryDownloadPartStatesDB(),
                settings = DownloadSettings(),
                emptyFileCreator = EmptyFileCreator(
                    diskStat = object : IDiskStat {
                        override fun getRemainingSpace(path: java.io.File) = Long.MAX_VALUE
                    },
                    useSparseFile = { false },
                ),
                downloaderRegistry = registry,
                downloadDataFolder = fixture.downloadFolder.toFile(),
            )
            try {
                manager.boot()
                assertEquals(1, manager.downloadJobs.size)
                manager.resume(item.id)
                withTimeout(25_000) {
                    while (item.status != DownloadStatus.Completed) delay(20)
                }
                assertContentEquals(
                    fixture.bytes,
                    Files.readAllBytes(fixture.downloadFolder.resolve("manager-downloaded.bin")),
                )
                assertEquals(fixture.bytes.size.toLong(), item.contentLength)
                assertEquals(DownloadStatus.Completed, listDb.getById(item.id)?.status)
                assertFalse(FtpSessionCredentials("test", "secret").toString().contains("secret"))
                assertFalse(
                    item.toString().contains("secret"),
                    "FTP persisted item should never contain a password",
                )
            } finally {
                manager.downloadJobs.forEach { it.close() }
                manager.scope.cancel()
            }
        }
    }

    @Test
    fun stopAllCancelsFtpWaitingOnHostPermitAndNextResumeFinishes() = withFixture { fixture ->
        runBlocking {
            val item = FtpDownloadItem(
                id = 72L,
                folder = fixture.downloadFolder.toString(),
                name = "gate.bin",
                link = fixture.url("fixture.bin"),
                username = "test",
                dateAdded = 72L,
            )
            val database = MemoryDownloadListDB()
            database.add(item)
            val manager = DownloadManager(
                dlListDb = database,
                partListDb = MemoryDownloadPartStatesDB(),
                settings = DownloadSettings(),
                emptyFileCreator = EmptyFileCreator(
                    diskStat = object : IDiskStat {
                        override fun getRemainingSpace(path: java.io.File) = Long.MAX_VALUE
                    },
                    useSparseFile = { false },
                ),
                downloaderRegistry = DownloaderRegistry().apply {
                    add(FtpDownloader(FtpCredentialsProvider {
                        FtpSessionCredentials("test", "secret")
                    }))
                },
                downloadDataFolder = fixture.downloadFolder.toFile(),
            )
            manager.configureHostLimits("127.0.0.1", maxConnections = 1)
            val heldPermit = manager.hostLimitRegistry.acquireForHost("127.0.0.1")
            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                manager.resume(item.id)
                assertTrue(job.status.value is DownloadJobStatus.Resuming)
                delay(150)
                assertEquals(1, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
                assertFalse(Files.exists(fixture.downloadFolder.resolve("gate.bin.ftp-part")))
                withTimeout(5_000) { manager.stopAll() }
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                assertFalse(job.isDownloadActive.value)
                heldPermit.close()
                manager.resume(item.id)
                withTimeout(25_000) {
                    while (item.status != DownloadStatus.Completed) delay(20)
                }
                assertContentEquals(fixture.bytes, Files.readAllBytes(fixture.downloadFolder.resolve("gate.bin")))
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
            } finally {
                heldPermit.close()
                manager.downloadJobs.forEach { it.close() }
                manager.scope.cancel()
            }
        }
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
