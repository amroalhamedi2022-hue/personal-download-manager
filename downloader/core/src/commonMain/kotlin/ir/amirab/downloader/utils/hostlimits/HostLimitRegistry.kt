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

    fun configure(host: String, limits: HostLimits) {
        entryForHost(host).configure(limits)
    }

    fun speedLimiterForHost(host: String): SpeedLimiter = entryForHost(host).speedLimiter

    fun speedLimiterForUrl(url: String): SpeedLimiter? = hostFromUrl(url)?.let(::speedLimiterForHost)

    suspend fun acquireForHost(host: String): HostConnectionPermit =
        entryForHost(host).connectionGate.acquire()

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

    private fun entryForHost(host: String): HostLimitEntry = entries.computeIfAbsent(normalizeHost(host)) {
        HostLimitEntry()
    }

    private fun hostFromUrl(url: String): String? = HttpUrlUtils.getHost(url)?.let(::normalizeHost)

    private fun normalizeHost(host: String): String {
        return host.trim().trimEnd('.').lowercase(Locale.ROOT).also {
            require(it.isNotEmpty()) { "host must not be blank" }
        }
    }
}

class HostConnectionPermit internal constructor(
    private val release: () -> Unit,
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

    suspend fun acquire(): HostConnectionPermit {
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
                return HostConnectionPermit(::release)
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
