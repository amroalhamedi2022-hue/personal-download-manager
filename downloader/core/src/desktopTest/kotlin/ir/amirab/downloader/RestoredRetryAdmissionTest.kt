package ir.amirab.downloader

import ir.amirab.downloader.db.DownloadQueuePersistedDataAccess
import ir.amirab.downloader.db.MemoryDownloadListDB
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.db.QueueModel
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.queue.DownloadQueue
import ir.amirab.downloader.queue.ManualDownloadQueue
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RestoredRetryAdmissionTest {

    @Test
    fun fiveDueRetriesRespectManualQueueLimitOne() = runBlocking {
        val now = System.currentTimeMillis()
        val items = (1L..5L).map { retryItem(it, now - 1) }
        val audit = AdmissionAudit()
        val manager = createManager(items, audit)
        val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ManualDownloadQueue(manager, queueScope)

        try {
            manager.boot()
            assertEquals(items.size, manager.downloadJobs.size)
            assertEquals(items.size, manager.downloadJobs.map { it.id }.distinct().size)
            queue.boot()
            withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it > 0 } }
            queue.setMaxConcurrent(1)

            manager.restoreInterruptedRetries { queue.resume(it) }
            withTimeout(10_000) {
                while (audit.finished.size < items.size) delay(10)
            }

            assertEquals(items.map { it.id }.toSet(), audit.started.toSet())
            assertEquals(items.size, audit.started.size)
            assertEquals(1, audit.peakActive.get())
        } finally {
            queue.clearQueue()
            queueScope.cancel()
            closeManager(manager)
        }
    }

    @Test
    fun stopAllCancelsFutureRestoredRetryBeforeAdmission() = runBlocking {
        val item = retryItem(20, System.currentTimeMillis() + 800)
        val audit = AdmissionAudit()
        val manager = createManager(listOf(item), audit)
        val due = ConcurrentLinkedQueue<Long>()

        try {
            manager.boot()
            manager.restoreInterruptedRetries { due += it }
            delay(100)

            assertEquals(DownloadJobStatus.Retrying::class, manager.downloadJobs.single().status.value::class)
            manager.stopAll()
            delay(900)

            assertEquals(emptyList(), due.toList())
            assertEquals(emptyList(), audit.started.toList())
            assertEquals(0, item.retryAttempt)
            assertNull(item.retryAt)
            assertFalse(manager.downloadJobs.single().isDownloadActive.value)
        } finally {
            closeManager(manager)
        }
    }

    @Test
    fun stopAllDuringDueCallbackHandoffCancelsStaleAdmissionAcrossRestart() = runBlocking {
        // C07: force the due timer to reach the callback boundary. No sleeping
        // to approximate the race: the callback is held on an explicit barrier.
        val item = retryItem(25, System.currentTimeMillis() - 1)
        val audit = AdmissionAudit()
        val enteredHandoff = CompletableDeferred<Unit>()
        val releaseHandoff = CompletableDeferred<Unit>()
        val manager = createManager(listOf(item), audit)
        try {
            manager.boot()
            manager.restoreInterruptedRetries { id ->
                enteredHandoff.complete(Unit)
                releaseHandoff.await()
                // Reaching this line would represent a zombie due callback.
                audit.started.add(id)
            }
            withTimeout(5_000) { enteredHandoff.await() }
            // Must cancel and JOIN the due callback even though its timer fired.
            withTimeout(5_000) { manager.stopAll() }
            releaseHandoff.complete(Unit)

            assertEquals(emptyList(), audit.started.toList())
            assertEquals(emptyList(), audit.finished.toList())
            assertNull(item.retryAt)
            assertEquals(0, item.retryAttempt)
            assertFalse(manager.downloadJobs.single().isDownloadActive.value)
        } finally {
            releaseHandoff.complete(Unit)
            closeManager(manager)
        }

        // Recreate the actual manager object from the resulting persisted item
        // values. A separate disk serialization round-trip is tracked by C07.
        val restartAudit = AdmissionAudit()
        val restarted = createManager(listOf(item), restartAudit)
        try {
            restarted.boot()
            restarted.restoreInterruptedRetries { restartAudit.started.add(it) }
            delay(100)
            assertEquals(emptyList(), restartAudit.started.toList())
            assertEquals(emptyList(), restartAudit.finished.toList())
            assertNull(item.retryAt)
        } finally {
            closeManager(restarted)
        }
    }

    @Test
    fun retryOwnedByStoppedQueueWaitsUntilQueueStarts() = runBlocking {
        val item = retryItem(30, System.currentTimeMillis() - 1)
        val audit = AdmissionAudit()
        val manager = createManager(listOf(item), audit)
        val persisted = InMemoryQueueData(QueueModel(id = 11, name = "Stopped", maxConcurrent = 1, queueItems = listOf(item.id)))
        val queue = DownloadQueue(
            persistedModel = persisted.model,
            persistedData = persisted,
            downloadEvents = manager,
            onQueueEvent = {},
        )

        try {
            manager.boot()
            queue.boot()
            withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it > 0 } }
            manager.restoreInterruptedRetries { queue.onRetryDue(it) }
            delay(150)

            assertEquals(emptyList(), audit.started.toList())
            assertFalse(queue.isQueueActive)

            queue.start()
            withTimeout(5_000) {
                while (audit.finished.size != 1) delay(10)
            }
            assertEquals(listOf(item.id), audit.started.toList())
        } finally {
            queue.dispose()
            closeManager(manager)
        }
    }

    private suspend fun createManager(
        items: List<HttpDownloadItem>,
        audit: AdmissionAudit,
    ): DownloadManager {
        val listDb = MemoryDownloadListDB().also { db ->
            items.forEach { db.add(it) }
        }
        val registry = DownloaderRegistry().apply {
            add(AdmissionDownloader(audit))
        }
        return DownloadManager(
            dlListDb = listDb,
            partListDb = MemoryDownloadPartStatesDB(),
            settings = DownloadSettings(),
            emptyFileCreator = EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: java.io.File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = registry,
            downloadDataFolder = Files.createTempDirectory("retry-admission-").toFile(),
        )
    }

    private fun retryItem(id: Long, retryAt: Long) = HttpDownloadItem(
        link = "https://example.test/$id.bin",
        id = id,
        folder = "downloads",
        name = "$id.bin",
        dateAdded = id,
        retryAttempt = 1,
        retryAt = retryAt,
    )

    private fun closeManager(manager: DownloadManager) {
        manager.downloadJobs.forEach(DownloadJob::close)
        manager.scope.cancel()
        manager.downloadDataFolder.deleteRecursively()
    }

    private class AdmissionAudit {
        val active = AtomicInteger()
        val peakActive = AtomicInteger()
        val started = ConcurrentLinkedQueue<Long>()
        val finished = ConcurrentLinkedQueue<Long>()
    }

    private class AdmissionDownloader(
        private val audit: AdmissionAudit,
    ) : Downloader<HttpDownloadItem, AdmissionJob, HttpDownloadCredentials> {
        override fun createJob(item: HttpDownloadItem, downloadManager: DownloadManager) =
            AdmissionJob(item, downloadManager, audit)

        override fun accept(item: IDownloadItem): Boolean = item is HttpDownloadItem
        override val downloadItemClass = HttpDownloadItem::class
        override val downloadCredentialsClass = HttpDownloadCredentials::class
        override val downloadJobClass = AdmissionJob::class
        override val downloadItemSerializer = HttpDownloadItem.serializer()
        override val downloadCredentialsSerializer = HttpDownloadCredentials.serializer()
    }

    private class AdmissionJob(
        override val downloadItem: HttpDownloadItem,
        downloadManager: DownloadManager,
        private val audit: AdmissionAudit,
    ) : DownloadJob(downloadManager) {
        override suspend fun actualBoot() = Unit
        override fun getDestination(): DownloadDestination = error("destination is not used")
        override fun initializeDestination() = Unit
        override suspend fun reset() = pause()
        override suspend fun resume() = restoreInterruptedRetry()

        override suspend fun restoreInterruptedRetry() {
            if (!tryActivateDownload()) return
            downloadItem.retryAttempt = 0
            downloadItem.retryAt = null
            _status.value = DownloadJobStatus.Downloading
            downloadManager.onDownloadResuming(downloadItem)
            downloadManager.onDownloadResumed(downloadItem)
            audit.started += id
            val active = audit.active.incrementAndGet()
            audit.peakActive.updateAndGet { previous -> maxOf(previous, active) }
            try {
                delay(40)
                downloadItem.status = DownloadStatus.Completed
                _status.value = DownloadJobStatus.Finished
                audit.finished += id
                downloadManager.onDownloadFinished(downloadItem)
            } finally {
                audit.active.decrementAndGet()
                markDownloadInactive()
            }
        }

        override suspend fun pause(throwable: Throwable) {
            markDownloadInactive()
            cancelInterruptedRetryAdmission()
            cancelExclusiveRetry()
            downloadItem.retryAttempt = 0
            downloadItem.retryAt = null
            _status.value = DownloadJobStatus.Canceled(throwable)
            downloadManager.onDownloadCanceled(downloadItem, throwable)
        }

        override suspend fun saveState() = downloadManager.dlListDb.update(downloadItem)
        override fun getDownloadedSize(): Long = 0
        override fun reloadSettings() = Unit
        override suspend fun changeConfig(
            updater: (IDownloadItem) -> Unit,
            extraConfig: DownloadJobExtraConfig?,
        ): IDownloadItem {
            updater(downloadItem)
            return downloadItem
        }

        override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) = Unit
    }

    private class InMemoryQueueData(
        var model: QueueModel,
    ) : DownloadQueuePersistedDataAccess {
        override suspend fun getModel(): QueueModel = model
        override suspend fun setModel(model: QueueModel) {
            this.model = model
        }
    }
}
