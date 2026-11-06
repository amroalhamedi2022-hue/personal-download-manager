package ir.amirab.downloader.utils.hostlimits

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HostPermitLimiterIdentityTest {
    @Test
    fun sixteenPermitsShareExactlyOneResolvedHostSpeedLimiter() = runBlocking {
        val registry = HostLimitRegistry()
        registry.configure("cdn.example.test", HostLimits(maxConnections = 16, bytesPerSecond = 64_000L))
        val limiter = registry.speedLimiterForHost("cdn.example.test")
        val permits = (1..16).map {
            requireNotNull(registry.acquireForUrl("https://CDN.EXAMPLE.TEST/segment-$it"))
        }
        try {
            permits.forEach { assertSame(limiter, it.speedLimiter) }
            assertEquals(16, registry.activeConnections("cdn.example.test"))
        } finally {
            permits.forEach { it.close() }
        }
        assertEquals(0, registry.activeConnections("cdn.example.test"))
    }

    @Test
    fun livePolicyUpdateReusesPermitLimiterInsteadOfCreatingADetachedThrottle() = runBlocking {
        val registry = HostLimitRegistry()
        registry.configure("*.example.test", HostLimits(maxConnections = 1, bytesPerSecond = 1_000L))
        val initial = requireNotNull(registry.acquireForUrl("http://api.example.test/first"))
        val limiter = initial.speedLimiter
        initial.close()
        registry.configure("api.example.test", HostLimits(maxConnections = 4, bytesPerSecond = 5_000L))
        val updated = requireNotNull(registry.acquireForUrl("http://api.example.test/second"))
        try {
            assertSame(limiter, updated.speedLimiter)
            assertSame(registry.speedLimiterForHost("api.example.test"), updated.speedLimiter)
        } finally {
            updated.close()
        }
    }

    @Test
    fun unparseableHttpUrlDoesNotAllocateHostConnectionPermit() = runBlocking {
        val registry = HostLimitRegistry()
        assertNull(registry.acquireForUrl("not a download URL"))
    }
}
