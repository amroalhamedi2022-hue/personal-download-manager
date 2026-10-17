package ir.amirab.downloader.part

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.connection.response.expectSuccess
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.exception.UnSuccessfulResponseException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Buffer
import java.net.InetSocketAddress
import java.net.ProxySelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DownloadCoreBaselineTest {
    @Test
    fun splitPreservesCoverageWithoutOverlap() {
        val original = RangedPart(from = 0, to = 999)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })

        val second = split.splitPart()

        assertEquals(0L..499L, original.range)
        assertEquals(500L..999L, second?.range)
        assertEquals(original.to?.plus(1), second?.from)
    }

    @Test
    fun splitNeverMovesBeforeTheSafeZone() {
        val original = RangedPart(from = 0, to = 999, current = 400)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })
        split.safeZone = 599

        val second = split.splitPart()

        assertEquals(799L, original.to)
        assertEquals(800L, second?.from)
        assertTrue(split.safeZone <= original.to!!)
    }

    @Test
    fun splitRejectsPartsBelowTheConfiguredMinimum() {
        val original = RangedPart(from = 0, to = 199)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 200 })

        assertFalse(split.canSplit())
        assertNull(split.splitPart())
        assertEquals(0L..199L, original.range)
    }

    @Test
    fun partialResponseKeepsIdentityAndRangeMetadata() {
        val response = HttpResponseInfo(
            statusCode = 206,
            message = "Partial Content",
            requestUrl = "https://example.test/file.bin",
            responseHeaders = mapOf(
                "content-length" to "4",
                "content-range" to "bytes 2-5/10",
                "etag" to "\"baseline-v1\"",
                "last-modified" to "Wed, 14 Oct 2026 20:00:00 GMT",
            ),
        )

        assertTrue(response.isPartial)
        assertTrue(response.resumeSupport)
        assertEquals(10L, response.totalLength)
        assertEquals("\"baseline-v1\"", response.etag)
        assertEquals("Wed, 14 Oct 2026 20:00:00 GMT", response.lastModified)
    }

    @Test
    fun rangedConnectionReadsExactBytesAndMetadata() = withHttpFixture(
        rangeMode = RangeMode.Supported,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 2,
                end = 5,
            )
        }

        connection.use {
            assertContentEquals(byteArrayOf(2, 3, 4, 5), it.readAllBytes())
            assertEquals(206, it.responseInfo.statusCode)
            assertEquals("bytes=2-5", it.responseInfo.requestHeaders["range"])
            assertEquals(10, it.responseInfo.totalLength)
            assertEquals(TEST_ETAG, it.responseInfo.etag)
            assertEquals(TEST_LAST_MODIFIED, it.responseInfo.lastModified)
            assertTrue(it.responseInfo.resumeSupport)
        }
    }

    @Test
    fun serverIgnoringRangeDoesNotAdvertiseResumeSupport() = withHttpFixture(
        rangeMode = RangeMode.Ignored,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 2,
                end = 5,
            )
        }

        connection.use {
            assertContentEquals(TEST_BYTES, it.readAllBytes())
            assertEquals(200, it.responseInfo.statusCode)
            assertEquals(10, it.responseInfo.totalLength)
            assertFalse(it.responseInfo.resumeSupport)
        }
    }

    @Test
    fun rangeNotSatisfiableIsExposedAsResponseFailure() = withHttpFixture(
        rangeMode = RangeMode.NotSatisfiable,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 10,
                end = 20,
            )
        }

        connection.use {
            assertEquals(416, it.responseInfo.statusCode)
            val failure = assertFailsWith<UnSuccessfulResponseException> {
                it.responseInfo.expectSuccess()
            }
            assertEquals(416, failure.code)
            assertFalse(it.responseInfo.resumeSupport)
        }
    }

    private fun <T> withHttpFixture(
        rangeMode: RangeMode,
        block: (HttpFixture) -> T,
    ): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.bin") { exchange ->
            respond(exchange, rangeMode)
        }
        server.start()
        return try {
            block(
                HttpFixture(
                    url = "http://127.0.0.1:${server.address.port}/file.bin",
                    client = createHttpClient(),
                )
            )
        } finally {
            server.stop(0)
        }
    }

    private fun respond(exchange: HttpExchange, rangeMode: RangeMode) {
        exchange.responseHeaders.add("ETag", TEST_ETAG)
        exchange.responseHeaders.add("Last-Modified", TEST_LAST_MODIFIED)
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")

        when (rangeMode) {
            RangeMode.Supported -> {
                assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
                val body = TEST_BYTES.copyOfRange(2, 6)
                exchange.responseHeaders.add("Content-Range", "bytes 2-5/10")
                exchange.sendResponseHeaders(206, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            RangeMode.Ignored -> {
                assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
                exchange.sendResponseHeaders(200, TEST_BYTES.size.toLong())
                exchange.responseBody.use { it.write(TEST_BYTES) }
            }

            RangeMode.NotSatisfiable -> {
                assertEquals("bytes=10-20", exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.add("Content-Range", "bytes */10")
                exchange.sendResponseHeaders(416, -1)
            }
        }
        exchange.close()
    }

    private fun createHttpClient() = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent() = "download-manager-test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )

    private fun ir.amirab.downloader.connection.Connection<HttpResponseInfo>.readAllBytes(): ByteArray {
        val buffer = Buffer()
        while (true) {
            val count = source.read(buffer, 8_192)
            if (count == -1L) break
        }
        return buffer.readByteArray()
    }

    private data class HttpFixture(
        val url: String,
        val client: OkHttpHttpDownloaderClient,
    )

    private enum class RangeMode {
        Supported,
        Ignored,
        NotSatisfiable,
    }

    private companion object {
        val TEST_BYTES = ByteArray(10) { it.toByte() }
        const val TEST_ETAG = "\"fixture-v1\""
        const val TEST_LAST_MODIFIED = "Wed, 14 Oct 2026 20:00:00 GMT"
    }
}
