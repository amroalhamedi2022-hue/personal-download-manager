package ir.amirab.downloader

import ir.amirab.downloader.db.IDownloadListDb
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.hls.HLSDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestartPersistenceSafetyTest {
    private val json = Json

    @Test
    fun bootSelectsEachPendingIdOnlyOnceAndKeepsFirstPersistedRecord() {
        val first = HttpDownloadItem(
            link = "https://example.test/first",
            id = 3,
            folder = "downloads",
            name = "first.bin",
            dateAdded = 1,
        )
        val duplicated = first.copy(
            link = "https://example.test/duplicated",
            name = "duplicated.bin",
        )
        val completed = HttpDownloadItem(
            link = "https://example.test/completed",
            id = 4,
            folder = "downloads",
            name = "completed.bin",
            dateAdded = 2,
            status = DownloadStatus.Completed,
        )
        val pending = HttpDownloadItem(
            link = "https://example.test/pending",
            id = 5,
            folder = "downloads",
            name = "pending.bin",
            dateAdded = 3,
        )

        val selected = pendingDownloadsToBoot(listOf(first, duplicated, completed, pending))

        assertEquals(listOf(3L, 5L), selected.map { it.id })
        assertEquals("first.bin", selected.first().name)
    }

    @Test
    fun httpRetryStateRoundTripsAndLegacySnapshotUsesSafeDefaults() {
        val item = HttpDownloadItem(
            link = "https://example.test/file",
            id = 7,
            folder = "downloads",
            name = "file.bin",
            dateAdded = 1,
            retryAttempt = 2,
            retryAt = 1_234_567L,
        )

        val encoded = json.encodeToString(HttpDownloadItem.serializer(), item)
        val restored = json.decodeFromString(HttpDownloadItem.serializer(), encoded)
        assertEquals(2, restored.retryAttempt)
        assertEquals(1_234_567L, restored.retryAt)

        val legacy = JsonObject(
            json.parseToJsonElement(encoded).jsonObject
                .filterKeys { it != "retryAttempt" && it != "retryAt" }
        )
        val legacyRestored = json.decodeFromString(HttpDownloadItem.serializer(), legacy.toString())
        assertEquals(0, legacyRestored.retryAttempt)
        assertNull(legacyRestored.retryAt)
    }

    @Test
    fun hlsRetryStateRoundTripsAndLegacySnapshotUsesSafeDefaults() {
        val item = HLSDownloadItem(
            id = 9,
            folder = "downloads",
            name = "video.mp4",
            link = "https://example.test/playlist.m3u8",
            dateAdded = 1,
            retryAttempt = 3,
            retryAt = 9_876_543L,
        )

        val encoded = json.encodeToString(HLSDownloadItem.serializer(), item)
        val restored = json.decodeFromString(HLSDownloadItem.serializer(), encoded)
        assertEquals(3, restored.retryAttempt)
        assertEquals(9_876_543L, restored.retryAt)

        val legacy = JsonObject(
            json.parseToJsonElement(encoded).jsonObject
                .filterKeys { it != "retryAttempt" && it != "retryAt" }
        )
        val legacyRestored = json.decodeFromString(HLSDownloadItem.serializer(), legacy.toString())
        assertEquals(0, legacyRestored.retryAttempt)
        assertNull(legacyRestored.retryAt)
    }
    @Test
    fun managerBootDefersRetryUntilAdmissionIsReadyAndNeverCreatesDuplicateJob() = kotlinx.coroutines.runBlocking {
        val first = HttpDownloadItem(
            link = "https://example.test/retry",
            id = 12,
            folder = "downloads",
            name = "retry.bin",
            dateAdded = 1,
            retryAttempt = 2,
            retryAt = System.currentTimeMillis() - 1,
        )
        val duplicated = first.copy(name = "duplicate.bin")
        val completed = first.copy(
            id = 13,
            name = "completed.bin",
            status = DownloadStatus.Completed,
        )
        val audit = JobAudit()
        val registry = DownloaderRegistry().apply {
            add(RetryAwareTestDownloader(audit))
        }
        val manager = DownloadManager(
            dlListDb = DuplicateFriendlyDb(listOf(first, duplicated, completed)),
            partListDb = MemoryDownloadPartStatesDB(),
            settings = DownloadSettings(),
            emptyFileCreator = EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: java.io.File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = registry,
            downloadDataFolder = java.nio.file.Files.createTempDirectory("restart-job-").toFile(),
        )

        try {
            manager.boot()

            assertEquals(listOf(12L), audit.created)
            assertEquals(listOf(12L), audit.booted)
            assertEquals(emptyList(), audit.restoredRetries)

            val dueId = CompletableDeferred<Long>()
            manager.restoreInterruptedRetries { dueId.complete(it) }
            assertEquals(12L, withTimeout(5_000) { dueId.await() })
            manager.resume(12L)

            assertEquals(listOf(12L), audit.restoredRetries)
        } finally {
            manager.downloadJobs.forEach(DownloadJob::close)
            manager.downloadDataFolder.deleteRecursively()
        }
    }

    private class JobAudit {
        val created = mutableListOf<Long>()
        val booted = mutableListOf<Long>()
        val restoredRetries = mutableListOf<Long>()
    }

    private class RetryAwareTestDownloader(
        private val audit: JobAudit,
    ) : Downloader<HttpDownloadItem, RetryAwareTestJob, HttpDownloadCredentials> {
        override fun createJob(
            item: HttpDownloadItem,
            downloadManager: DownloadManager,
        ): RetryAwareTestJob {
            audit.created += item.id
            return RetryAwareTestJob(item, downloadManager, audit)
        }

        override fun accept(item: IDownloadItem): Boolean = item is HttpDownloadItem
        override val downloadItemClass = HttpDownloadItem::class
        override val downloadCredentialsClass = HttpDownloadCredentials::class
        override val downloadJobClass = RetryAwareTestJob::class
        override val downloadItemSerializer = HttpDownloadItem.serializer()
        override val downloadCredentialsSerializer = HttpDownloadCredentials.serializer()
    }

    private class RetryAwareTestJob(
        override val downloadItem: HttpDownloadItem,
        downloadManager: DownloadManager,
        private val audit: JobAudit,
    ) : DownloadJob(downloadManager) {
        override suspend fun actualBoot() {
            audit.booted += id
        }

        override suspend fun restoreInterruptedRetry() {
            if (downloadItem.retryAt != null) {
                audit.restoredRetries += id
            }
        }

        override fun getDestination(): DownloadDestination = error("not needed for boot")
        override fun initializeDestination() = Unit
        override suspend fun reset() = Unit
        override suspend fun resume() = Unit
        override suspend fun pause(throwable: Throwable) = Unit
        override suspend fun saveState() = Unit
        override fun getDownloadedSize(): Long = 0
        override fun reloadSettings() = Unit

        override suspend fun changeConfig(
            updater: (IDownloadItem) -> Unit,
            extraConfig: DownloadJobExtraConfig?,
        ): IDownloadItem = downloadItem

        override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) = Unit
    }

    private class DuplicateFriendlyDb(
        private val items: List<IDownloadItem>,
    ) : IDownloadListDb {
        override suspend fun getAll(): List<IDownloadItem> = items
        override suspend fun getById(id: Long): IDownloadItem? = items.firstOrNull { it.id == id }
        override suspend fun add(item: IDownloadItem) = error("not needed for boot")
        override suspend fun update(item: IDownloadItem) = error("not needed for boot")
        override suspend fun remove(item: IDownloadItem) = error("not needed for boot")
        override suspend fun removeById(itemId: Long) = error("not needed for boot")
        override suspend fun getLastId(): Long = items.maxOfOrNull { it.id } ?: -1
    }

}
