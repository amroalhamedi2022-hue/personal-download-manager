package ir.amirab.downloader.connection

import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpIdentityHeaderParityTest {
    private fun client() = OkHttpHttpDownloaderClient(
        OkHttpClient.Builder().build(),
        object : UserAgentProvider {
            override fun getUserAgent() = "HyperGet-Identity-Regression"
        },
        object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
        },
        object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        AutoConfigurableProxyProvider.NoOp(),
    )

    @Test
    fun defaultRangeRequestsKeepIdentityEncodingAcrossSixteenAttempts() = runBlocking<Unit> {
        val observed = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            observed.set(exchange.requestHeaders.getFirst("Accept-Encoding"))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val credentials = HttpDownloadCredentials("http://127.0.0.1:${server.address.port}/file")
            val client = client()
            repeat(16) {
                val result = client.head(credentials, 0L, 255L)
                assertEquals("identity", result.requestHeaders["accept-encoding"])
                assertEquals("identity", observed.get())
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun serverRangeSafetyHeaderOverridesUserCompressionPreference() = runBlocking<Unit> {
        val observed = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            observed.set(exchange.requestHeaders.getFirst("Accept-Encoding"))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val credentials = HttpDownloadCredentials(
                link = "http://127.0.0.1:${server.address.port}/file",
                headers = mapOf("Accept-Encoding" to "gzip"),
            )
            val result = client().head(credentials, 0L, 255L)
            assertEquals("identity", result.requestHeaders["accept-encoding"])
            assertEquals("identity", observed.get())
        } finally {
            server.stop(0)
        }
    }
}
