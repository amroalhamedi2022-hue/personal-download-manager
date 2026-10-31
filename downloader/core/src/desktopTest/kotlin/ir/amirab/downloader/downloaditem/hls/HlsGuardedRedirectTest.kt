package ir.amirab.downloader.downloaditem.hls

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpDownloadCredentials
import kotlinx.coroutines.runBlocking
import okio.Buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kotlin fake-transport tests; not a substitute for live Windows network E2E. */
class HlsGuardedRedirectTest {
    private val base = "https://media.example.test/live/index.m3u8"
    private fun protectedCredentials() = credentialsForHlsPlaylist(
        HLSDownloadCredentials(link = base, headers = mapOf("Cookie" to "session=private"))
    )

    private class ScriptedClient(
        private val responses: Map<String, Pair<Int, String?>>,
    ) : HttpDownloaderClient() {
        val requests = mutableListOf<HttpDownloadCredentials>()
        override suspend fun actualHead(
            credentials: IHttpDownloadCredentials, start: Long?, end: Long?
        ): HttpResponseInfo = error("HEAD not expected")

        override suspend fun actualConnect(
            credentials: IHttpBasedDownloadCredentials, start: Long?, end: Long?
        ): Connection<HttpResponseInfo> {
            val request = credentials as HttpDownloadCredentials
            requests.add(request)
            val (status, location) = responses[request.link]
                ?: error("Unexpected network request")
            return Connection(
                source = Buffer().writeUtf8("fixture"),
                contentLength = 7L,
                responseInfo = HttpResponseInfo(
                    statusCode = status, message = "fixture", requestUrl = request.link,
                    responseHeaders = location?.let { mapOf("location" to it) } ?: emptyMap()
                )
            )
        }
    }

    @Test fun followsRelativeSameOriginRedirectsAndRetainsExplicitCookies() = runBlocking {
        val second = "https://media.example.test/live/step2.m3u8"
        val final = "https://media.example.test/final/index.m3u8"
        val client = ScriptedClient(mapOf(
            base to (302 to "step2.m3u8"),
            second to (307 to "/final/index.m3u8"),
            final to (200 to null),
        ))
        client.connectHlsWithOriginGuard(protectedCredentials()).use {
            assertEquals(final, it.responseInfo.requestUrl)
        }
        assertEquals(listOf(base, second, final), client.requests.map { it.link })
        assertTrue(client.requests.all {
            !it.followRedirects && it.headers?.get("Cookie") == "session=private"
        })
    }

    @Test fun crossHostRedirectIsRejectedBeforeSecondRequest() {
        val client = ScriptedClient(mapOf(base to (302 to "https://cdn.example.test/steal")))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(listOf(base), client.requests.map { it.link })
    }

    @Test fun protocolDowngradeIsRejected() {
        val client = ScriptedClient(mapOf(base to (301 to "http://media.example.test/live/clear")))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(1, client.requests.size)
    }

    @Test fun portChangeIsRejected() {
        val client = ScriptedClient(mapOf(base to (308 to "https://media.example.test:8443/live/next")))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(1, client.requests.size)
    }

    @Test fun urlEmbeddedCredentialsAreRejected() {
        val client = ScriptedClient(mapOf(base to (302 to "https://user:password@media.example.test/next")))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(1, client.requests.size)
    }

    @Test fun missingLocationIsRejected() {
        val client = ScriptedClient(mapOf(base to (302 to null)))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(1, client.requests.size)
    }

    @Test fun cycleIsRejectedWithoutRepeatingFirstRequest() {
        val next = "https://media.example.test/live/next"
        val client = ScriptedClient(mapOf(
            base to (302 to "next"),
            next to (302 to "index.m3u8"),
        ))
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(listOf(base, next), client.requests.map { it.link })
    }

    @Test fun longChainStopsAfterFiveRedirects() {
        val hops = (0..6).map { "https://media.example.test/live/hop$it" }
        val map = mutableMapOf(base to (302 to hops[0]))
        for (i in 0..5) map[hops[i]] = 302 to hops[i + 1]
        val client = ScriptedClient(map)
        assertFailsWith<IOException> { runBlocking { client.connectHlsWithOriginGuard(protectedCredentials()) } }
        assertEquals(6, client.requests.size)
        assertFalse(client.requests.any { it.link == hops[5] })
    }

    @Test fun anonymousRequestsUseExistingClientRedirectPolicy() = runBlocking {
        val client = ScriptedClient(mapOf(base to (302 to "/other")))
        val anonymous = credentialsForHlsPlaylist(HLSDownloadCredentials(link = base))
        client.connectHlsWithOriginGuard(anonymous).use {
            assertEquals(302, it.responseInfo.statusCode)
        }
        assertEquals(1, client.requests.size)
        assertTrue(client.requests.single().followRedirects)
    }

    @Test fun protectedSegmentUsesSameGuardForRedirects() = runBlocking {
        val segment = "https://media.example.test/live/1.ts"
        val next = "https://media.example.test/live/2.ts"
        val client = ScriptedClient(mapOf(segment to (302 to "2.ts"), next to (200 to null)))
        val source = HLSDownloadCredentials(link = base, headers = mapOf("Authorization" to "Bearer private"))
        val credentials = credentialsForHlsSegment(source, base, segment)
        client.connectHlsWithOriginGuard(credentials).use {
            assertEquals(next, it.responseInfo.requestUrl)
        }
        assertTrue(client.requests.all { it.headers?.get("Authorization") == "Bearer private" })
    }
}
