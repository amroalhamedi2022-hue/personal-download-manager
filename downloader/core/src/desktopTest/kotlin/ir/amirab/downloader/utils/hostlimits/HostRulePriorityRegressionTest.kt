package ir.amirab.downloader.utils.hostlimits

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse

class HostRulePriorityRegressionTest {
    private suspend fun assertOneConnectionOnly(
        host: String,
        rules: Map<String, HostLimits>,
    ) = coroutineScope {
        val registry = HostLimitRegistry()
        registry.replaceLimits(rules)
        val first = registry.acquireForHost(host)
        val queued = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquireForHost(host)
        }
        try {
            assertFalse(queued.isCompleted, "Expected the winning host rule to allow only one connection")
            first.close()
            val next = withTimeout(3_000L) { queued.await() }
            next.close()
        } finally {
            first.close()
            queued.cancel()
        }
    }

    @Test
    fun fewerWildcardsWinEvenIfOtherPatternIsLonger() = runBlocking {
        assertOneConnectionOnly(
            "axbxx.example.test",
            mapOf(
                "*.example.test" to HostLimits(maxConnections = 1),
                "a*b*.example.test" to HostLimits(maxConnections = 2),
            ),
        )
    }

    @Test
    fun longestMatchingPatternWinsWhenWildcardCountsTie() = runBlocking {
        assertOneConnectionOnly(
            "api.example.test",
            mapOf(
                "*example.test" to HostLimits(maxConnections = 2),
                "*.example.test" to HostLimits(maxConnections = 1),
            ),
        )
    }

    @Test
    fun lexicalTieBreakIsDeterministicForEqualSpecificity() = runBlocking {
        assertOneConnectionOnly(
            "aa.example.test",
            mapOf(
                "a*.example.test" to HostLimits(maxConnections = 2),
                "*a.example.test" to HostLimits(maxConnections = 1),
            ),
        )
    }
}
