package ir.amirab.downloader.aria2

import java.security.SecureRandom
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Stage-3 boundary for the optional aria2 engine. HyperGet task IDs remain the
 * durable identity; aria2 GIDs are replaceable engine-session details.
 */
@Serializable
data class Aria2TaskBinding(
    val hyperGetTaskId: Long,
    val aria2Gid: String,
) {
    init {
        require(hyperGetTaskId > 0) { "HyperGet task ID must be positive." }
        require(ARIA2_GID.matches(aria2Gid)) { "aria2 GID must contain 16 hexadecimal characters." }
    }

    companion object {
        private val ARIA2_GID = Regex("^[0-9a-fA-F]{16}$")
    }
}

class Aria2TaskRegistry(restored: Iterable<Aria2TaskBinding> = emptyList()) {
    private val byHyperGetId = linkedMapOf<Long, Aria2TaskBinding>()
    private val byAria2Gid = linkedMapOf<String, Aria2TaskBinding>()

    init {
        restored.forEach(::bind)
    }

    @Synchronized
    fun bind(binding: Aria2TaskBinding) {
        val normalized = binding.copy(aria2Gid = binding.aria2Gid.lowercase(Locale.ROOT))
        val existingForTask = byHyperGetId[normalized.hyperGetTaskId]
        require(existingForTask == null || existingForTask == normalized) {
            "HyperGet task ${normalized.hyperGetTaskId} is already bound to another aria2 GID."
        }
        val existingForGid = byAria2Gid[normalized.aria2Gid]
        require(existingForGid == null || existingForGid == normalized) {
            "aria2 GID ${normalized.aria2Gid} is already bound to another HyperGet task."
        }
        byHyperGetId[normalized.hyperGetTaskId] = normalized
        byAria2Gid[normalized.aria2Gid] = normalized
    }

    @Synchronized
    fun bindingForTask(hyperGetTaskId: Long): Aria2TaskBinding? = byHyperGetId[hyperGetTaskId]

    @Synchronized
    fun bindingForGid(aria2Gid: String): Aria2TaskBinding? =
        byAria2Gid[aria2Gid.lowercase(Locale.ROOT)]

    @Synchronized
    fun removeTask(hyperGetTaskId: Long): Aria2TaskBinding? {
        val removed = byHyperGetId.remove(hyperGetTaskId) ?: return null
        byAria2Gid.remove(removed.aria2Gid)
        return removed
    }

    @Synchronized
    fun snapshot(): List<Aria2TaskBinding> = byHyperGetId.values.sortedBy { it.hyperGetTaskId }
}

enum class Aria2TransferState {
    WAITING,
    ACTIVE,
    PAUSED,
    COMPLETE,
    ERROR,
    REMOVED,
}

data class Aria2ProgressSample(
    val state: Aria2TransferState,
    val completedLength: Long,
    val totalLength: Long?,
    val downloadSpeed: Long,
) {
    init {
        require(completedLength >= 0) { "Completed length cannot be negative." }
        require(totalLength == null || totalLength >= 0) { "Total length cannot be negative." }
        require(downloadSpeed >= 0) { "Download speed cannot be negative." }
    }
}

/** Prevents delayed RPC samples from moving UI progress backwards. */
class Aria2MonotonicProgress {
    private var latest: Aria2ProgressSample? = null

    @Synchronized
    fun accept(sample: Aria2ProgressSample): Aria2ProgressSample {
        val previous = latest
        val accepted = if (previous == null) {
            sample
        } else {
            val terminalState = previous.state.takeIf { it.isTerminal() }
            sample.copy(
                state = terminalState ?: sample.state,
                completedLength = maxOf(previous.completedLength, sample.completedLength),
                totalLength = listOfNotNull(previous.totalLength, sample.totalLength).maxOrNull(),
                downloadSpeed = if (terminalState != null) 0 else sample.downloadSpeed,
            )
        }
        latest = accepted
        return accepted
    }
}

private fun Aria2TransferState.isTerminal(): Boolean = when (this) {
    Aria2TransferState.COMPLETE,
    Aria2TransferState.ERROR,
    Aria2TransferState.REMOVED,
    -> true
    Aria2TransferState.WAITING,
    Aria2TransferState.ACTIVE,
    Aria2TransferState.PAUSED,
    -> false
}

fun aria2TransferState(rawStatus: String): Aria2TransferState = when (rawStatus) {
    "waiting" -> Aria2TransferState.WAITING
    "active" -> Aria2TransferState.ACTIVE
    "paused" -> Aria2TransferState.PAUSED
    "complete" -> Aria2TransferState.COMPLETE
    "error" -> Aria2TransferState.ERROR
    "removed" -> Aria2TransferState.REMOVED
    else -> error("Unknown aria2 status: $rawStatus")
}

class Aria2SessionSecret private constructor(private val value: String) {
    internal fun rpcToken(): String = "token:$value"

    override fun toString(): String = "Aria2SessionSecret(REDACTED)"

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): Aria2SessionSecret {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Aria2SessionSecret(
                bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
            )
        }
    }
}

/** Off by default; only an explicit process setting can select the prototype. */
class Aria2EngineFeatureFlag(
    private val value: () -> String? = {
        System.getProperty("hyperget.engine.aria2") ?: System.getenv("HYPERGET_ENGINE_ARIA2")
    },
) {
    fun isEnabled(): Boolean = value()?.trim()?.lowercase(Locale.ROOT) in setOf("1", "true", "yes", "on")
}

@Serializable
class Aria2RpcRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: List<JsonElement>,
) {
    override fun toString(): String = "Aria2RpcRequest(id=$id, method=$method, params=REDACTED)"
}

object Aria2RpcRequestFactory {
    fun create(
        secret: Aria2SessionSecret,
        id: Long,
        method: String,
        params: List<JsonElement> = emptyList(),
    ): Aria2RpcRequest {
        require(id > 0) { "RPC request ID must be positive." }
        require(method.startsWith("aria2.") || method.startsWith("system.")) {
            "Only aria2/system RPC methods are allowed."
        }
        return Aria2RpcRequest(
            id = id,
            method = method,
            params = listOf(JsonPrimitive(secret.rpcToken())) + params,
        )
    }
}

