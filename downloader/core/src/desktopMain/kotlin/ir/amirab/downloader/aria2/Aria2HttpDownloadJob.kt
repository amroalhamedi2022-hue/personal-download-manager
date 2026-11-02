package ir.amirab.downloader.aria2

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.destination.IncompleteFileUtil
import ir.amirab.downloader.destination.SimpleDownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.OptionalHttpDownloadJobFactory
import ir.amirab.downloader.downloaditem.http.RangeBasedDownloadJob
import ir.amirab.downloader.downloaditem.http.applyFrom
import ir.amirab.downloader.downloaditem.http.sameHttpResourceLocation
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.part.RangedPart
import ir.amirab.downloader.part.RangedParts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

class Aria2DownloadException(
    val engineErrorCode: String?,
    val engineProvidedMessage: Boolean,
) : IllegalStateException(
    "aria2 transfer failed" + (engineErrorCode?.let { " (code=$it)" } ?: "."),
)

/**
 * Real DownloadManager bridge for the optional desktop aria2 engine. The public
 * task identity, persisted item and UI state remain HyperGet-owned.
 */
class Aria2HttpDownloadJob(
    override val downloadItem: HttpDownloadItem,
    downloadManager: DownloadManager,
    private val engine: Aria2TaskEngine,
    private val pollInterval: Duration = Duration.ofMillis(200),
) : DownloadJob(downloadManager), RangeBasedDownloadJob {
    private val itemSaveLock = Mutex()
    private val engineCommandLock = Mutex()
    private val parts = mutableListOf<RangedPart>()
    private lateinit var destination: Aria2DownloadDestination

    override val supportsConcurrent: Boolean? = true

    override fun initializeDestination() {
        destination = Aria2DownloadDestination(
            outputFile = downloadManager.calculateOutputFile(downloadItem),
            downloadId = id,
            emptyFileCreator = downloadManager.emptyFileCreator,
        )
    }

    override fun getDestination(): DownloadDestination = destination

    override suspend fun actualBoot() {
        initializeDestination()
        val stored = downloadManager.partListDb.getParts(id) as? RangedParts
        synchronized(parts) {
            parts.clear()
            parts += stored?.list?.singleOrNull()?.copy() ?: RangedPart(0, null, 0)
        }
        if (engine.contains(id)) {
            val snapshot = engine.status(id)
            applySnapshot(snapshot)
            when (snapshot.progress.state) {
                Aria2TransferState.COMPLETE -> onDownloadFinished()
                Aria2TransferState.ERROR,
                Aria2TransferState.REMOVED,
                -> markEngineFailure(snapshot)
                Aria2TransferState.PAUSED -> {
                    downloadItem.status = DownloadStatus.Paused
                    _status.value = DownloadJobStatus.IDLE
                }
                Aria2TransferState.ACTIVE,
                Aria2TransferState.WAITING,
                -> {
                    // Process configuration restores session entries paused. If a
                    // third-party aria2 build reports active here, take ownership
                    // by pausing it before exposing a resumable HyperGet job.
                    engine.pause(id)
                    downloadItem.status = DownloadStatus.Paused
                    _status.value = DownloadJobStatus.IDLE
                }
            }
            saveState()
        }
    }

    override suspend fun resume() {
        if (!tryActivateDownload()) return
        boot()
        onDownloadResuming()
        try {
            destination.prepareDestinationFolder()
            engineCommandLock.withLock {
                engine.add(createAddRequest())
                val current = engine.status(id)
                when (current.progress.state) {
                    Aria2TransferState.PAUSED -> engine.unpause(id)
                    Aria2TransferState.ERROR,
                    Aria2TransferState.REMOVED,
                    -> throw Aria2DownloadException(current.errorCode, current.hasErrorMessage)
                    Aria2TransferState.WAITING,
                    Aria2TransferState.ACTIVE,
                    Aria2TransferState.COMPLETE,
                    -> Unit
                }
            }
            downloadItem.status = DownloadStatus.Downloading
            downloadItem.retryAttempt = 0
            downloadItem.retryAt = null
            if (downloadItem.startTime == null) downloadItem.startTime = System.currentTimeMillis()
            saveState()
            onDownloadResumed()
            startPolling()
        } catch (failure: Throwable) {
            markDownloadInactive()
            if (failure is CancellationException) throw failure
            onDownloadCanceled(failure)
        }
    }

    private fun startPolling() {
        activeDownloadScope?.coroutineContext?.job?.cancel()
        val pollingScope = newScopeBasedOn(scope).also { activeDownloadScope = it }
        pollingScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val snapshot = engineCommandLock.withLock { engine.status(id) }
                    applySnapshot(snapshot)
                    when (snapshot.progress.state) {
                        Aria2TransferState.COMPLETE -> {
                            onDownloadFinished()
                            return@launch
                        }
                        Aria2TransferState.ERROR,
                        Aria2TransferState.REMOVED,
                        -> throw Aria2DownloadException(snapshot.errorCode, snapshot.hasErrorMessage)
                        Aria2TransferState.PAUSED -> {
                            markDownloadInactive()
                            return@launch
                        }
                        Aria2TransferState.WAITING,
                        Aria2TransferState.ACTIVE,
                        -> Unit
                    }
                    delay(pollInterval.toMillis().coerceAtLeast(10))
                    ensureActive()
                }
            } catch (_: CancellationException) {
                // The lifecycle command that cancelled this scope publishes state.
            } catch (failure: Throwable) {
                scope.launch { pause(failure) }
            }
        }
    }

    private fun createAddRequest(): Aria2AddRequest {
        val transferFile = destination.transferFile
        val controlFile = destination.controlFile
        return Aria2AddRequest(
            hyperGetTaskId = id,
            source = downloadItem.link,
            destinationDirectory = transferFile.parentFile.toPath(),
            fileName = transferFile.name,
            connections = (downloadItem.preferredConnectionCount
                ?: downloadManager.settings.defaultThreadCount).coerceIn(1, 16),
            speedLimitBytesPerSecond = downloadItem.speedLimit,
            userAgent = downloadItem.userAgent,
            resumeExistingPartial = transferFile.isFile && controlFile.isFile,
        )
    }

    private fun applySnapshot(snapshot: Aria2TaskSnapshot) {
        val sample = snapshot.progress
        val total = sample.totalLength
        synchronized(parts) {
            val part = parts.singleOrNull() ?: RangedPart(0, null, 0).also {
                parts.clear()
                parts += it
            }
            part.current = sample.completedLength
            part.to = total?.takeIf { it > 0 }?.minus(1)
            if (sample.state == Aria2TransferState.COMPLETE && part.to == null) {
                part.setBlindAsCompleted()
            }
            part.statusFlow.value = when (sample.state) {
                Aria2TransferState.ACTIVE -> PartDownloadStatus.ReceivingData
                Aria2TransferState.COMPLETE -> PartDownloadStatus.Completed
                Aria2TransferState.ERROR -> PartDownloadStatus.Canceled(
                    Aria2DownloadException(snapshot.errorCode, snapshot.hasErrorMessage)
                )
                Aria2TransferState.WAITING,
                Aria2TransferState.PAUSED,
                Aria2TransferState.REMOVED,
                -> PartDownloadStatus.IDLE
            }
        }
        if (total != null && total > 0) downloadItem.contentLength = total
        _status.value = when (sample.state) {
            Aria2TransferState.ACTIVE -> DownloadJobStatus.Downloading
            Aria2TransferState.WAITING -> DownloadJobStatus.Resuming
            Aria2TransferState.PAUSED -> DownloadJobStatus.IDLE
            Aria2TransferState.COMPLETE -> DownloadJobStatus.Downloading
            Aria2TransferState.ERROR,
            Aria2TransferState.REMOVED,
            -> DownloadJobStatus.Canceled(
                Aria2DownloadException(snapshot.errorCode, snapshot.hasErrorMessage)
            )
        }
    }

    private fun markEngineFailure(snapshot: Aria2TaskSnapshot) {
        val failure = Aria2DownloadException(snapshot.errorCode, snapshot.hasErrorMessage)
        downloadItem.status = DownloadStatus.Error
        _status.value = DownloadJobStatus.Canceled(failure)
        markDownloadInactive()
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        markDownloadInactive()
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
        val completed = engineCommandLock.withLock {
            if (!engine.contains(id)) return@withLock false
            val current = engine.status(id)
            applySnapshot(current)
            when (current.progress.state) {
                Aria2TransferState.ACTIVE,
                Aria2TransferState.WAITING,
                -> engine.pause(id)
                Aria2TransferState.COMPLETE -> return@withLock true
                Aria2TransferState.PAUSED,
                Aria2TransferState.ERROR,
                Aria2TransferState.REMOVED,
                -> Unit
            }
            false
        }
        if (completed) onDownloadFinished() else onDownloadCanceled(throwable)
    }

    override suspend fun reset() {
        val preserveCompletedOutput = downloadItem.status == DownloadStatus.Completed
        pause()
        engineCommandLock.withLock {
            if (engine.contains(id)) engine.remove(id)
        }
        if (!preserveCompletedOutput) {
            destination.deleteOutputFile()
            destination.cleanUpJunkFiles()
        }
        synchronized(parts) {
            parts.clear()
            parts += RangedPart(0, null, 0)
        }
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.serverETag = null
        downloadItem.status = DownloadStatus.Added
        downloadItem.startTime = null
        downloadItem.completeTime = null
        downloadItem.retryAttempt = 0
        downloadItem.retryAt = null
        _status.value = DownloadJobStatus.IDLE
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    override fun getDownloadedSize(): Long = synchronized(parts) {
        parts.sumOf(RangedPart::howMuchProceed)
    }

    override fun getParts(): List<RangedPart> = synchronized(parts) { parts.map(RangedPart::copy) }

    override suspend fun saveState() {
        itemSaveLock.withLock {
            downloadManager.dlListDb.update(downloadItem)
            downloadManager.partListDb.setParts(id, RangedParts(getParts()))
        }
    }

    override suspend fun onDownloadFinishedAfterSave() {
        engineCommandLock.withLock {
            if (engine.contains(id)) engine.remove(id)
        }
    }

    override suspend fun downloadRemoved(removeOutputFile: Boolean) {
        engineCommandLock.withLock {
            if (engine.contains(id)) engine.remove(id)
        }
        super.downloadRemoved(removeOutputFile)
    }

    override fun reloadSettings() {
        if (!isDownloadActive.value) return
        scope.launch {
            pause()
            resume()
        }
    }

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?,
    ): IDownloadItem {
        boot()
        val previous = downloadItem.copy()
        val updated = previous.copy().apply(updater)
        val previousOutput = downloadManager.calculateOutputFile(previous)
        val newOutput = downloadManager.calculateOutputFile(updated)
        val destinationChanged = previousOutput != newOutput
        val resourceChanged = previous.link != updated.link &&
            !sameHttpResourceLocation(previous.link, updated.link)
        val wasActive = isDownloadActive.value

        if (wasActive) pause()
        engineCommandLock.withLock {
            destination.preserveControlFileAcross {
                if (engine.contains(id)) engine.remove(id)
            }
        }
        if (destinationChanged) destination.moveOutput(newOutput)
        downloadItem.applyFrom(updated)
        if (resourceChanged) {
            destination.cleanUpJunkFiles()
            synchronized(parts) {
                parts.clear()
                parts += RangedPart(0, null, 0)
            }
            downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
            downloadItem.serverETag = null
        }
        initializeDestination()
        extraConfig?.let { extraConfigsReceived(it) }
        saveState()
        if (wasActive) resume()
        return downloadItem
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) = Unit
}

class Aria2HttpDownloadJobFactory(
    private val runtime: Aria2EngineRuntime,
) : OptionalHttpDownloadJobFactory {
    override fun create(item: HttpDownloadItem, downloadManager: DownloadManager): DownloadJob? {
        if (!item.link.startsWith("http://", true) && !item.link.startsWith("https://", true)) return null
        // RPC option values for arbitrary headers/auth require a richer typed
        // secret boundary. Preserve full behavior by keeping those jobs on the
        // built-in engine until that boundary is implemented and tested.
        if (!item.headers.isNullOrEmpty() || !item.username.isNullOrBlank() || !item.password.isNullOrBlank()) {
            return null
        }
        val engine = runtime.engineOrNull() ?: return null
        return Aria2HttpDownloadJob(item, downloadManager, engine)
    }
}

class Aria2EngineRuntime(
    private val featureFlag: Aria2EngineFeatureFlag,
    private val processConfig: () -> Aria2ProcessConfig?,
) : AutoCloseable {
    private var started: StartedAria2Engine? = null
    private var startAttempted = false

    @Synchronized
    fun engineOrNull(): Aria2TaskEngine? {
        if (!featureFlag.isEnabled()) return null
        started?.let {
            if (it.controller.state() == Aria2ProcessState.READY) return it.adapter
            runCatching { it.close() }
            started = null
        }
        if (startAttempted) return null
        startAttempted = true
        val config = processConfig() ?: return null
        val controller = Aria2ProcessController(
            config = config,
            rpcFactory = { endpoint, secret ->
                Aria2HttpRpcClient(endpoint, secret, timeout = Duration.ofSeconds(5))
            },
        )
        val bindingStore = Aria2BindingStore(config.sessionDirectory.resolve("bindings.json"))
        return Aria2EngineBootstrap.tryStart(featureFlag, controller, bindingStore)
            ?.also { started = it }
            ?.adapter
    }

    @Synchronized
    override fun close() {
        val current = started ?: return
        current.close()
        started = null
    }
}

private class Aria2DownloadDestination(
    outputFile: File,
    downloadId: Long,
    emptyFileCreator: ir.amirab.downloader.utils.EmptyFileCreator,
) : SimpleDownloadDestination(
    file = outputFile,
    appendExtensionToIncompleteDownloads = true,
    downloadId = downloadId,
    emptyFileCreator = emptyFileCreator,
) {
    val transferFile: File get() = incompleteFile
    val controlFile: File get() = File(transferFile.parentFile, "${transferFile.name}.aria2")

    override fun cleanUpJunkFiles() {
        super.cleanUpJunkFiles()
        controlFile.delete()
    }

    override fun moveOutput(to: File) {
        val oldControl = controlFile
        super.moveOutput(to)
        if (oldControl.exists()) {
            val newPartial = IncompleteFileUtil.addIncompleteIndicator(to, downloadId)
            Files.move(
                oldControl.toPath(),
                Path.of("${newPartial.path}.aria2"),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun preserveControlFileAcross(action: () -> Unit) {
        if (!controlFile.isFile) {
            action()
            return
        }
        val backup = Files.createTempFile(controlFile.parentFile.toPath(), ".aria2-control-", ".tmp")
        try {
            Files.copy(controlFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING)
            action()
        } finally {
            if (!controlFile.exists() && Files.exists(backup)) {
                Files.move(backup, controlFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.deleteIfExists(backup)
        }
    }
}
