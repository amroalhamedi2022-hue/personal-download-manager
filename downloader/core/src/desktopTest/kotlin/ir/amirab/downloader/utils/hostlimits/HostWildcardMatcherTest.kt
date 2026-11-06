package ir.amirab.downloader.utils.hostlimits

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HostWildcardMatcherTest {
    @Test
    fun wildcardMatchingHonoursAnchorsAndMultipleStars() = runBlocking {
        val registry = HostLimitRegistry()
        registry.replaceLimits(
            mapOf(
                "a*b*c.example.test" to HostLimits(maxConnections = 1),
                "*.other.test" to HostLimits(maxConnections = 2),
            )
        )
        val matching = registry.acquireForHost("aXXbYYc.example.test")
        val matchingWaiter = kotlinx.coroutines.async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost("aXXbYYc.example.test")
        }
        try {
            kotlin.test.assertFalse(matchingWaiter.isCompleted)
        } finally {
            matching.close()
            kotlinx.coroutines.withTimeout(3_000) { matchingWaiter.await() }.close()
        }
        val unmatched = registry.acquireForHost("xaXXbYYc.example.test")
        val another = registry.acquireForHost("xaXXbYYc.example.test")
        try {
            assertEquals(2, registry.activeConnections("xaXXbYYc.example.test"))
        } finally {
            unmatched.close()
            another.close()
        }
    }

    @Test
    fun wildcardRulesApplyToSixteenHostsAndRespectExactPriority() = runBlocking {
        val registry = HostLimitRegistry()
        registry.replaceLimits(
            mapOf(
                "*.example.test" to HostLimits(maxConnections = 1),
                "fast.example.test" to HostLimits(maxConnections = 16),
            )
        )
        val permits = (1..16).map { registry.acquireForHost("fast.example.test") }
        try {
            assertEquals(16, registry.activeConnections("fast.example.test"))
            (1..16).forEach { index ->
                registry.acquireForHost("host$index.example.test").close()
            }
        } finally {
            permits.forEach { it.close() }
        }
        assertEquals(0, registry.activeConnections("fast.example.test"))
    }
}
