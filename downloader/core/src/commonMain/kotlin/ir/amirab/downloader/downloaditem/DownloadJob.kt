package ir.amirab.downloader.downloaditem

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.utils.ExceptionUtils
import ir.amirab.util.suspendGuardedEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

abstract class DownloadJob(
    val downloadManager: DownloadManager,
) {
    protected val _isDownloadActive = MutableStateFlow(false)
    val isDownloadActive = _isDownloadActive.asStateFlow()

    /**
     * Claims the one active lifecycle allowed for this persisted download.
     * StateFlow's compare-and-set makes simultaneous Resume calls deterministic.
     */
    protected fun tryActivateDownload(): Boolean =
        _isDownloadActive.compareAndSet(expect = false, update = true)

    protected fun markDownloadActive() {
        _isDownloadActive.value = true
    }

    protected fun markDownloadInactive() {
        _isDownloadActive.value = false
    }

    private val scheduledRetryLock = Any()
    private var scheduledRetryGeneration = 0L
    private var scheduledRetryJob: Job? = null

    /**
     * Publishes a retry before starting its coroutine. Pause can therefore always
     * find and join the accepted retry, including the small pre-start window.
     */
    protected fun launchExclusiveRetry(
        onAccepted: () -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ): Boolean {
        lateinit var newJob: Job
        val accepted = synchronized(scheduledRetryLock) {
            if (scheduledRetryJob?.isCompleted == false) {
                false
            } else {
                val generation = ++scheduledRetryGeneration
                newJob = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val stillCurrent = synchronized(scheduledRetryLock) {
                            scheduledRetryGeneration == generation && scheduledRetryJob === newJob
                        }
                        if (stillCurrent) {
                            block()
                        }
                    } finally {
                        synchronized(scheduledRetryLock) {
                            if (scheduledRetryJob === newJob) {
                                scheduledRetryJob = null
                            }
                        }
                    }
                }
                scheduledRetryJob = newJob
                onAccepted()
                true
            }
        }
        if (accepted) {
            newJob.start()
        }
        return accepted
    }

    protected suspend fun cancelExclusiveRetry() {
        val jobToCancel = synchronized(scheduledRetryLock) {
            scheduledRetryGeneration++
            scheduledRetryJob.also {
                scheduledRetryJob = null
            }
        }
        jobToCancel?.cancelAndJoin()
    }

    private val retryAdmissionLock = Any()
    private var retryAdmissionGeneration = 0L
    private var retryAdmissionJob: Job? = null

    /**
     * Restores only the persisted wait. When it becomes due, ownership is handed
     * back to DownloadSystem so an existing queue can admit the job normally.
     * No queue/file/host permit is reserved during this wait.
     */
    internal fun scheduleInterruptedRetryAdmission(
        onDue: suspend (Long) -> Unit,
    ): Boolean {
        val retryAt = downloadItem.retryAt ?: return false
        lateinit var newJob: Job
        val accepted = synchronized(retryAdmissionLock) {
            if (retryAdmissionJob?.isCompleted == false) {
                false
            } else {
                val generation = ++retryAdmissionGeneration
                newJob = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val remainingMillis = (retryAt - System.currentTimeMillis()).coerceAtLeast(0L)
                        delay(remainingMillis)
                        ensureActive()
                        val shouldNotify = synchronized(retryAdmissionLock) {
                            retryAdmissionGeneration == generation &&
                                retryAdmissionJob === newJob
                        }
                        if (shouldNotify) {
                            _status.value = DownloadJobStatus.IDLE
                            onDue(id)
                        }
                    } finally {
                        synchronized(retryAdmissionLock) {
                            if (retryAdmissionJob === newJob) {
                                retryAdmissionJob = null
                            }
                        }
                    }
                }
                retryAdmissionJob = newJob
                markDownloadInactive()
                _status.value = DownloadJobStatus.Retrying(
                    (retryAt - System.currentTimeMillis()).coerceAtLeast(0L)
                )
                true
            }
        }
        if (accepted) {
            newJob.start()
        }
        return accepted
    }

    protected suspend fun cancelInterruptedRetryAdmission() {
        val jobToCancel = synchronized(retryAdmissionLock) {
            retryAdmissionGeneration++
            retryAdmissionJob.also {
                retryAdmissionJob = null
            }
        }
        jobToCancel?.cancelAndJoin()
    }

    abstract val downloadItem: IDownloadItem
    val id get() = downloadItem.id
    val scope = CoroutineScope(SupervisorJob())
    var activeDownloadScope: CoroutineScope? = null
    abstract fun getDestination(): DownloadDestination
    private val booted = suspendGuardedEntry()

    protected val _status = MutableStateFlow<DownloadJobStatus>(DownloadJobStatus.IDLE)
    val status = _status.asStateFlow()

    suspend fun boot() {
        booted.action {
            actualBoot()
        }
    }

    abstract suspend fun actualBoot()

    /**
     * Recreate a retry delay that was persisted before the app stopped.
     * Download types that do not use retries keep the safe no-op default.
     */
    open suspend fun restoreInterruptedRetry() = Unit

    abstract fun initializeDestination()
    abstract suspend fun reset()
    abstract suspend fun resume()
    abstract suspend fun pause(throwable: Throwable = CancellationException())
    abstract suspend fun saveState()
    protected fun ensureBooted() {
        require(booted.isDone()) {
            "DownloadJob is not booted! Call boot() before using this object."
        }
    }

    protected fun startAutoSaver() {
        activeDownloadScope?.launch(Dispatchers.IO) {
            while (true) {
                saveState()
                delay(1.seconds)
            }
        }
    }

    protected fun onDownloadResuming() {
        _status.update {
            DownloadJobStatus.Resuming
        }
        downloadManager.onDownloadResuming(downloadItem)
    }

    protected fun onDownloadResumed() {
        _status.update { DownloadJobStatus.Downloading }
        downloadManager.onDownloadResumed(downloadItem)
    }

    protected suspend fun onDownloadCanceled(throwable: Throwable) {
        _status.update { DownloadJobStatus.Canceled(throwable) }
        if (ExceptionUtils.isNormalCancellation(throwable)) {
            downloadItem.status = DownloadStatus.Paused
        } else {
            downloadItem.status = DownloadStatus.Error
        }
        markDownloadInactive()
        saveState()
        downloadManager.onDownloadCanceled(downloadItem, throwable)
    }

    protected fun onDownloadFinished() {
        scope.launch {
            try {
                getDestination().onAllPartsCompleted {
                    _status.value = DownloadJobStatus.PreparingFile(it)
                }
            } catch (e: Exception) {
                pause(e)
                return@launch
            }
            downloadItem.status = DownloadStatus.Completed
            downloadItem.completeTime = System.currentTimeMillis()
            _status.value = DownloadJobStatus.Finished
            markDownloadInactive()
            onDownloadFinishedBeforeSave()
            saveState()
            downloadManager.onDownloadFinished(downloadItem)
        }
    }

    open fun onDownloadFinishedBeforeSave() {}
    abstract fun getDownloadedSize(): Long

    fun downloadRemoved(
        removeOutputFile: Boolean = true,
    ) {
        ensureBooted()
        getDestination().cleanUpJunkFiles()
        if (removeOutputFile) {
            getDestination().deleteOutputFile()
        }
    }

    abstract fun reloadSettings()

    fun newScopeBasedOn(scope: CoroutineScope): CoroutineScope {
        return CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))
    }

    fun close() {
        scope.cancel()
    }

    abstract suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?
    ): IDownloadItem
    abstract suspend fun extraConfigsReceived(config: DownloadJobExtraConfig)
}
