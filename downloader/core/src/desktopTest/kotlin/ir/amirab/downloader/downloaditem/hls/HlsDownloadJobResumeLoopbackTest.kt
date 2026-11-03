package ir.amirab.downloader.downloaditem.hls

import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.NoopSystemProxySelectorProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.db.IDownloadListDb
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.part.MediaSegment
import ir.amirab.downloader.part.MediaSegments
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real DownloadManager/HLSDownloadJob boot from persisted completed stage files. */
class HlsDownloadJobResumeLoopbackTest {
    private fun client() = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient.Builder().build(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent(): String? = "HyperGet-HLS-Resume-Test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = NoopSystemProxySelectorProvider(),
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )

    @Test fun persistedCompletedOldPlaylistIsRefetchedAndRelocatedBytesReplaceOldStages() = runBlocking {
        val first = ByteArray(1024) { (it * 3 % 251).toByte() }
        val second = ByteArray(2048) { (it * 11 % 253).toByte() }
        val oldFirst = ByteArray(first.size) { 42 }
        val oldSecond = ByteArray(second.size) { 43 }
        val manifest = (
            "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            "#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:4.0,\none.ts\n" +
            "#EXTINF:4.0,\ntwo.ts\n#EXT-X-ENDLIST\n"
        ).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val firstRequests = AtomicInteger()
        val secondRequests = AtomicInteger()
        server.createContext("/entry.m3u8") { exchange ->
            exchange.responseHeaders.add("Location", "/new/index.m3u8")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/new/index.m3u8") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, manifest.size.toLong())
            exchange.responseBody.use { it.write(manifest) }
        }
        server.createContext("/new/one.ts") { exchange ->
            firstRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, first.size.toLong())
            exchange.responseBody.use { it.write(first) }
        }
        server.createContext("/new/two.ts") { exchange ->
            secondRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, second.size.toLong())
            exchange.responseBody.use { it.write(second) }
        }
        val root = Files.createTempDirectory("hyperget-hls-job-resume-").toFile()
        server.start()
        var manager: DownloadManager? = null
        try {
            val port = server.address.port
            val item = HLSDownloadItem(
                id = 88, folder = root.resolve("output").absolutePath,
                name = "video.ts", link = "http://127.0.0.1:" + port + "/entry.m3u8",
                dateAdded = 1, status = DownloadStatus.Paused,
                preferredConnectionCount = 2
            )
            val partsDb = MemoryDownloadPartStatesDB()
            partsDb.setParts(88, MediaSegments(listOf(
                MediaSegment(1, "http://127.0.0.1:" + port + "/old/one.ts", 4.0,
                    isCompleted = true, length = oldFirst.size.toLong(), current = oldFirst.size.toLong()),
                MediaSegment(2, "http://127.0.0.1:" + port + "/old/two.ts", 4.0,
                    isCompleted = true, length = oldSecond.size.toLong(), current = oldSecond.size.toLong())
            )))
            val stage = root.resolve("stage/88").apply { mkdirs() }
            stage.resolve("1").writeBytes(oldFirst)
            stage.resolve("2").writeBytes(oldSecond)
            val listDb = object : IDownloadListDb {
                override suspend fun getAll(): List<IDownloadItem> = listOf(item)
                override suspend fun getById(id: Long): IDownloadItem? = item.takeIf { id == 88L }
                override suspend fun add(item: IDownloadItem) = error("not used")
                override suspend fun update(item: IDownloadItem) = Unit
                override suspend fun remove(item: IDownloadItem) = error("not used")
                override suspend fun removeById(itemId: Long) = error("not used")
                override suspend fun getLastId(): Long = 88
            }
            manager = DownloadManager(
                dlListDb = listDb, partListDb = partsDb,
                settings = DownloadSettings(defaultThreadCount = 2),
                emptyFileCreator = EmptyFileCreator(
                    diskStat = object : IDiskStat {
                        override fun getRemainingSpace(path: java.io.File): Long = Long.MAX_VALUE
                    }, useSparseFile = { false }
                ),
                downloaderRegistry = DownloaderRegistry().apply {
                    add(HLSDownloader(lazy { client() }))
                },
                downloadDataFolder = root.resolve("stage")
            )
            manager.boot()
            manager.resume(88)
            withTimeout(25_000) {
                while (item.status != DownloadStatus.Completed) delay(30)
            }
            val actual = root.resolve("output/video.ts").readBytes()
            assertContentEquals(first + second, actual)
            assertContentEquals(
                MessageDigest.getInstance("SHA-256").digest(first + second),
                MessageDigest.getInstance("SHA-256").digest(actual)
            )
            assertTrue(firstRequests.get() >= 1, "relocated first segment must redownload")
            assertTrue(secondRequests.get() >= 1, "relocated second segment must redownload")
            assertEquals(DownloadStatus.Completed, item.status)
        } finally {
            manager?.downloadJobs?.forEach(DownloadJob::close)
            server.stop(0)
            root.deleteRecursively()
        }
    }
}
