package ir.amirab.downloader.connection

import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSingleAttemptRetryTest {
    @Test fun server503WithImmediateRetryAfterIsOneObservableAttempt() = runBlocking<Unit> {
        val requests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/unavailable") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.set("Retry-After", "0")
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val client = createClient()
            val info = client.head(
                HttpDownloadCredentials("http://127.0.0.1:${server.address.port}/unavailable"),
                null,
                null,
            )
            assertEquals(503, info.statusCode)
            assertEquals("0", info.responseHeaders["retry-after"])
            assertEquals(1, requests.get(), "The job, not OkHttp, owns request retry decisions")
        } finally {
            server.stop(0)
        }
    }

    @Test fun rateLimited429WithRetryAfterIsOneObservableAttempt() = runBlocking<Unit> {
        val requests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rate-limited") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.set("Retry-After", "7")
            exchange.sendResponseHeaders(429, -1)
            exchange.close()
        }
        server.start()
        try {
            val info = createClient().head(
                HttpDownloadCredentials("http://127.0.0.1:${server.address.port}/rate-limited"),
                null,
                null,
            )
            assertEquals(429, info.statusCode)
            assertEquals("7", info.responseHeaders["retry-after"])
            assertEquals(1, requests.get(), "429 must be handled by the job retry budget, not hidden OkHttp retries")
        } finally {
            server.stop(0)
        }
    }

    @Test fun rateLimited429WithHttpDateRetryAfterIsOneObservableAttempt() = runBlocking<Unit> {
        val requests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val retryAfterDate = "Wed, 21 Oct 2015 07:28:00 GMT"
        server.createContext("/rate-limited-date") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.set("Retry-After", retryAfterDate)
            exchange.sendResponseHeaders(429, -1)
            exchange.close()
        }
        server.start()
        try {
            val info = createClient().head(
                HttpDownloadCredentials("http://127.0.0.1:${server.address.port}/rate-limited-date"),
                null,
                null,
            )
            assertEquals(429, info.statusCode)
            assertEquals(retryAfterDate, info.responseHeaders["retry-after"])
            assertEquals(1, requests.get(), "429 with HTTP date must be handled by the job retry budget, not hidden OkHttp retries")
        } finally {
            server.stop(0)
        }
    }

    @Test fun regularSuccessAndHeadersRemainIntact() = runBlocking<Unit> {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val response = createClient().head(
                HttpDownloadCredentials("http://127.0.0.1:${server.address.port}/ok"),
                null, null,
            )
            assertEquals(200, response.statusCode)
            assertEquals("application/octet-stream", response.responseHeaders["content-type"])
            assertEquals(1, requests.get())
        } finally {
            server.stop(0)
        }
    }

    private fun createClient() = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient.Builder().build(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent() = "HyperGet-Retry-Test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )
}
