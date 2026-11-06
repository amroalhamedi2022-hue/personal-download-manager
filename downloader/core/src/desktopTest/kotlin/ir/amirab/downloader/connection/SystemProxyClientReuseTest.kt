package ir.amirab.downloader.connection

import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SystemProxyClientReuseTest {
    private fun directSelector() = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
    }

    @Test
    fun unchangedSystemSelectorReusesClientAcrossSixteenPartRequests() {
        val selector = directSelector()
        val client = OkHttpClient.Builder().proxySelector(selector).build()
        repeat(16) { assertSame(client, client.withSystemProxySelector(selector)) }
    }

    @Test
    fun aNewSystemSelectorRebuildsClientButRetainsConnectionInfrastructure() {
        val original = OkHttpClient.Builder().proxySelector(directSelector()).build()
        val newSelector = directSelector()
        val replacement = original.withSystemProxySelector(newSelector)

        assertNotSame(original, replacement)
        assertSame(newSelector, replacement.proxySelector)
        assertSame(original.dispatcher, replacement.dispatcher)
        assertSame(original.connectionPool, replacement.connectionPool)
        assertSame(replacement, replacement.withSystemProxySelector(newSelector))
    }
}
