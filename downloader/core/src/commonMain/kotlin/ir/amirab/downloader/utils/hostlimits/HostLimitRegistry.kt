package ir.amirab.downloader.utils.hostlimits

import ir.amirab.downloader.utils.speedlimiter.SpeedLimiter
import ir.amirab.util.HttpUrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.ForwardingSource
import okio.Source
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class HostLimits(
    val maxConnections: Int = 0,
    val bytesPerSecond: Long = 0,
) {
    init {
        require(maxConnections >= 0) { "maxConnections must be >= 0" }
        require(bytesPerSecond >= 0) { "bytesPerSecond must be >= 0" }
    }
}

class HostLimitRegistry {
    private val entries = ConcurrentHashMap<String, HostLimitEntry>()
    private val rulesLock = Any()

    @Volatile
    private var rules: Map<String, HostLimits> = emptyMap()

    fun configure(host: String, limits: HostLimits) {
        val normalizedPattern = normalizeHost(host)
        synchronized(rulesLock) {
            rules = rules + (normalizedPattern to limits)
            // Publish the rule and apply it to every cached host atomically.
            // Otherwise an older concurrent configure can overwrite a newer cap.
            reconfigureEntries()
        }
    }

    fun replaceLimits(limitsByHostPattern: Map<String, HostLimits>) {
        val normalizedRules = limitsByHostPattern.mapKeys { (hostPattern, _) ->
            normalizeHost(hostPattern)
        }
        synchronized(rulesLock) {
            rules = normalizedRules
            reconfigureEntries()
        }
    }

    fun speedLimiterForHost(host: String): SpeedLimiter = entryForHost(host).speedLimiter

    fun speedLimiterForUrl(url: String): SpeedLimiter? = hostFromUrl(url)?.let(::speedLimiterForHost)

    suspend fun acquireForHost(host: String): HostConnectionPermit =
        entryForHost(host).let { it.connectionGate.acquire(it.speedLimiter) }

    suspend fun acquireForUrl(url: String): HostConnectionPermit? =
        hostFromUrl(url)?.let { acquireForHost(it) }

    suspend fun <T> withConnectionPermit(host: String, block: suspend () -> T): T {
        val permit = acquireForHost(host)
        return try {
            block()
        } finally {
            permit.close()
        }
    }

    internal fun activeConnections(host: String): Int = entryForHost(host).connectionGate.activeCount()

    private fun entryForHost(host: String): HostLimitEntry {
        val normalizedHost = normalizeHost(host)
        // Fast path for existing entries; a live reconfiguration updates their
        // gates in-place without requiring the registry lock on every acquire.
        entries[normalizedHost]?.let { return it }
        return synchronized(rulesLock) {
            // Prevent a first-time host entry from being created with an old rule
            // between a rule update and its reconfiguration pass.
            entries.computeIfAbsent(normalizedHost) {
                HostLimitEntry().also { entry ->
                    entry.configure(resolveLimits(normalizedHost))
                }
            }
        }
    }

    private fun hostFromUrl(url: String): String? = HttpUrlUtils.getHost(url)?.let(::normalizeHost)

    private fun normalizeHost(host: String): String {
        return host.trim().trimEnd('.').lowercase(Locale.ROOT).also {
            require(it.isNotEmpty()) { "host must not be blank" }
        }
    }

    private fun reconfigureEntries() {
        entries.forEach { (host, entry) ->
            entry.configure(resolveLimits(host))
        }
    }

    private fun resolveLimits(host: String): HostLimits {
        // Preserve the exact wildcard-count, longest-pattern, lexical tie
        // ordering without allocating or sorting a list on every cached-host
        // creation or live policy reconfiguration (including 16 active hosts).
        val snapshot = rules
        var bestPattern: String? = null
        var bestLimit: HostLimits? = null
        var bestWildcardCount = Int.MAX_VALUE
        for ((pattern, limit) in snapshot) {
            if (!patternMatchesHost(pattern, host)) continue
            val wildcardCount = pattern.count { it == '*' }
            val previous = bestPattern
            if (previous == null ||
                wildcardCount < bestWildcardCount ||
                (wildcardCount == bestWildcardCount &&
                    (pattern.length > previous.length ||
                        (pattern.length == previous.length && pattern < previous)))
            ) {
                bestPattern = pattern
                bestLimit = limit
                bestWildcardCount = wildcardCount
            }
        }
        return bestLimit ?: HostLimits()
    }

    private fun patternMatchesHost(pattern: String, host: String): Boolean {
        // Match glob '*' without allocating and compiling a Regex for each
        // active host. Greedy backtracking is linear for these simple globs.
        var patternIndex = 0
        var hostIndex = 0
        var lastStar = -1
        var afterStar = 0
        while (hostIndex < host.length) {
            when {
                patternIndex < pattern.length && pattern[patternIndex] == '*' -> {
                    lastStar = patternIndex++
                    afterStar = hostIndex
                }
                patternIndex < pattern.length && pattern[patternIndex] == host[hostIndex] -> {
                    patternIndex++
                    hostIndex++
                }
                lastStar >= 0 -> {
                    patternIndex = lastStar + 1
                    hostIndex = ++afterStar
                }
                else -> return false
            }
        }
        while (patternIndex < pattern.length && pattern[patternIndex] == '*') patternIndex++
        return patternIndex == pattern.length
    }
}

class HostConnectionPermit internal constructor(
    private val release: () -> Unit,
    // The permit already resolved the effective host: expose its existing
    // limiter so HTTP parts do not parse and look up the URL a second time.
    internal val speedLimiter: SpeedLimiter,
) : Closeable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) {
            release()
        }
    }

    fun releaseWhenClosed(source: Source): Source = object : ForwardingSource(source) {
        override fun close() {
            try {
                super.close()
            } finally {
                this@HostConnectionPermit.close()
            }
        }
    }
}

private class HostLimitEntry {
    val speedLimiter = SpeedLimiter()
    val connectionGate = HostConnectionGate()

    fun configure(limits: HostLimits) {
        connectionGate.setMaxConnections(limits.maxConnections)
        speedLimiter.bytesPerSecond(limits.bytesPerSecond)
    }
}

private data class HostConnectionState(
    val maxConnections: Int = 0,
    val activeConnections: Int = 0,
    val revision: Long = 0,
)

private class HostConnectionGate {
    private val lock = Any()
    private val state = MutableStateFlow(HostConnectionState())

    fun setMaxConnections(maxConnections: Int) {
        require(maxConnections >= 0) { "maxConnections must be >= 0" }
        synchronized(lock) {
            state.value = state.value.copy(
                maxConnections = maxConnections,
                revision = state.value.revision + 1,
            )
        }
    }

    suspend fun acquire(speedLimiter: SpeedLimiter): HostConnectionPermit {
        while (true) {
            val observedRevision = state.value.revision
            val acquired = synchronized(lock) {
                val current = state.value
                if (current.maxConnections == 0 || current.activeConnections < current.maxConnections) {
                    state.value = current.copy(
                        activeConnections = current.activeConnections + 1,
                        revision = current.revision + 1,
                    )
                    true
                } else {
                    false
                }
            }
            if (acquired) {
                return HostConnectionPermit(::release, speedLimiter)
            }
            state.first { it.revision != observedRevision }
        }
    }

    fun activeCount(): Int = state.value.activeConnections

    private fun release() {
        synchronized(lock) {
            val current = state.value
            check(current.activeConnections > 0) { "host connection permit released too many times" }
            state.value = current.copy(
                activeConnections = current.activeConnections - 1,
                revision = current.revision + 1,
            )
        }
    }
}
