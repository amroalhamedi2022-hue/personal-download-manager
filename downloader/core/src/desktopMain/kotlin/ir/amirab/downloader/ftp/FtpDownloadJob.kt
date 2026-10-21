package ir.amirab.downloader.ftp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * Desktop-only FTP adapter. The registry/UI is deliberately not enabled yet:
 * authentication storage, host/speed limits, and shared processing monitor must
 * be integrated and regression-tested before FTP can be offered in Add URL.
 */
class FtpDownloadJob(
    override val downloadItem: FtpDownloadItem,
    downloadManager: DownloadManager,
    private val transport: FtpTransferClient = FtpTransferClient(),
    private val credentialProvider: FtpCredentialsProvider = AnonymousFtpCredentialsProvider,
) : DownloadJob(downloadManager) {
    private lateinit var destination: FtpDownloadDestination
    private val stateLock = Mutex()

    @Volatile
    private var worker: Job? = null

    @Volatile
    private var progressBytes: Long = 0L

    override suspend fun actualBoot() {
        initializeDestination()
        progressBytes = getDownloadedSize()
    }

    override fun initializeDestination() {
        destination = FtpDownloadDestination(downloadManager.calculateOutputFile(downloadItem))
    }

    override fun getDestination(): DownloadDestination = destination

    override fun getDownloadedSize(): Long {
        if (!::destination.isInitialized) return progressBytes
        val path = destination.outputFile
        val partial = File(path.path + ".ftp-part")
        return if (path.isFile) path.length() else maxOf(progressBytes, partial.takeIf { it.isFile }?.length() ?: 0L)
    }

    override suspend fun resume() {
        boot()
        if (!tryActivateDownload()) return
        // Publish Resuming before scheduling the lazy worker so Stop All can
        // never miss an already-accepted FTP start command.
        onDownloadResuming()
        val runningScope = newScopeBasedOn(scope)
        activeDownloadScope = runningScope
        val newWorker = runningScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                // The transient provider must never persist its password in download-item JSON.
                val login = credentialProvider.resolve(downloadItem)
                val outputFile = destination.outputFile.toPath()
                downloadItem.status = DownloadStatus.Downloading
                if (downloadItem.startTime == null) downloadItem.startTime = System.currentTimeMillis()
                saveState()
                onDownloadResumed()
                // Reuse the same host permit used by HTTP/HLS. Waiting for a
                // permit is cancellable, and a paused transfer releases it even
                // when the network read exits with an error.
                val host = java.net.URI(downloadItem.link).host
                    ?: throw IOException("FTP host is missing")
                val result = downloadManager.hostLimitRegistry.withConnectionPermit(host) {
                    currentCoroutineContext().ensureActive()
                    transport.download(
                        url = downloadItem.link,
                        output = outputFile,
                        username = login.username,
                        password = login.password,
                        speedLimitBytesPerSecond = downloadItem.speedLimit,
                        sharedSpeedLimiters = listOf(
                            downloadManager.speedLimiter,
                            downloadManager.hostLimitRegistry.speedLimiterForHost(host),
                        ),
                        onProgress = { bytes, total ->
                            progressBytes = bytes
                            // Length is displayed only when it came from the FTP server.
                            if (total != null) downloadItem.contentLength = total
                        },
                    )
                }
                currentCoroutineContext().ensureActive()
                progressBytes = result.resumedFrom + result.transferredBytes
                result.remoteSize?.let { downloadItem.contentLength = it }
                if (isDownloadActive.value) onDownloadFinished()
            } catch (error: Exception) {
                // The caller executing pause() already publishes cancellation.
                // Failures originate outside this worker so it cannot join itself.
                if (isDownloadActive.value) {
                    scope.launch { pause(error) }
                }
            }
        }
        worker = newWorker
        if (isDownloadActive.value) newWorker.start() else newWorker.cancel()
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        if (!isDownloadActive.value && downloadItem.status != DownloadStatus.Downloading) return
        markDownloadInactive()
        cancelExclusiveRetry()
        cancelInterruptedRetryAdmission()
        transport.cancel() // Unblocks FTP socket reads before waiting for worker.
        val oldWorker = worker
        oldWorker?.cancelAndJoin()
        worker = null
        activeDownloadScope?.cancel()
        activeDownloadScope = null
        onDownloadCanceled(throwable)
    }

    override suspend fun reset() {
        boot()
        pause()
        if (destination.outputFile.isFile) {
            throw IOException("Refusing to reset an FTP download over an existing completed output")
        }
        destination.cleanUpJunkFiles()
        progressBytes = 0L
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.startTime = null
        downloadItem.completeTime = null
        downloadItem.retryAttempt = 0
        downloadItem.retryAt = null
        downloadItem.status = DownloadStatus.Added
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    override suspend fun saveState() {
        stateLock.withLock { downloadManager.dlListDb.update(downloadItem) }
    }

    override fun onDownloadFinishedBeforeSave() {
        if (downloadItem.contentLength == IDownloadItem.LENGTH_UNKNOWN) {
            downloadItem.contentLength = destination.outputFile.length()
        }
    }

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?,
    ): IDownloadItem {
        boot()
        // Mutable IDownloadItem update API is used by the existing editor; stage it
        // and validate BEFORE mutating the persisted item.
        val proposed = downloadItem.copy().apply(updater)
        proposed.validateItem()
        if (isDownloadActive.value) pause()
        val oldOutput = downloadManager.calculateOutputFile(downloadItem)
        val newOutput = downloadManager.calculateOutputFile(proposed)
        if (oldOutput != newOutput) destination.moveOutput(newOutput)
        downloadItem.link = proposed.link
        downloadItem.folder = proposed.folder
        downloadItem.name = proposed.name
        downloadItem.downloadPage = proposed.downloadPage
        downloadItem.contentLength = proposed.contentLength
        downloadItem.preferredConnectionCount = proposed.preferredConnectionCount
        downloadItem.speedLimit = proposed.speedLimit
        downloadItem.fileChecksum = proposed.fileChecksum
        if (oldOutput != newOutput) initializeDestination()
        extraConfig?.let { extraConfigsReceived(it) }
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
        return downloadItem
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) = Unit
    override fun reloadSettings() = Unit
}
