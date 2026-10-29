package ir.amirab.downloader.aria2

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

data class Aria2AddRequest(
    val hyperGetTaskId: Long,
    val source: String,
    val destinationDirectory: Path,
    val fileName: String,
)

data class Aria2TaskSnapshot(
    val hyperGetTaskId: Long,
    val aria2Gid: String,
    val progress: Aria2ProgressSample,
    val errorCode: String?,
    val hasErrorMessage: Boolean,
)

/**
 * Stage-3 adapter boundary. It deliberately knows nothing about Compose, browser
 * messaging, HLS/DASH assembly or package handling. HyperGet IDs are the only
 * public identity and the replaceable aria2 GID stays behind this class.
 */
class Aria2EngineAdapter private constructor(
    private val rpc: Aria2RpcApi,
    private val bindingStore: Aria2BindingStore,
    private val registry: Aria2TaskRegistry,
) {
    private val progressByTask = mutableMapOf<Long, Aria2MonotonicProgress>()

    companion object {
        fun restore(rpc: Aria2RpcApi, bindingStore: Aria2BindingStore): Aria2EngineAdapter =
            Aria2EngineAdapter(rpc, bindingStore, Aria2TaskRegistry(bindingStore.load()))
    }

    @Synchronized
    fun add(request: Aria2AddRequest): Aria2TaskBinding {
        registry.bindingForTask(request.hyperGetTaskId)?.let { return it }
        val source = validateSource(request.source)
        val directory = validateDestination(request.destinationDirectory, request.fileName)
        val options = mapOf(
            "dir" to directory.toString(),
            "out" to request.fileName,
            "continue" to "true",
            "allow-overwrite" to "false",
            "auto-file-renaming" to "false",
        )
        val gid = rpc.addUri(source, options)
        val binding = Aria2TaskBinding(request.hyperGetTaskId, gid)
        try {
            registry.bind(binding)
            bindingStore.save(registry.snapshot())
            progressByTask.putIfAbsent(request.hyperGetTaskId, Aria2MonotonicProgress())
            return registry.bindingForTask(request.hyperGetTaskId)!!
        } catch (failure: Throwable) {
            registry.removeTask(request.hyperGetTaskId)
            runCatching { rpc.remove(gid) }
            throw failure
        }
    }

    @Synchronized
    fun status(hyperGetTaskId: Long): Aria2TaskSnapshot {
        val binding = requireBinding(hyperGetTaskId)
        val status = rpc.tellStatus(binding.aria2Gid)
        val progress = progressByTask.getOrPut(hyperGetTaskId, ::Aria2MonotonicProgress).accept(
            Aria2ProgressSample(
                state = status.state,
                completedLength = status.completedLength,
                totalLength = status.totalLength.takeUnless { it == 0L },
                downloadSpeed = status.downloadSpeed,
            )
        )
        return Aria2TaskSnapshot(
            hyperGetTaskId = hyperGetTaskId,
            aria2Gid = binding.aria2Gid,
            progress = progress,
            errorCode = status.errorCode,
            // Raw engine messages can contain source URLs or credentials. The UI
            // receives only the fact that a diagnostic exists; logs use the
            // redacted Aria2Status.toString().
            hasErrorMessage = !status.errorMessage.isNullOrBlank(),
        )
    }

    @Synchronized
    fun pause(hyperGetTaskId: Long) {
        val binding = requireBinding(hyperGetTaskId)
        rpc.pause(binding.aria2Gid)
    }

    @Synchronized
    fun unpause(hyperGetTaskId: Long) {
        val binding = requireBinding(hyperGetTaskId)
        rpc.unpause(binding.aria2Gid)
    }

    /** Removes the engine task before forgetting its durable association. */
    @Synchronized
    fun remove(hyperGetTaskId: Long): Boolean {
        val binding = registry.bindingForTask(hyperGetTaskId) ?: return false
        removeEngineTask(binding.aria2Gid)
        registry.removeTask(hyperGetTaskId)
        try {
            bindingStore.save(registry.snapshot())
        } catch (failure: Throwable) {
            registry.bind(binding)
            throw failure
        }
        progressByTask.remove(hyperGetTaskId)
        return true
    }

    /**
     * aria2 keeps completed/error/removed tasks in its result store, where
     * `aria2.remove` is rejected. A task can also finish between tellStatus and
     * remove, so code 1 on the active-task path gets one result-store cleanup
     * attempt. A second code 1 means the GID is already absent and is success.
     */
    private fun removeEngineTask(gid: String) {
        val state = try {
            rpc.tellStatus(gid).state
        } catch (failure: Aria2RpcRemoteException) {
            if (failure.code == 1) return
            throw failure
        }

        if (state.isResultStoreState()) {
            removeDownloadResultOrAcceptMissing(gid)
            return
        }

        try {
            rpc.remove(gid)
        } catch (failure: Aria2RpcRemoteException) {
            if (failure.code != 1) throw failure
            // The task may have completed after tellStatus.
            removeDownloadResultOrAcceptMissing(gid)
            return
        }

        // `remove` moves an active task to the stopped-result store. Purge it
        // when aria2 has already completed that transition; code 1 here is a
        // harmless asynchronous transition and must not resurrect the binding.
        removeDownloadResultOrAcceptMissing(gid)
    }

    private fun removeDownloadResultOrAcceptMissing(gid: String) {
        try {
            rpc.removeDownloadResult(gid)
        } catch (failure: Aria2RpcRemoteException) {
            if (failure.code != 1) throw failure
        }
    }

    /**
     * Validates restored mappings without recreating downloads. Transient RPC
     * errors keep their mapping; only aria2's explicit unknown-GID result removes
     * it. This makes recovery conservative and duplicate-free.
     */
    @Synchronized
    fun reconcileRestored(): List<Aria2TaskBinding> {
        var changed = false
        registry.snapshot().forEach { binding ->
            try {
                rpc.tellStatus(binding.aria2Gid)
                progressByTask.putIfAbsent(binding.hyperGetTaskId, Aria2MonotonicProgress())
            } catch (failure: Aria2RpcRemoteException) {
                if (failure.code != 1) throw failure
                registry.removeTask(binding.hyperGetTaskId)
                progressByTask.remove(binding.hyperGetTaskId)
                changed = true
            }
        }
        if (changed) bindingStore.save(registry.snapshot())
        return registry.snapshot()
    }

    @Synchronized
    fun bindings(): List<Aria2TaskBinding> = registry.snapshot()

    private fun requireBinding(hyperGetTaskId: Long): Aria2TaskBinding =
        registry.bindingForTask(hyperGetTaskId)
            ?: throw IllegalArgumentException("HyperGet task is not bound to aria2.")

    private fun validateSource(raw: String): String {
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Invalid download URI.") }
        val supported = setOf("http", "https", "ftp", "ftps", "magnet")
        require(uri.scheme?.lowercase() in supported) { "Unsupported aria2 transfer scheme." }
        require(!uri.isOpaque || uri.scheme.equals("magnet", ignoreCase = true)) { "Invalid download URI." }
        return uri.toASCIIString()
    }

    private fun validateDestination(directory: Path, fileName: String): Path {
        require(fileName.isNotBlank()) { "Destination file name cannot be blank." }
        require(Path.of(fileName).fileName.toString() == fileName) { "Destination must be a plain file name." }
        val normalized = directory.toAbsolutePath().normalize()
        require(Files.isDirectory(normalized) && Files.isWritable(normalized)) {
            "Destination directory is unavailable."
        }
        require(Files.notExists(normalized.resolve(fileName))) { "Destination file already exists." }
        return normalized
    }
}

private fun Aria2TransferState.isResultStoreState(): Boolean = when (this) {
    Aria2TransferState.COMPLETE,
    Aria2TransferState.ERROR,
    Aria2TransferState.REMOVED,
    -> true
    Aria2TransferState.WAITING,
    Aria2TransferState.ACTIVE,
    Aria2TransferState.PAUSED,
    -> false
}

enum class Aria2Workload {
    HTTP,
    HTTPS,
    FTP,
    TORRENT,
    HLS,
    DASH,
    MEDIA_EXTRACTION,
    PACKAGE,
    BROWSER_NATIVE_MESSAGING,
}

enum class TransferEngineSelection { ARIA2, CURRENT_OR_SPECIALIZED }

/** Keeps aria2 opt-in and preserves every specialized engine boundary. */
class Aria2EngineRouter(private val featureFlag: Aria2EngineFeatureFlag) {
    fun select(workload: Aria2Workload): TransferEngineSelection {
        if (!featureFlag.isEnabled()) return TransferEngineSelection.CURRENT_OR_SPECIALIZED
        return when (workload) {
            Aria2Workload.HTTP,
            Aria2Workload.HTTPS,
            Aria2Workload.FTP,
            Aria2Workload.TORRENT,
            -> TransferEngineSelection.ARIA2
            Aria2Workload.HLS,
            Aria2Workload.DASH,
            Aria2Workload.MEDIA_EXTRACTION,
            Aria2Workload.PACKAGE,
            Aria2Workload.BROWSER_NATIVE_MESSAGING,
            -> TransferEngineSelection.CURRENT_OR_SPECIALIZED
        }
    }
}

data class StartedAria2Engine(
    val controller: Aria2ProcessController,
    val adapter: Aria2EngineAdapter,
) : AutoCloseable {
    override fun close() = controller.shutdown()
}

/** Startup failure is an explicit fallback, never a production-engine outage. */
object Aria2EngineBootstrap {
    fun tryStart(
        featureFlag: Aria2EngineFeatureFlag,
        controller: Aria2ProcessController,
        bindingStore: Aria2BindingStore,
    ): StartedAria2Engine? {
        if (!featureFlag.isEnabled()) return null
        return try {
            val rpc = controller.start()
            StartedAria2Engine(controller, Aria2EngineAdapter.restore(rpc, bindingStore))
        } catch (_: Throwable) {
            runCatching { controller.shutdown() }
            null
        }
    }
}
