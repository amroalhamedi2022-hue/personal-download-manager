package ir.amirab.downloader.utils.hostlimits

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostLimitRegistryConcurrencyTest {
    @Test
    fun lowerLiveLimitDoesNotOverAdmitOrRevokeExistingConnections() = runBlocking<Unit> {
        val registry = HostLimitRegistry()
        registry.configure("Example.TEST.", HostLimits(maxConnections = 2))
        val first = registry.acquireForHost("example.test")
        val second = registry.acquireForHost("EXAMPLE.TEST")
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost("example.test")
        }
        try {
            assertEquals(2, registry.activeConnections("example.test"))
            assertFalse(waiting.isCompleted)
            registry.configure("example.test", HostLimits(maxConnections = 1))
            first.close()
            assertFalse(waiting.isCompleted, "Lowered limit must wait until active count drops below cap")
            second.close()
            val third = withTimeout(3_000) { waiting.await() }
            try {
                assertEquals(1, registry.activeConnections("example.test"))
            } finally {
                third.close()
            }
            assertEquals(0, registry.activeConnections("example.test"))
        } finally {
            first.close()
            second.close()
            waiting.cancel()
        }
    }

    @Test
    fun clearingHostPolicyWakesWaiterWithoutLosingExistingPermit() = runBlocking<Unit> {
        val registry = HostLimitRegistry()
        registry.replaceLimits(mapOf("example.test" to HostLimits(maxConnections = 1)))
        val first = registry.acquireForHost("example.test")
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost("example.test")
        }
        try {
            assertFalse(waiting.isCompleted)
            registry.replaceLimits(emptyMap())
            val second = withTimeout(3_000) { waiting.await() }
            try {
                assertEquals(2, registry.activeConnections("example.test"))
            } finally {
                second.close()
            }
        } finally {
            first.close()
            waiting.cancel()
        }
        assertEquals(0, registry.activeConnections("example.test"))
    }

    @Test
    fun cancelledWaiterDoesNotConsumeCapacityOnSubsequentAcquire() = runBlocking<Unit> {
        val registry = HostLimitRegistry()
        registry.configure("example.test", HostLimits(maxConnections = 1))
        val first = registry.acquireForHost("example.test")
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost("example.test")
        }
        assertFalse(waiting.isCompleted)
        waiting.cancelAndJoin()
        first.close()
        val second = withTimeout(3_000) { registry.acquireForHost("example.test") }
        try {
            assertEquals(1, registry.activeConnections("example.test"))
        } finally {
            second.close()
        }
        assertEquals(0, registry.activeConnections("example.test"))
    }

    @Test
    fun exactHostOverridesWildcardAndNewHostUsesLatestReplacement() = runBlocking<Unit> {
        val registry = HostLimitRegistry()
        registry.replaceLimits(
            mapOf(
                "*.example.test" to HostLimits(maxConnections = 1),
                "fast.example.test" to HostLimits(maxConnections = 2),
            )
        )
        val fastA = registry.acquireForHost("fast.example.test")
        val fastB = registry.acquireForHost("fast.example.test")
        val slowA = registry.acquireForHost("other.example.test")
        val slowWaiting = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost("other.example.test")
        }
        try {
            assertEquals(2, registry.activeConnections("fast.example.test"))
            assertEquals(1, registry.activeConnections("other.example.test"))
            assertFalse(slowWaiting.isCompleted)
            registry.replaceLimits(mapOf("*.example.test" to HostLimits(maxConnections = 2)))
            val slowB = withTimeout(3_000) { slowWaiting.await() }
            try {
                assertEquals(2, registry.activeConnections("other.example.test"))
            } finally {
                slowB.close()
            }
            val fresh = registry.acquireForHost("new.example.test")
            fresh.close()
            assertEquals(0, registry.activeConnections("new.example.test"))
            assertTrue(registry.activeConnections("fast.example.test") <= 2)
        } finally {
            fastA.close()
            fastB.close()
            slowA.close()
            slowWaiting.cancel()
        }
    }
}
