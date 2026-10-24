package ir.amirab.downloader.downloaditem.hls

import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.NoopSystemProxySelectorProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Real loopback sockets with production OkHttp transport, no internet. */
class HlsGuardedRedirectLoopbackTest {
    private fun client(cookieJar: CookieJar = CookieJar.NO_COOKIES) =
        OkHttpHttpDownloaderClient(
            okHttpClient = OkHttpClient.Builder().cookieJar(cookieJar).build(),
            customUserAgentProvider = object : UserAgentProvider {
                override fun getUserAgent(): String? = "HyperGet-Loopback-Test"
            },
            proxyStrategyProvider = object : ProxyStrategyProvider {
                override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
            },
            systemProxySelectorProvider = NoopSystemProxySelectorProvider(),
            autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
        )

    @Test fun sameOrigin302PreservesExplicitCookieWithoutAmbientJarCookie() = runBlocking {
        val received = CopyOnWriteArrayList<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/start") { exchange ->
            received.add(exchange.requestHeaders.getFirst("Cookie"))
            exchange.responseHeaders.add("Location", "/done")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/done") { exchange ->
            received.add(exchange.requestHeaders.getFirst("Cookie"))
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/start"
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    listOfNotNull(Cookie.parse(url, "ambient=should-not-leak"))
            }
            val credentials = credentialsForHlsPlaylist(HLSDownloadCredentials(
                link = url, headers = mapOf("Cookie" to "sid=private")
            ))
            client(jar).connectHlsWithOriginGuard(credentials).use {
                assertEquals(200, it.responseInfo.statusCode)
            }
            assertEquals(listOf("sid=private", "sid=private"), received.toList())
        } finally {
            server.stop(0)
        }
    }

    @Test fun crossPort302NeverContactsSecondServer() {
        val foreignHits = AtomicInteger(0)
        val foreign = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        foreign.createContext("/capture") { exchange ->
            foreignHits.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        val original = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        original.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${foreign.address.port}/capture")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        foreign.start()
        original.start()
        try {
            val url = "http://127.0.0.1:${original.address.port}/start"
            val credentials = credentialsForHlsPlaylist(HLSDownloadCredentials(
                link = url, headers = mapOf("Authorization" to "Bearer secret")
            ))
            assertFailsWith<IOException> {
                runBlocking { client().connectHlsWithOriginGuard(credentials) }
            }
            assertEquals(0, foreignHits.get())
        } finally {
            original.stop(0)
            foreign.stop(0)
        }
    }
}
