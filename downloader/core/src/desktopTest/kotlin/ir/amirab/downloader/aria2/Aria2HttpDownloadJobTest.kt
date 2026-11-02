package ir.amirab.downloader.aria2

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.db.MemoryDownloadListDB
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.downloaditem.http.OptionalHttpDownloadJobFactory
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Aria2HttpDownloadJobTest {
    @Test
    fun downloadManagerRoutesPlainHttpThroughAdapterAndPublishesAtomically() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.manager.boot()
            assertIs<Aria2HttpDownloadJob>(fixture.manager.downloadJobs.single())

            fixture.manager.resume(fixture.item.id)
            await { fixture.engine.addCalls == 1 && fixture.engine.state == Aria2TransferState.ACTIVE }
            assertFalse(fixture.output.exists(), "staged bytes must not be published before completion")

            fixture.engine.complete(Fixture.PAYLOAD)
            await {
                fixture.item.status == DownloadStatus.Completed &&
                    fixture.output.isFile &&
                    fixture.engine.removeCalls == 1 &&
                    !fixture.engine.contains(fixture.item.id)
            }

            assertContentEquals(Fixture.PAYLOAD, fixture.output.readBytes())
            assertEquals(Fixture.PAYLOAD.size.toLong(), fixture.item.contentLength)
            assertEquals(1, fixture.engine.removeCalls)
            assertFalse(fixture.engine.contains(fixture.item.id))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun pauseAndResumeReuseOneHyperGetTaskWithoutDuplicateAria2Adds() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.manager.boot()
            fixture.manager.resume(fixture.item.id)
            await { fixture.engine.state == Aria2TransferState.ACTIVE }

            fixture.manager.pause(fixture.item.id)
            assertEquals(Aria2TransferState.PAUSED, fixture.engine.state)
            assertEquals(DownloadStatus.Paused, fixture.item.status)

            fixture.manager.resume(fixture.item.id)
            await { fixture.engine.state == Aria2TransferState.ACTIVE }
            assertEquals(1, fixture.engine.addCalls)
            assertEquals(1, fixture.engine.unpauseCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun activeSettingsChangePreservesOwnedPartialAndRestartsWithoutCollision() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.manager.boot()
            fixture.manager.resume(fixture.item.id)
            await { fixture.engine.state == Aria2TransferState.ACTIVE }
            fixture.engine.createOwnedPartial()

            fixture.manager.updateDownloadItem(fixture.item.id, null) {
                it.speedLimit = 2_000_000
            }
            await { fixture.engine.state == Aria2TransferState.ACTIVE && fixture.engine.addCalls == 2 }

            assertTrue(fixture.engine.lastRequest.resumeExistingPartial)
            assertTrue(fixture.engine.controlPath().toFile().isFile)
            assertEquals(2_000_000, fixture.engine.lastRequest.speedLimitBytesPerSecond)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun credentialedHttpStaysOnBuiltInEngine() {
        val runtime = Aria2EngineRuntime(Aria2EngineFeatureFlag { "true" }) { null }
        val factory = Aria2HttpDownloadJobFactory(runtime)
        val item = Fixture.item(Files.createTempDirectory("aria2-credentials-").toFile()).copy(
            headers = mapOf("Authorization" to "secret"),
        )
        assertEquals(null, factory.create(item, Fixture.managerFor(item, OptionalHttpDownloadJobFactory.Disabled)))
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    private class Fixture {
        val root = Files.createTempDirectory("aria2-http-job-").toFile()
        val item = item(root)
        val engine = FakeEngine()
        val manager = managerFor(
            item,
            OptionalHttpDownloadJobFactory { httpItem, downloadManager ->
                Aria2HttpDownloadJob(
                    httpItem,
                    downloadManager,
                    engine,
                    pollInterval = Duration.ofMillis(10),
                )
            },
        )
        val output = root.resolve(item.name)

        fun close() {
            manager.downloadJobs.forEach(DownloadJob::close)
            root.deleteRecursively()
        }

        companion object {
            val PAYLOAD = ByteArray(256 * 1024) { ((it * 17 + 3) and 0xff).toByte() }

            fun item(root: java.io.File) = HttpDownloadItem(
                link = "https://example.test/fixture.bin",
                id = 77,
                folder = root.path,
                name = "fixture.bin",
                dateAdded = 77,
            )

            fun managerFor(
                item: HttpDownloadItem,
                factory: OptionalHttpDownloadJobFactory,
            ): DownloadManager {
                val db = MemoryDownloadListDB().also { runBlocking { it.add(item) } }
                val registry = DownloaderRegistry().apply {
                    add(HttpDownloader(lazy { error("built-in client must not be resolved") }, factory))
                }
                return DownloadManager(
                    dlListDb = db,
                    partListDb = MemoryDownloadPartStatesDB(),
                    settings = DownloadSettings(),
                    emptyFileCreator = EmptyFileCreator(
                        diskStat = object : IDiskStat {
                            override fun getRemainingSpace(path: java.io.File): Long = Long.MAX_VALUE
                        },
                        useSparseFile = { false },
                    ),
                    downloaderRegistry = registry,
                    downloadDataFolder = java.io.File(item.folder),
                )
            }
        }
    }

    private class FakeEngine : Aria2TaskEngine {
        var addCalls = 0
        var unpauseCalls = 0
        var removeCalls = 0
        var state = Aria2TransferState.WAITING
        private var bound = false
        private var completed = 0L
        private var total: Long? = null
        lateinit var lastRequest: Aria2AddRequest
            private set

        override fun add(request: Aria2AddRequest): Long {
            if (!bound) {
                addCalls++
                bound = true
                this.lastRequest = request
                state = Aria2TransferState.ACTIVE
            }
            return request.hyperGetTaskId
        }

        override fun status(hyperGetTaskId: Long) = Aria2TaskSnapshot(
            hyperGetTaskId,
            Aria2ProgressSample(state, completed, total, 0),
            errorCode = null,
            hasErrorMessage = false,
        )

        override fun pause(hyperGetTaskId: Long) {
            state = Aria2TransferState.PAUSED
        }

        override fun unpause(hyperGetTaskId: Long) {
            unpauseCalls++
            state = Aria2TransferState.ACTIVE
        }

        override fun cancel(hyperGetTaskId: Long): Boolean = remove(hyperGetTaskId)

        override fun remove(hyperGetTaskId: Long): Boolean {
            if (!bound) return false
            removeCalls++
            Files.deleteIfExists(controlPath())
            bound = false
            return true
        }

        override fun contains(hyperGetTaskId: Long): Boolean = bound

        fun complete(bytes: ByteArray) {
            val path = lastRequest.destinationDirectory.resolve(lastRequest.fileName)
            Files.write(path, bytes)
            completed = bytes.size.toLong()
            total = completed
            state = Aria2TransferState.COMPLETE
        }

        fun controlPath() = lastRequest.destinationDirectory.resolve("${lastRequest.fileName}.aria2")

        fun createOwnedPartial() {
            Files.write(lastRequest.destinationDirectory.resolve(lastRequest.fileName), byteArrayOf(1, 2, 3))
            Files.write(controlPath(), byteArrayOf(4, 5, 6))
            completed = 3
        }
    }
}
