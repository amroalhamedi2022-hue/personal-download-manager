package ir.amirab.downloader.downloaditem.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deferred desktopTest: no Gradle/app build until final readiness. */
class HlsSegmentCredentialPolicyTest {
    private val playlist = "https://media.example.test/live/master.m3u8"

    @Test fun authenticatedPlaylistNeverFollowsRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist,
            headers = mapOf("Cookie" to "sid=private", "Authorization" to "Bearer secret"),
            username = "user",
            password = "password",
            userAgent = "HyperGet-Test",
            downloadPage = "https://media.example.test/private",
        ))
        assertEquals(playlist, result.link)
        assertEquals("sid=private", result.headers?.get("Cookie"))
        assertEquals("Bearer secret", result.headers?.get("Authorization"))
        assertEquals("user", result.username)
        assertEquals("password", result.password)
        assertEquals("HyperGet-Test", result.userAgent)
        assertNull(result.downloadPage)
        assertFalse(result.followRedirects)
    }

    @Test fun anonymousPlaylistPreservesOrdinaryRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(link = playlist))
        assertNull(result.headers)
        assertTrue(result.followRedirects)
    }

    @Test fun basicAuthAloneDisablesPlaylistRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist, username = "user", password = "password"
        ))
        assertFalse(result.followRedirects)
    }

    @Test fun cookieAloneDisablesPlaylistRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist, headers = mapOf("cOoKiE" to "private")
        ))
        assertFalse(result.followRedirects)
    }

    @Test fun playlistDropsHopByHopAndProxyAuthHeaders() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist,
            headers = mapOf("Host" to "other.example.test", "Proxy-Authorization" to "secret",
                "Range" to "bytes=5-", "Cookie" to "sid=private"),
        ))
        assertEquals(mapOf("Cookie" to "sid=private"), result.headers)
        assertFalse(result.followRedirects)
    }

    @Test fun onlyFilteredHeadersDoNotBlockAnonymousPlaylistRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist, headers = mapOf("Host" to "other.example.test", "Range" to "bytes=0-")
        ))
        assertNull(result.headers)
        assertTrue(result.followRedirects)
    }

    @Test fun userAgentAloneDoesNotBlockAnonymousRedirects() {
        val result = credentialsForHlsPlaylist(HLSDownloadCredentials(
            link = playlist, userAgent = "HyperGet-Test"
        ))
        assertEquals("HyperGet-Test", result.userAgent)
        assertTrue(result.followRedirects)
    }

    @Test fun sameOriginPreservesRequiredHeadersAndBasicAuthWithoutRedirects() {
        val source = HLSDownloadCredentials(
            link = playlist,
            headers = mapOf("Cookie" to "session=private", "Referer" to "https://media.example.test/watch"),
            username = "user", password = "password", userAgent = "HyperGet-Test",
            downloadPage = "https://media.example.test/private-page",
        )
        val result = credentialsForHlsSegment(source, playlist, "https://media.example.test/live/1.ts")
        assertEquals("session=private", result.headers?.get("Cookie"))
        assertEquals("user", result.username)
        assertEquals("password", result.password)
        assertEquals("HyperGet-Test", result.userAgent)
        assertNull(result.downloadPage)
        assertFalse(result.followRedirects)
    }

    @Test fun foreignHostDoesNotReceiveSecrets() {
        val source = HLSDownloadCredentials(
            link = playlist,
            headers = mapOf("Authorization" to "Bearer private", "Cookie" to "sid=private"),
            username = "user", password = "password", userAgent = "HyperGet-Test",
        )
        val result = credentialsForHlsSegment(source, playlist, "https://cdn.example.test/segment.ts")
        assertNull(result.headers)
        assertNull(result.username)
        assertNull(result.password)
        assertEquals("HyperGet-Test", result.userAgent)
        assertTrue(result.followRedirects)
    }

    @Test fun redirectedPlaylistDoesNotExpandCredentialTrust() {
        val source = HLSDownloadCredentials(link = playlist, headers = mapOf("Cookie" to "sid=private"))
        val result = credentialsForHlsSegment(
            source, "https://cdn.example.test/new/index.m3u8", "https://cdn.example.test/new/1.ts"
        )
        assertNull(result.headers)
        assertTrue(result.followRedirects)
    }

    @Test fun differentPortAndSchemeAreUntrusted() {
        val source = HLSDownloadCredentials(link = playlist, headers = mapOf("Cookie" to "sid=private"))
        for (segment in listOf("https://media.example.test:8443/a.ts", "http://media.example.test/a.ts")) {
            val result = credentialsForHlsSegment(source, playlist, segment)
            assertNull(result.headers)
            assertTrue(result.followRedirects)
        }
    }

    @Test fun hopByHopAndHostHeadersAreNotForwarded() {
        val source = HLSDownloadCredentials(
            link = playlist,
            headers = mapOf("Host" to "evil.test", "Proxy-Authorization" to "secret",
                "Range" to "bytes=5-", "Connection" to "close", "Cookie" to "session=ok")
        )
        val result = credentialsForHlsSegment(source, playlist, "https://media.example.test/1.ts")
        assertEquals(mapOf("Cookie" to "session=ok"), result.headers)
        assertFalse(result.followRedirects)
    }

    @Test fun unauthenticatedSegmentKeepsNormalRedirectCompatibility() {
        val result = credentialsForHlsSegment(
            HLSDownloadCredentials(link = playlist), playlist, "https://media.example.test/1.ts"
        )
        assertNull(result.headers)
        assertTrue(result.followRedirects)
    }

    @Test fun nonDefaultPortMustMatchExactly() {
        val source = HLSDownloadCredentials(
            link = "https://media.example.test:8443/list.m3u8",
            headers = mapOf("Cookie" to "sid=private")
        )
        val result = credentialsForHlsSegment(
            source, "https://media.example.test:8443/list.m3u8",
            "https://media.example.test:8443/1.ts"
        )
        assertEquals("sid=private", result.headers?.get("Cookie"))
        assertFalse(result.followRedirects)
    }
}
