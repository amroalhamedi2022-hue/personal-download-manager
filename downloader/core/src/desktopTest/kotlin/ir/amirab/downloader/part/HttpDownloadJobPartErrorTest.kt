package ir.amirab.downloader.part

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.db.MemoryDownloadListDB
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadJob
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.exception.TooManyErrorException
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpDownloadJobPartErrorTest {

    private fun newProps(item: HttpDownloadItem) = NewDownloadItemProps(
        downloadItem = item,
        extraConfig = null,
        onDuplicateStrategy = OnDuplicateStrategy.AddNumbered,
        context = EmptyContext,
    )

    @Test
    fun singlePartFailureDoesNotLeavePartStalledAndCompletesDownload() = runBlocking {
        val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
        val failedPartIndex = AtomicInteger(0)

        withPartErrorFixture(bytes) { fixture ->
            fixture.onRequest = { exchange, start, _ ->
                if (start > 0 && failedPartIndex.getAndIncrement() == 0) {
                    exchange.sendResponseHeaders(503, -1)
                    exchange.close()
                    true
                } else {
                    false
                }
            }

            val targetDir = Files.createTempDirectory("part-error-test-").toFile()
            val manager = createTestManager(fixture.client, targetDir)
            val item = HttpDownloadItem(
                link = fixture.url,
                id = 101L,
                folder = targetDir.absolutePath,
                name = "test.bin",
                preferredConnectionCount = 2,
            )

            try {
                manager.boot()
                manager.addDownload(newProps(item))
                val job = manager.downloadJobs.single() as HttpDownloadJob

                withTimeout(15_000) {
                    job.resume()
                    job.status.first { it is DownloadJobStatus.Finished }
                }

                assertEquals(DownloadStatus.Completed, job.downloadItem.status)
                val targetFile = File(targetDir, job.downloadItem.name)
                assertTrue(targetFile.exists())
                assertContentEquals(bytes, targetFile.readBytes())
            } finally {
                manager.downloadJobs.forEach { it.close() }
                targetDir.deleteRecursively()
            }
        }
    }

    @Test
    fun permanentFailureExhaustsRetryBudgetAndTransitionsToError() = runBlocking {
        val bytes = ByteArray(64 * 1024) { it.toByte() }

        withPartErrorFixture(bytes) { fixture ->
            fixture.onRequest = { exchange, start, _ ->
                if (start > 0) {
                    exchange.sendResponseHeaders(500, -1)
                    exchange.close()
                    true
                } else false
            }

            val targetDir = Files.createTempDirectory("part-perm-error-").toFile()
            val manager = createTestManager(fixture.client, targetDir)
            val item = HttpDownloadItem(
                link = fixture.url,
                id = 102L,
                folder = targetDir.absolutePath,
                name = "perm_fail.bin",
                preferredConnectionCount = 2,
            )

            try {
                manager.boot()
                manager.settings.maxDownloadRetryCount = 2
                manager.addDownload(newProps(item))
                val job = manager.downloadJobs.single() as HttpDownloadJob

                withTimeout(15_000) {
                    job.resume()
                    job.status.first { it is DownloadJobStatus.Canceled && it.e is TooManyErrorException }
                }

                assertEquals(DownloadStatus.Error, job.downloadItem.status)
            } finally {
                manager.downloadJobs.forEach { it.close() }
                targetDir.deleteRecursively()
            }
        }
    }

    @Test
    fun normalCancellationDoesNotTriggerUnwantedRetry() = runBlocking {
        val bytes = ByteArray(512 * 1024) { it.toByte() }

        withPartErrorFixture(bytes) { fixture ->
            val targetDir = Files.createTempDirectory("part-cancel-test-").toFile()
            val manager = createTestManager(fixture.client, targetDir)
            val item = HttpDownloadItem(
                link = fixture.url,
                id = 103L,
                folder = targetDir.absolutePath,
                name = "cancel.bin",
                preferredConnectionCount = 2,
            )

            try {
                manager.boot()
                manager.addDownload(newProps(item))
                val job = manager.downloadJobs.single() as HttpDownloadJob

                withTimeout(15_000) {
                    job.resume()
                    job.status.first { it is DownloadJobStatus.Downloading }
                    job.pause()
                }

                val finalStatus = job.status.value
                assertTrue(finalStatus is DownloadJobStatus.Canceled)
                assertEquals(DownloadStatus.Paused, job.downloadItem.status)
                assertEquals(0, job.downloadItem.retryAttempt)
            } finally {
                manager.downloadJobs.forEach { it.close() }
                targetDir.deleteRecursively()
            }
        }
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size, "Size mismatch")
        assertEquals(expected.sha256(), actual.sha256(), "SHA-256 hash mismatch")
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun createTestManager(client: OkHttpHttpDownloaderClient, folder: File): DownloadManager {
        val registry = DownloaderRegistry().apply {
            add(HttpDownloader(lazyOf(client)))
        }
        return DownloadManager(
            dlListDb = MemoryDownloadListDB(),
            partListDb = MemoryDownloadPartStatesDB(),
            settings = DownloadSettings().apply {
                minPartSize = 1024
                maxDownloadRetryCount = 3
            },
            emptyFileCreator = EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = registry,
            downloadDataFolder = folder,
        )
    }

    private inline fun <T> withPartErrorFixture(
        bytes: ByteArray,
        block: (PartErrorFixture) -> T,
    ): T {
        var fixtureRef: PartErrorFixture? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/test.bin") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            val (start, end) = if (range != null) {
                val match = Regex("""bytes=(\d+)-(\d*)""").matchEntire(range)!!
                val s = match.groupValues[1].toLong()
                val e = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: (bytes.size - 1).toLong()
                s to e
            } else {
                0L to (bytes.size - 1).toLong()
            }

            fixtureRef?.let { fix ->
                if (fix.onRequest(exchange, start, end)) {
                    return@createContext
                }
            }

            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.responseHeaders.add("ETag", "\"part-test-etag\"")
            if (range != null) {
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
                exchange.sendResponseHeaders(206, end - start + 1)
            } else {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
            }

            exchange.responseBody.use { out ->
                val length = (end - start + 1).toInt()
                out.write(bytes, start.toInt(), length)
            }
            exchange.close()
        }
        server.start()

        val client = OkHttpHttpDownloaderClient(
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
            customUserAgentProvider = object : UserAgentProvider {
                override fun getUserAgent() = "part-error-test"
            },
            proxyStrategyProvider = object : ProxyStrategyProvider {
                override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
            },
            systemProxySelectorProvider = object : SystemProxySelectorProvider {
                override fun getSystemProxySelector(): ProxySelector? = null
            },
            autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
        )

        val fixture = PartErrorFixture(
            url = "http://127.0.0.1:${server.address.port}/test.bin",
            client = client,
        )
        fixtureRef = fixture

        return try {
            block(fixture)
        } finally {
            server.stop(0)
        }
    }

    private class PartErrorFixture(
        val url: String,
        val client: OkHttpHttpDownloaderClient,
    ) {
        var onRequest: (exchange: HttpExchange, start: Long, end: Long) -> Boolean = { _, _, _ -> false }
    }
}
