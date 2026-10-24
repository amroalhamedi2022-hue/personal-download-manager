package ir.amirab.downloader.downloaditem.hls

import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.NoopSystemProxySelectorProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.destination.SegmentedDownloadDestination
import ir.amirab.downloader.part.MediaSegment
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real loopback HTTP sockets and the production HLS segment worker/destination.
 * Unlike a URL-only unit test this verifies the actual downloaded and assembled bytes.
 */
class HlsSegmentTransferLoopbackTest {
    private fun client() = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient.Builder().build(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent(): String? = "HyperGet-HLS-E2E-Test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = NoopSystemProxySelectorProvider(),
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )

    private fun sha256(bytes: ByteArray): List<Byte> =
        MessageDigest.getInstance("SHA-256").digest(bytes).toList()

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, bytes: ByteArray) {
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun redirectedPlaylistDownloadsTwoSegmentsAndAssemblesExactSha256() = runBlocking {
        val first = ByteArray(1024) { (it % 251).toByte() }
        val second = ByteArray(2048) { ((it * 7) % 253).toByte() }
        val expected = first + second
        val manifest = (
            "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            "#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:4.0,\none.ts\n" +
            "#EXTINF:4.0,\ntwo.ts\n#EXT-X-ENDLIST\n"
        ).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/entry.m3u8") { exchange ->
            exchange.responseHeaders.add("Location", "/relocated/index.m3u8")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/relocated/index.m3u8") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            respond(exchange, 200, manifest)
        }
        server.createContext("/relocated/one.ts") { respond(it, 200, first) }
        server.createContext("/relocated/two.ts") { respond(it, 200, second) }
        val directory = Files.createTempDirectory("hyperget-hls-e2e-").toFile()
        server.start()
        try {
            val input = "http://127.0.0.1:${server.address.port}/entry.m3u8"
            val credentials = HLSDownloadCredentials(link = input)
            val http = client()
            val response = http.connectHlsWithOriginGuard(credentialsForHlsPlaylist(credentials)).use {
                HLSResponseInfo.fromConnection(it)
            }
            assertTrue(response.httpResponseInfo.requestUrl.endsWith("/relocated/index.m3u8"))
            val base = response.httpResponseInfo.requestUrl
            val parts = response.hlsManifest.mediaSegments().mapIndexed { index, segment ->
                MediaSegment(
                    segmentIndex = response.hlsManifest.mediaSequence() + index,
                    link = resolveHlsSegmentIdentity(base, segment.uri()),
                    duration = segment.duration(),
                )
            }
            assertEquals(2, parts.size)
            val destination = SegmentedDownloadDestination(
                tempDirectory = directory.resolve("staging"),
                outputFile = directory.resolve("complete.ts"),
                getAllParts = { parts },
                getFileName = { it.getID().toString() },
                appendMode = false,
            )
            for (part in parts) {
                val worker = HLSPartDownloader(
                    part = part,
                    getDestWriter = { destination.getWriterFor(part) },
                    baseURL = base,
                    playlistCredentials = credentials,
                    client = http,
                    speedLimiters = emptyList(),
                    allowInternalRetries = false,
                )
                worker.onTooManyErrors = { }
                worker.start()
                withTimeout(15_000) { worker.join() }
                destination.onPartCancelled(part)
                assertTrue(part.isCompleted, "segment ${part.segmentIndex} must complete")
            }
            destination.onAllPartsCompleted { }
            val output = directory.resolve("complete.ts").readBytes()
            assertContentEquals(expected, output)
            assertEquals(sha256(expected), sha256(output))
        } finally {
            server.stop(0)
            directory.deleteRecursively()
        }
    }

    @Test
    fun unsolicitedHttp206IsNeverAcceptedAsACompleteSegment() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/partial.ts") { exchange ->
            exchange.responseHeaders.add("Content-Range", "bytes 4-7/8")
            respond(exchange, 206, byteArrayOf(5, 6, 7, 8))
        }
        val directory = Files.createTempDirectory("hyperget-hls-partial-").toFile()
        server.start()
        try {
            val link = "http://127.0.0.1:${server.address.port}/partial.ts"
            val part = MediaSegment(segmentIndex = 1, link = link, duration = 4.0)
            val destination = SegmentedDownloadDestination(
                tempDirectory = directory.resolve("staging"),
                outputFile = directory.resolve("complete.ts"),
                getAllParts = { listOf(part) },
                getFileName = { it.getID().toString() },
                appendMode = false,
            )
            val worker = HLSPartDownloader(
                part = part,
                getDestWriter = { destination.getWriterFor(part) },
                baseURL = link,
                playlistCredentials = HLSDownloadCredentials(link = link),
                client = client(),
                speedLimiters = emptyList(),
                allowInternalRetries = false,
            )
            worker.onTooManyErrors = { }
            worker.start()
            withTimeout(15_000) { worker.join() }
            assertFalse(part.isCompleted)
            assertFalse(directory.resolve("complete.ts").exists())
        } finally {
            server.stop(0)
            directory.deleteRecursively()
        }
    }

    @Test fun prematureEofCannotCompleteSegment() {
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(100, 40) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(100, 101) }
        requireCompleteHlsSegmentBytes(100, 100)
    }

    @Test fun emptyOrPartialResponseCannotCompleteSegment() {
        assertFailsWith<IOException> { requireCompleteHlsSegmentResponse(206) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentResponse(204) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(null, 0) }
        requireCompleteHlsSegmentResponse(200)
        requireCompleteHlsSegmentBytes(null, 1)
    }
}
