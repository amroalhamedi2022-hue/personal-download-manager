package ir.amirab.downloader

import ir.amirab.downloader.db.IDownloadListDb
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadLifecycleSafetyTest {

    @Test
    fun concurrentResumeCreatesAndActivatesOnlyOneJob() = runBlocking {
        val item = testItem(id = 21)
        val audit = LifecycleAudit()
        val releaseResume = CompletableDeferred<Unit>()
        val manager = createManager(
            items = listOf(item),
            audit = audit,
            releaseResume = releaseResume,
        )
        val startTogether = CompletableDeferred<Unit>()

        try {
            val resumes = List(32) {
                async(Dispatchers.Default) {
                    startTogether.await()
                    manager.resume(item.id)
                }
            }
            startTogether.complete(Unit)
            withTimeout(5_000) { audit.firstResumeEntered.await() }
            releaseResume.complete(Unit)
            resumes.awaitAll()

            assertEquals(1, audit.created.get())
            assertEquals(1, audit.activeResumeClaims.get())
            assertEquals(1, manager.downloadJobs.count { it.id == item.id })
        } finally {
            releaseResume.complete(Unit)
            manager.downloadJobs.forEach(DownloadJob::close)
            manager.downloadDataFolder.deleteRecursively()
        }
    }

    @Test
    fun stopAllWaitsForInflightStartThenStopsIt() = runBlocking {
        val item = testItem(id = 22)
        val audit = LifecycleAudit()
        val releaseResume = CompletableDeferred<Unit>()
        val manager = createManager(
            items = listOf(item),
            audit = audit,
            releaseResume = releaseResume,
        )

        try {
            val resume = async(Dispatchers.Default) { manager.resume(item.id) }
            withTimeout(5_000) { audit.firstResumeEntered.await() }
            val stop = async(Dispatchers.Default) { manager.stopAll() }

            delay(50)
            assertFalse(stop.isCompleted, "Stop All returned while Resume was still entering its lifecycle")

            releaseResume.complete(Unit)
            withTimeout(5_000) {
                resume.await()
                stop.await()
            }

            assertEquals(listOf(item.id), audit.pausedIds.toList())
            assertFalse(manager.downloadJobs.single().isDownloadActive.value)
        } finally {
            releaseResume.complete(Unit)
            manager.downloadJobs.forEach(DownloadJob::close)
            manager.downloadDataFolder.deleteRecursively()
        }
    }

    @Test
    fun stopAllIncludesEveryActiveLifecycleStatus() = runBlocking {
        val statuses = linkedMapOf(
            31L to DownloadJobStatus.Downloading,
            32L to DownloadJobStatus.Retrying(60_000),
            33L to DownloadJobStatus.Resuming,
            34L to DownloadJobStatus.PreparingFile(50),
            35L to DownloadJobStatus.IDLE,
        )
        val items = statuses.keys.map { id ->
            testItem(id).also {
                if (id == 32L) {
                    it.retryAttempt = 2
                    it.retryAt = System.currentTimeMillis() + 60_000
                }
            }
        }
        val audit = LifecycleAudit()
        val manager = createManager(
            items = items,
            audit = audit,
            statusOnBoot = statuses,
        )

        try {
            manager.boot()
            manager.stopAll()

            assertEquals(setOf(31L, 32L, 33L, 34L), audit.pausedIds.toSet())
            assertFalse(35L in audit.pausedIds)
            val retryItem = items.single { it.id == 32L }
            assertEquals(0, retryItem.retryAttempt)
            assertNull(retryItem.retryAt)
            assertTrue(manager.downloadJobs.none { it.status.value is DownloadJobStatus.IsActive })
        } finally {
            manager.downloadJobs.forEach(DownloadJob::close)
            manager.downloadDataFolder.deleteRecursively()
        }
    }

    @Test
    fun lifecycleCommandsForOneIdNeverOverlap() = runBlocking {
        val item = testItem(id = 51)
        val audit = LifecycleAudit()
        val manager = createManager(items = listOf(item), audit = audit)

        try {
            manager.boot()
            val startTogether = CompletableDeferred<Unit>()
            val commands = List(24) { index ->
                async(Dispatchers.Default) {
                    startTogether.await()
                    when (index % 4) {
                        0 -> manager.resume(item.id)
                        1 -> manager.pause(item.id)
                        2 -> manager.reset(item.id)
                        else -> manager.updateDownloadItem(item.id, null) {
                            it.name = "updated-$index.bin"
                        }
                    }
                }
            }
            startTogether.complete(Unit)
            withTimeout(10_000) { commands.awaitAll() }

            assertEquals(1, audit.maxLifecycleCommands.get())
            assertEquals(0, audit.lifecycleCommandsInFlight.get())
            assertEquals(1, manager.downloadJobs.count { it.id == item.id })
        } finally {
            manager.downloadJobs.forEach(DownloadJob::close)
            manager.downloadDataFolder.deleteRecursively()
        }
    }

    private fun createManager(
        items: List<HttpDownloadItem>,
        audit: LifecycleAudit,
        releaseResume: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) },
        statusOnBoot: Map<Long, DownloadJobStatus> = emptyMap(),
    ): DownloadManager {
        val registry = DownloaderRegistry().apply {
            add(LifecycleTestDownloader(audit, releaseResume, statusOnBoot))
        }
        return DownloadManager(
            dlListDb = TestDownloadListDb(items),
            partListDb = MemoryDownloadPartStatesDB(),
            settings = DownloadSettings(),
            emptyFileCreator = EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: java.io.File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = registry,
            downloadDataFolder = Files.createTempDirectory("lifecycle-safety-").toFile(),
        )
    }

    private fun testItem(id: Long) = HttpDownloadItem(
        link = "https://example.test/$id",
        id = id,
        folder = "downloads",
        name = "$id.bin",
        dateAdded = id,
    )

    private class LifecycleAudit {
        val created = AtomicInteger()
        val activeResumeClaims = AtomicInteger()
        val lifecycleCommandsInFlight = AtomicInteger()
        val maxLifecycleCommands = AtomicInteger()
        val firstResumeEntered = CompletableDeferred<Unit>()
        val pausedIds = java.util.concurrent.ConcurrentLinkedQueue<Long>()

        suspend fun <T> trackLifecycleCommand(block: suspend () -> T): T {
            val current = lifecycleCommandsInFlight.incrementAndGet()
            maxLifecycleCommands.updateAndGet { previous -> maxOf(previous, current) }
            return try {
                delay(10)
                block()
            } finally {
                lifecycleCommandsInFlight.decrementAndGet()
            }
        }
    }

    private class LifecycleTestDownloader(
        private val audit: LifecycleAudit,
        private val releaseResume: CompletableDeferred<Unit>,
        private val statusOnBoot: Map<Long, DownloadJobStatus>,
    ) : Downloader<HttpDownloadItem, LifecycleTestJob, HttpDownloadCredentials> {
        override fun createJob(
            item: HttpDownloadItem,
            downloadManager: DownloadManager,
        ): LifecycleTestJob {
            audit.created.incrementAndGet()
            return LifecycleTestJob(item, downloadManager, audit, releaseResume, statusOnBoot[item.id])
        }

        override fun accept(item: IDownloadItem): Boolean = item is HttpDownloadItem
        override val downloadItemClass = HttpDownloadItem::class
        override val downloadCredentialsClass = HttpDownloadCredentials::class
        override val downloadJobClass = LifecycleTestJob::class
        override val downloadItemSerializer = HttpDownloadItem.serializer()
        override val downloadCredentialsSerializer = HttpDownloadCredentials.serializer()
    }

    private class LifecycleTestJob(
        override val downloadItem: HttpDownloadItem,
        downloadManager: DownloadManager,
        private val audit: LifecycleAudit,
        private val releaseResume: CompletableDeferred<Unit>,
        private val statusOnBoot: DownloadJobStatus?,
    ) : DownloadJob(downloadManager) {
        override suspend fun actualBoot() {
            statusOnBoot?.let { status ->
                _status.value = status
                if (status is DownloadJobStatus.IsActive) {
                    _isDownloadActive.value = true
                }
            }
        }

        override fun getDestination(): DownloadDestination = error("destination is not used")
        override fun initializeDestination() = Unit
        override suspend fun reset() {
            audit.trackLifecycleCommand {
                _isDownloadActive.value = false
                _status.value = DownloadJobStatus.IDLE
            }
        }

        override suspend fun resume() {
            boot()
            if (isDownloadActive.value) return
            _isDownloadActive.value = true
            audit.activeResumeClaims.incrementAndGet()
            audit.trackLifecycleCommand {
                audit.firstResumeEntered.complete(Unit)
                releaseResume.await()
                _status.value = DownloadJobStatus.Downloading
            }
        }

        override suspend fun pause(throwable: Throwable) {
            audit.trackLifecycleCommand {
                _isDownloadActive.value = false
                downloadItem.retryAttempt = 0
                downloadItem.retryAt = null
                _status.value = DownloadJobStatus.Canceled(throwable)
                audit.pausedIds += id
            }
        }

        override suspend fun saveState() = Unit
        override fun getDownloadedSize(): Long = 0
        override fun reloadSettings() = Unit

        override suspend fun changeConfig(
            updater: (IDownloadItem) -> Unit,
            extraConfig: DownloadJobExtraConfig?,
        ): IDownloadItem = audit.trackLifecycleCommand {
            updater(downloadItem)
            downloadItem
        }

        override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) = Unit
    }

    private class TestDownloadListDb(
        private val items: List<HttpDownloadItem>,
    ) : IDownloadListDb {
        override suspend fun getAll(): List<IDownloadItem> = items
        override suspend fun getById(id: Long): IDownloadItem? = items.firstOrNull { it.id == id }
        override suspend fun add(item: IDownloadItem) = error("not used")
        override suspend fun update(item: IDownloadItem) = Unit
        override suspend fun remove(item: IDownloadItem) = error("not used")
        override suspend fun removeById(itemId: Long) = error("not used")
        override suspend fun getLastId(): Long = items.maxOfOrNull { it.id } ?: -1
    }
}
