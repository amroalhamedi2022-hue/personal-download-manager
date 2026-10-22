package ir.amirab.downloader

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.db.MemoryDownloadListDB
import ir.amirab.downloader.db.MemoryDownloadPartStatesDB
import ir.amirab.downloader.db.DownloadQueuePersistedDataAccess
import ir.amirab.downloader.db.QueueModel
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.hls.HLSDownloadItem
import ir.amirab.downloader.downloaditem.hls.HLSDownloader
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.queue.ManualDownloadQueue
import ir.amirab.downloader.queue.DownloadQueue
import ir.amirab.downloader.queue.ScheduleTimes
import ir.amirab.downloader.part.RangedParts
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import ir.amirab.downloader.utils.ISparseFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActualRestoredRetryJobTest {

    @Test
    fun httpPartFailuresExhaustExactlyTwoRetriesWithoutProgress() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("retry-budget-output-")
            val data = createTempDir("retry-budget-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/81.bin",
                id = 81,
                folder = output.path,
                name = "81.bin",
                dateAdded = 81,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                manager.resume(item.id)
                withTimeout(20_000) {
                    while (job.status.value !is DownloadJobStatus.Canceled) delay(10)
                }

                assertFalse(job.isDownloadActive.value)
                assertEquals(3, fixture.partRequestsFor("/file/81.bin"))
                assertEquals(6, fixture.requestsFor("/file/81.bin"))
                assertNull(item.retryAt)
                assertEquals(0, item.retryAttempt)
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun realByteProgressStartsOneFreshRetryBudget() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("retry-progress-output-")
            val data = createTempDir("retry-progress-data-")
            val listDb = MemoryDownloadListDB()
            val partDb = MemoryDownloadPartStatesDB()
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/86.bin",
                id = 86,
                folder = output.path,
                name = "86.bin",
                dateAdded = 86,
                preferredConnectionCount = 1,
            )
            val firstManager = createHttpManager(
                items = listOf(item),
                client = fixture.client,
                dataFolder = data,
                listDb = listDb,
                partDb = partDb,
            )
            var restartedManager: DownloadManager? = null
            var firstManagerClosed = false

            try {
                firstManager.boot()
                val firstJob = firstManager.downloadJobs.single()
                firstManager.resume(item.id)
                withTimeout(15_000) {
                    while (firstJob.getDownloadedSize() == 0L || item.retryAt == null) delay(10)
                }
                withTimeout(5_000) {
                    while (
                        ((partDb.getParts(item.id) as? RangedParts)
                            ?.list
                            ?.sumOf { it.howMuchProceed() } ?: 0L) == 0L
                    ) delay(10)
                }

                assertEquals(1, item.retryAttempt)
                closeManager(firstManager)
                firstManagerClosed = true

                val secondManager = createHttpManager(
                    items = listOf(item),
                    client = fixture.client,
                    dataFolder = data,
                    listDb = listDb,
                    partDb = partDb,
                )
                restartedManager = secondManager
                secondManager.boot()
                val restoredJob = secondManager.downloadJobs.single()
                secondManager.resume(item.id)
                withTimeout(25_000) {
                    while (restoredJob.status.value !is DownloadJobStatus.Canceled) delay(10)
                }
                assertEquals(4, fixture.partRequestsFor("/file/86.bin"))
                assertNull(item.retryAt)
                assertEquals(0, item.retryAttempt)
            } finally {
                restartedManager?.let(::closeManager)
                if (!firstManagerClosed) closeManager(firstManager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun http429RetryAfterPreventsEarlyMetadataRetry() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("retry-after-http-output-")
            val data = createTempDir("retry-after-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/82.bin",
                id = 82,
                folder = output.path,
                name = "82.bin",
                dateAdded = 82,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                manager.resume(item.id)
                withTimeout(15_000) {
                    while (item.status != DownloadStatus.Completed) delay(10)
                }

                val times = fixture.requestTimesFor("/file/82.bin")
                assertTrue(times.size >= 3)
                assertTrue(
                    TimeUnit.NANOSECONDS.toMillis(times[1] - times[0]) >= 900,
                    "metadata retry was sent before Retry-After elapsed",
                )
                assertContentEquals(fixture.fileBytes(item.id), File(item.folder, item.name).readBytes())
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun retryAfterOneHourIsPersistedWithoutOverflowOrEarlyRequest() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("retry-hour-output-")
            val data = createTempDir("retry-hour-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/83.bin",
                id = 83,
                folder = output.path,
                name = "83.bin",
                dateAdded = 83,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                manager.resume(item.id)
                withTimeout(5_000) {
                    while (item.retryAt == null || job.status.value !is DownloadJobStatus.Retrying) delay(10)
                }
                val remaining = requireNotNull(item.retryAt) - System.currentTimeMillis()
                assertTrue(remaining >= 3_590_000, "one-hour Retry-After was shortened: $remaining")
                assertEquals(1, fixture.requestsFor("/file/83.bin"))
                delay(250)
                assertEquals(1, fixture.requestsFor("/file/83.bin"))
                withTimeout(10_000) { manager.stopAll() }
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
            Unit
        }
    }

    @Test
    fun http404IsPermanentAndDoesNotRetry() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("permanent-http-output-")
            val data = createTempDir("permanent-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/84.bin",
                id = 84,
                folder = output.path,
                name = "84.bin",
                dateAdded = 84,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                manager.resume(item.id)
                withTimeout(5_000) {
                    while (job.status.value !is DownloadJobStatus.Canceled) delay(10)
                }
                delay(250)
                assertEquals(1, fixture.requestsFor("/file/84.bin"))
                assertNull(item.retryAt)
                assertFalse(job.isDownloadActive.value)
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun hls503SegmentPreservesRetryAfterAndRecoversThroughJobBudget() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("retry-after-hls-output-")
            val data = createTempDir("retry-after-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/85.m3u8",
                id = 85,
                folder = output.path,
                name = "85.ts",
                dateAdded = 85,
                preferredConnectionCount = 1,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                manager.resume(item.id)
                withTimeout(15_000) {
                    while (item.status != DownloadStatus.Completed) delay(10)
                }

                val times = fixture.requestTimesFor("/segment/85.ts")
                assertEquals(2, times.size)
                assertTrue(
                    TimeUnit.NANOSECONDS.toMillis(times[1] - times[0]) >= 900,
                    "HLS segment retry was sent before Retry-After elapsed",
                )
                assertContentEquals(fixture.segmentBytes(item.id), File(item.folder, item.name).readBytes())
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllCancelsFutureActualHttpRetryBeforeAnyRequest() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-future-http-output-")
            val data = createTempDir("stop-future-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/71.bin",
                id = 71,
                folder = output.path,
                name = "71.bin",
                dateAdded = 71,
                retryAttempt = 2,
                retryAt = System.currentTimeMillis() + 1_200,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                runFutureRetryStoppedBeforeAdmission(manager, item)
                assertEquals(0, fixture.requestsFor("/file/71.bin"))
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllCancelsFutureActualHlsRetryBeforeAnyRequest() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-future-hls-output-")
            val data = createTempDir("stop-future-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/72.m3u8",
                id = 72,
                folder = output.path,
                name = "72.ts",
                dateAdded = 72,
                retryAttempt = 2,
                retryAt = System.currentTimeMillis() + 1_200,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)

            try {
                runFutureRetryStoppedBeforeAdmission(manager, item)
                assertEquals(0, fixture.requestsFor("/playlist/72.m3u8"))
                assertEquals(0, fixture.requestsFor("/segment/72.ts"))
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllHaltsActualHttpTransferWithoutLateCompletion() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-http-output-")
            val data = createTempDir("stop-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/61.bin",
                id = 61,
                folder = output.path,
                name = "61.bin",
                dateAdded = 61,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)
            val completions = AtomicInteger()
            val eventJob = manager.scope.launch {
                manager.listOfJobsEvents.collect { event ->
                    if (event is DownloadManagerEvents.OnJobCompleted) completions.incrementAndGet()
                }
            }

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 1 } }
                manager.resume(item.id)
                withTimeout(10_000) {
                    while (
                        job.status.value != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/file/61.bin") < 2
                    ) {
                        delay(5)
                    }
                }

                withTimeout(10_000) { manager.stopAll() }
                val requestsAfterStop = fixture.requestsFor("/file/61.bin")
                delay(250)

                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                assertNull(job.activeDownloadScope)
                assertNull(item.retryAt)
                assertEquals(0, completions.get())
                assertEquals(requestsAfterStop, fixture.requestsFor("/file/61.bin"))
            } finally {
                eventJob.cancel()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllHaltsActualHlsTransferWithoutLateCompletion() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-hls-output-")
            val data = createTempDir("stop-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/62.m3u8",
                id = 62,
                folder = output.path,
                name = "62.ts",
                dateAdded = 62,
                preferredConnectionCount = 1,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)
            val completions = AtomicInteger()
            val eventJob = manager.scope.launch {
                manager.listOfJobsEvents.collect { event ->
                    if (event is DownloadManagerEvents.OnJobCompleted) completions.incrementAndGet()
                }
            }

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 1 } }
                manager.resume(item.id)
                withTimeout(10_000) {
                    while (
                        job.status.value != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/segment/62.ts") < 1
                    ) {
                        delay(5)
                    }
                }

                withTimeout(10_000) { manager.stopAll() }
                val requestsAfterStop = fixture.requestsFor("/playlist/62.m3u8") +
                    fixture.requestsFor("/segment/62.ts")
                delay(250)

                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                assertNull(job.activeDownloadScope)
                assertNull(item.retryAt)
                assertEquals(0, completions.get())
                assertEquals(
                    requestsAfterStop,
                    fixture.requestsFor("/playlist/62.m3u8") + fixture.requestsFor("/segment/62.ts"),
                )
            } finally {
                eventJob.cancel()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllWaitsForActualHttpResumingThenLeavesNoLateWorker() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-resuming-output-")
            val data = createTempDir("stop-resuming-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/101.bin",
                id = 101,
                folder = output.path,
                name = "101.bin",
                dateAdded = 101,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                val resume = async(Dispatchers.Default) { manager.resume(item.id) }
                assertTrue(fixture.awaitBlockedInfoRequest())
                assertTrue(job.status.value is DownloadJobStatus.Resuming)
                assertTrue(job.isDownloadActive.value)

                val stop = async(Dispatchers.Default) { manager.stopAll() }
                delay(100)
                assertFalse(stop.isCompleted, "Stop All returned before the in-flight Resume command drained")

                fixture.releaseBlockedInfoRequest()
                withTimeout(15_000) {
                    resume.await()
                    stop.await()
                }
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                val requestsAfterStop = fixture.requestsFor("/file/101.bin")
                delay(200)

                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                assertFalse(job.isDownloadActive.value)
                assertEquals(requestsAfterStop, fixture.requestsFor("/file/101.bin"))
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
            } finally {
                fixture.releaseBlockedInfoRequest()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stopAllWaitsForActualPreparingFileThenLeavesNoLateWorker() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-preparing-output-")
            val data = createTempDir("stop-preparing-data-")
            val sparseGate = BlockingSparseFile()
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/102.bin",
                id = 102,
                folder = output.path,
                name = "102.bin",
                dateAdded = 102,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(
                items = listOf(item),
                client = fixture.client,
                dataFolder = data,
                emptyFileCreator = EmptyFileCreator(
                    diskStat = object : IDiskStat {
                        override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
                    },
                    useSparseFile = { true },
                    sparseFile = sparseGate,
                ),
            )

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                val resume = async(Dispatchers.Default) { manager.resume(item.id) }
                assertTrue(sparseGate.awaitCreate())
                assertTrue(job.status.value is DownloadJobStatus.PreparingFile)
                assertTrue(job.isDownloadActive.value)

                val stop = async(Dispatchers.Default) { manager.stopAll() }
                delay(100)
                assertFalse(stop.isCompleted, "Stop All returned while file preparation was still in flight")

                sparseGate.releaseCreate()
                withTimeout(15_000) {
                    resume.await()
                    stop.await()
                }
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                val requestsAfterStop = fixture.requestsFor("/file/102.bin")
                delay(200)

                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                assertFalse(job.isDownloadActive.value)
                assertEquals(requestsAfterStop, fixture.requestsFor("/file/102.bin"))
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
            } finally {
                sparseGate.releaseCreate()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun stoppingActualQueueKeepsQueuedHttpItemFromStartingLater() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stop-queued-output-")
            val data = createTempDir("stop-queued-data-")
            val items = listOf(103L, 104L).map { id ->
                HttpDownloadItem(
                    link = "${fixture.baseUrl}/file/$id.bin",
                    id = id,
                    folder = output.path,
                    name = "$id.bin",
                    dateAdded = id,
                    preferredConnectionCount = 1,
                )
            }
            val manager = createHttpManager(items, fixture.client, data)
            val persisted = InMemoryQueueData(
                QueueModel(
                    id = 79,
                    name = "Stop All queued coverage",
                    maxConcurrent = 1,
                    queueItems = items.map { it.id },
                )
            )
            val queue = DownloadQueue(
                persistedModel = persisted.model,
                persistedData = persisted,
                downloadEvents = manager,
                onQueueEvent = {},
            )

            try {
                manager.boot()
                queue.boot()
                withTimeout(5_000) {
                    manager.listOfJobsEvents.subscriptionCount.first { it >= 1 }
                }
                queue.start()
                withTimeout(10_000) {
                    while (
                        manager.getJobStatusOf(103) != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/file/103.bin") < 2
                    ) {
                        delay(5)
                    }
                }
                assertEquals(0, fixture.requestsFor("/file/104.bin"))

                // Mirrors DownloadSystem.stopAnything(): stop queue admission before manager workers.
                queue.stopAsync()
                manager.stopAll()
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                val firstRequestsAfterStop = fixture.requestsFor("/file/103.bin")
                delay(700) // longer than DownloadQueue's debounced shake

                assertFalse(queue.isQueueActive)
                assertTrue(manager.downloadJobs.none { it.isDownloadActive.value })
                assertEquals(0, fixture.requestsFor("/file/104.bin"))
                assertEquals(firstRequestsAfterStop, fixture.requestsFor("/file/103.bin"))
                assertEquals(DownloadJobStatus.IDLE, manager.getJobStatusOf(104))
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
            } finally {
                queue.dispose()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun concurrentResumeCommandsStartOneActualHttpLifecycle() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("concurrent-http-output-")
            val data = createTempDir("concurrent-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/51.bin",
                id = 51,
                folder = output.path,
                name = "51.bin",
                dateAdded = 51,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)
            val started = AtomicInteger()
            val completed = CompletableDeferred<Unit>()
            val eventJob = manager.scope.launch {
                manager.listOfJobsEvents
                    .onEach { event ->
                        if (event is DownloadManagerEvents.OnJobStarting) started.incrementAndGet()
                    }
                    .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                    .first()
                completed.complete(Unit)
            }

            try {
                manager.boot()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 1 } }
                coroutineScope {
                    List(16) { async { manager.resume(item.id) } }.awaitAll()
                }
                withTimeout(20_000) { completed.await() }

                assertEquals(1, started.get())
                assertEquals(1, fixture.peakTransfers.get())
                assertContentEquals(fixture.fileBytes(item.id), File(item.folder, item.name).readBytes())
            } finally {
                eventJob.cancel()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun concurrentResumeCommandsStartOneActualHlsLifecycle() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("concurrent-hls-output-")
            val data = createTempDir("concurrent-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/52.m3u8",
                id = 52,
                folder = output.path,
                name = "52.ts",
                dateAdded = 52,
                preferredConnectionCount = 1,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)
            val started = AtomicInteger()
            val completed = CompletableDeferred<Unit>()
            val eventJob = manager.scope.launch {
                manager.listOfJobsEvents
                    .onEach { event ->
                        if (event is DownloadManagerEvents.OnJobStarting) started.incrementAndGet()
                    }
                    .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                    .first()
                completed.complete(Unit)
            }

            try {
                manager.boot()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 1 } }
                coroutineScope {
                    List(16) { async { manager.resume(item.id) } }.awaitAll()
                }
                val completedInTime = withTimeoutOrNull(20_000) {
                    completed.await()
                    true
                } ?: false
                val segmentDiagnostics = (manager.downloadJobs.find { it.id == item.id }
                    as? ir.amirab.downloader.downloaditem.hls.HLSDownloadJob)
                    ?.getParts()
                    ?.map { part ->
                        "${part.segmentIndex}:${part.statusFlow.value}:bytes=${part.current}:length=${part.length}"
                    }
                assertTrue(
                    completedInTime,
                    "HLS lifecycle did not emit completion: itemStatus=${item.status}, " +
                        "jobStatus=${manager.getJobStatusOf(item.id)}, " +
                        "active=${manager.downloadJobs.find { it.id == item.id }?.isDownloadActive?.value}, " +
                        "playlistRequests=${fixture.requestsFor("/playlist/52.m3u8")}, " +
                        "segmentRequests=${fixture.requestsFor("/segment/52.ts")}, " +
                        "bytes=${File(item.folder, item.name).length()}, " +
                        "peakTransfers=${fixture.peakTransfers.get()}, " +
                        "activeServerTransfers=${fixture.activeTransferCount()}, " +
                        "segments=$segmentDiagnostics",
                )

                assertEquals(1, started.get())
                assertEquals(1, fixture.peakTransfers.get())
                assertContentEquals(fixture.segmentBytes(item.id), File(item.folder, item.name).readBytes())
            } finally {
                eventJob.cancel()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun concurrentResumeResetAndLinkUpdateKeepOneActualHttpLifecycle() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("concurrent-lifecycle-output-")
            val data = createTempDir("concurrent-lifecycle-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/91.bin",
                id = 91,
                folder = output.path,
                name = "lifecycle.bin",
                dateAdded = 91,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)
            manager.configureHostLimits("127.0.0.1", maxConnections = 1)

            try {
                manager.boot()
                manager.resume(item.id)
                val job = manager.downloadJobs.single()
                withTimeout(10_000) {
                    while (
                        job.status.value != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/file/91.bin") < 2
                    ) {
                        delay(5)
                    }
                }

                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.link = "${fixture.baseUrl}/file/92.bin"
                }
                assertTrue(
                    fixture.requestsFor("/file/92.bin") >= 1,
                    "link update returned before the replacement lifecycle reached the server",
                )

                val startTogether = CompletableDeferred<Unit>()
                coroutineScope {
                    listOf(
                        async(Dispatchers.Default) {
                            startTogether.await()
                            manager.resume(item.id)
                        },
                        async(Dispatchers.Default) {
                            startTogether.await()
                            manager.reset(item.id)
                        },
                        async(Dispatchers.Default) {
                            startTogether.await()
                            manager.updateDownloadItem(item.id, null) { updated ->
                                updated.link = "${fixture.baseUrl}/file/91.bin"
                            }
                        },
                    ).also { startTogether.complete(Unit) }.awaitAll()
                }

                // Establish a deterministic final generation after the contested commands.
                manager.reset(item.id)
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                assertFalse(job.isDownloadActive.value)
                assertEquals(DownloadStatus.Added, item.status)
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
                assertEquals(1, manager.downloadJobs.count { it.id == item.id })

                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.link = "${fixture.baseUrl}/file/92.bin"
                }
                val completed = CompletableDeferred<Unit>()
                val completionJob = manager.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    manager.listOfJobsEvents
                        .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                        .first { it.downloadItem.id == item.id }
                    completed.complete(Unit)
                }
                try {
                    withTimeout(5_000) {
                        manager.listOfJobsEvents.subscriptionCount.first { it >= 1 }
                    }
                    manager.resume(item.id)
                    val completedInTime = withTimeoutOrNull(20_000) {
                        completed.await()
                        true
                    } ?: false
                    assertTrue(
                        completedInTime,
                        "HTTP lifecycle did not emit completion: " +
                            "status=${job.status.value}, itemStatus=${item.status}, " +
                            "active=${job.isDownloadActive.value}, " +
                            "requests91=${fixture.requestsFor("/file/91.bin")}, " +
                            "requests92=${fixture.requestsFor("/file/92.bin")}, " +
                            "activeTransfers=${fixture.activeTransferCount()}, " +
                            "hostConnections=${manager.hostLimitRegistry.activeConnections("127.0.0.1")}",
                    )
                } finally {
                    completionJob.cancel()
                }

                assertContentEquals(
                    fixture.fileBytes(92),
                    File(item.folder, item.name).readBytes(),
                )
                assertFalse(job.isDownloadActive.value)
                assertEquals(0, manager.hostLimitRegistry.activeConnections("127.0.0.1"))
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun restoredHttpRetryStopsBeforeMovingDestinationAndCanResumeSafely() = withRetryServer { fixture ->
        runBlocking {
            val oldOutput = createTempDir("move-http-old-")
            val newOutput = createTempDir("move-http-new-")
            val data = createTempDir("move-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/41.bin",
                id = 41,
                folder = oldOutput.path,
                name = "before.bin",
                dateAdded = 41,
                retryAttempt = 1,
                retryAt = System.currentTimeMillis() - 1,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)
            val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = ManualDownloadQueue(manager, queueScope)
            val completed = CompletableDeferred<Unit>()
            val completionJob = queueScope.launch {
                manager.listOfJobsEvents
                    .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                    .first()
                completed.complete(Unit)
            }

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                queue.boot()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 2 } }
                queue.setMaxConcurrent(1)
                manager.restoreInterruptedRetries { queue.resume(it) }
                withTimeout(10_000) {
                    while (
                        job.status.value != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/file/41.bin") < 2
                    ) {
                        delay(5)
                    }
                }

                val oldFile = File(oldOutput, "before.bin")
                val newFile = File(newOutput, "after.bin")
                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.folder = newOutput.path
                    updated.name = newFile.name
                    // C04: change transfer configuration atomically with the destination.
                    updated.preferredConnectionCount = 2
                    updated.speedLimit = 64L * 1024L * 1024L
                }

                assertEquals(2, job.downloadItem.preferredConnectionCount)
                assertEquals(64L * 1024L * 1024L, job.downloadItem.speedLimit)
                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                assertEquals(0, fixture.activeTransferCount())
                assertFalse(oldFile.exists())
                assertTrue(newFile.exists())
                val bytesAfterMove = newFile.readBytes()
                delay(150)
                assertContentEquals(bytesAfterMove, newFile.readBytes())

                manager.resume(item.id)
                val completedInTime = withTimeoutOrNull(20_000) {
                    completed.await()
                    true
                } ?: false
                assertTrue(
                    completedInTime,
                    "HTTP resume after move did not finish: status=${job.status.value}, " +
                        "active=${job.isDownloadActive.value}, retryAt=${item.retryAt}, " +
                        "requests=${fixture.requestsFor("/file/41.bin")}, output=${newFile.length()}",
                )
                assertContentEquals(fixture.fileBytes(item.id), newFile.readBytes())
                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Finished)
            } finally {
                completionJob.cancel()
                queue.clearQueue()
                queueScope.cancel()
                closeManager(manager)
                oldOutput.deleteRecursively()
                newOutput.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun restoredHlsRetryStopsBeforeMovingDestinationAndCanResumeSafely() = withRetryServer { fixture ->
        runBlocking {
            val oldOutput = createTempDir("move-hls-old-")
            val newOutput = createTempDir("move-hls-new-")
            val data = createTempDir("move-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/42.m3u8",
                id = 42,
                folder = oldOutput.path,
                name = "before.ts",
                dateAdded = 42,
                retryAttempt = 1,
                retryAt = System.currentTimeMillis() - 1,
                preferredConnectionCount = 1,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)
            val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = ManualDownloadQueue(manager, queueScope)
            val completed = CompletableDeferred<Unit>()
            val completionJob = queueScope.launch {
                manager.listOfJobsEvents
                    .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                    .first()
                completed.complete(Unit)
            }

            try {
                manager.boot()
                val job = manager.downloadJobs.single()
                queue.boot()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 2 } }
                queue.setMaxConcurrent(1)
                manager.restoreInterruptedRetries { queue.resume(it) }
                withTimeout(10_000) {
                    while (
                        job.status.value != DownloadJobStatus.Downloading ||
                        fixture.requestsFor("/segment/42.ts") < 1
                    ) {
                        delay(5)
                    }
                }

                val oldFile = File(oldOutput, "before.ts")
                val newFile = File(newOutput, "after.ts")
                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.folder = newOutput.path
                    updated.name = newFile.name
                    // C04: change transfer configuration atomically with the destination.
                    updated.preferredConnectionCount = 2
                    updated.speedLimit = 64L * 1024L * 1024L
                }

                assertEquals(2, job.downloadItem.preferredConnectionCount)
                assertEquals(64L * 1024L * 1024L, job.downloadItem.speedLimit)
                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                assertFalse(oldFile.exists())
                delay(150)
                assertFalse(oldFile.exists())

                manager.resume(item.id)
                val completedInTime = withTimeoutOrNull(20_000) {
                    completed.await()
                    true
                } ?: false
                assertTrue(
                    completedInTime,
                    "HLS resume after move did not finish: status=${job.status.value}, " +
                        "active=${job.isDownloadActive.value}, retryAt=${item.retryAt}, " +
                        "playlistRequests=${fixture.requestsFor("/playlist/42.m3u8")}, " +
                        "segmentRequests=${fixture.requestsFor("/segment/42.ts")}, output=${newFile.length()}",
                )
                assertContentEquals(fixture.segmentBytes(item.id), newFile.readBytes())
                assertEquals(1, fixture.peakTransfers.get())
            } finally {
                completionJob.cancel()
                queue.clearQueue()
                queueScope.cancel()
                closeManager(manager)
                oldOutput.deleteRecursively()
                newOutput.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun dueHttpRetryOwnedByStoppedQueueMakesNoRequestUntilStarted() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stopped-http-output-")
            val data = createTempDir("stopped-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/21.bin",
                id = 21,
                folder = output.path,
                name = "21.bin",
                dateAdded = 21,
                retryAttempt = 1,
                retryAt = System.currentTimeMillis() - 1,
                preferredConnectionCount = 2,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)

            try {
                runRestoredJobOwnedByStoppedQueue(manager, item.id) {
                    assertEquals(0, fixture.requestsFor("/file/21.bin"))
                }
                assertContentEquals(fixture.fileBytes(item.id), File(item.folder, item.name).readBytes())
                assertTrue(fixture.requestsFor("/file/21.bin") >= 2)
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun dueHlsRetryOwnedByStoppedQueueMakesNoRequestUntilStarted() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("stopped-hls-output-")
            val data = createTempDir("stopped-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/22.m3u8",
                id = 22,
                folder = output.path,
                name = "22.ts",
                dateAdded = 22,
                retryAttempt = 1,
                retryAt = System.currentTimeMillis() - 1,
                preferredConnectionCount = 2,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)

            try {
                runRestoredJobOwnedByStoppedQueue(manager, item.id) {
                    assertEquals(0, fixture.requestsFor("/playlist/22.m3u8"))
                    assertEquals(0, fixture.requestsFor("/segment/22.ts"))
                }
                assertContentEquals(fixture.segmentBytes(item.id), File(item.folder, item.name).readBytes())
                assertTrue(fixture.requestsFor("/playlist/22.m3u8") >= 1)
                assertTrue(fixture.requestsFor("/segment/22.ts") >= 1)
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun scheduledRestoredHttpRetriesWaitForWindowAndRespectQueueHostAndPartLimits() =
        withRetryServer { fixture ->
            runBlocking {
                val output = createTempDir("scheduled-retry-output-")
                val data = createTempDir("scheduled-retry-data-")
                val items = listOf(71L, 72L).map { id ->
                    HttpDownloadItem(
                        link = "${fixture.baseUrl}/file/$id.bin",
                        id = id,
                        folder = output.path,
                        name = "$id.bin",
                        dateAdded = id,
                        retryAttempt = 1,
                        retryAt = System.currentTimeMillis() - 1,
                        preferredConnectionCount = 2,
                    )
                }
                val manager = createHttpManager(items, fixture.client, data)
                manager.configureHostLimits("127.0.0.1", maxConnections = 3)

                val scheduledStart = ZonedDateTime.now().plusSeconds(4)
                val persisted = InMemoryQueueData(
                    QueueModel(
                        id = 78,
                        name = "Scheduled restored retries",
                        maxConcurrent = 2,
                        queueItems = items.map { it.id },
                        scheduledTimes = ScheduleTimes(
                            daysOfWeek = setOf(DayOfWeek.valueOf(scheduledStart.dayOfWeek.name)),
                            startTime = LocalTime(
                                scheduledStart.hour,
                                scheduledStart.minute,
                                scheduledStart.second,
                                scheduledStart.nano,
                            ),
                            endTime = LocalTime(23, 59),
                            enabledStartTime = true,
                            enabledEndTime = false,
                        ),
                    )
                )
                val queue = DownloadQueue(
                    persistedModel = persisted.model,
                    persistedData = persisted,
                    downloadEvents = manager,
                    onQueueEvent = {},
                )
                val completed = CompletableDeferred<Unit>()
                val completionJob = manager.scope.launch {
                    manager.listOfJobsEvents
                        .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                        .take(items.size)
                        .collect()
                    completed.complete(Unit)
                }

                try {
                    manager.boot()
                    assertEquals(items.size, manager.downloadJobs.map { it.id }.distinct().size)
                    queue.boot()
                    withTimeout(5_000) {
                        manager.listOfJobsEvents.subscriptionCount.first { it >= 2 }
                    }
                    manager.restoreInterruptedRetries { queue.onRetryDue(it) }

                    delay(500)
                    assertFalse(queue.isQueueActive)
                    assertTrue(manager.downloadJobs.none { it.isDownloadActive.value })
                    items.forEach { item ->
                        assertEquals(0, fixture.requestsFor("/file/${item.id}.bin"))
                    }

                    withTimeout(35_000) { completed.await() }

                    assertTrue(queue.isQueueActive)
                    val perFilePeaks = mutableListOf<Int>()
                    items.forEach { item ->
                        assertContentEquals(
                            fixture.fileBytes(item.id),
                            File(item.folder, item.name).readBytes(),
                        )
                        assertTrue(fixture.partRequestsFor("/file/${item.id}.bin") >= 2)
                        perFilePeaks += fixture.peakPartTransfersFor("/file/${item.id}.bin")
                    }
                    assertTrue(perFilePeaks.all { it in 1..2 })
                    assertEquals(2, perFilePeaks.max())
                    assertEquals(3, fixture.peakPartTransfers())
                } finally {
                    completionJob.cancel()
                    queue.dispose()
                    closeManager(manager)
                    output.deleteRecursively()
                    data.deleteRecursively()
                }
            }
        }

    @Test
    fun futureHttpRetryWaitsWithoutOwningAnActiveSlot() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("future-http-output-")
            val data = createTempDir("future-http-data-")
            val item = HttpDownloadItem(
                link = "${fixture.baseUrl}/file/31.bin",
                id = 31,
                folder = output.path,
                name = "31.bin",
                dateAdded = 31,
                retryAttempt = 1,
                retryAt = System.currentTimeMillis() + 1_500,
                preferredConnectionCount = 1,
            )
            val manager = createHttpManager(listOf(item), fixture.client, data)
            val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = ManualDownloadQueue(manager, queueScope)
            val completed = CompletableDeferred<Unit>()
            val completionJob = queueScope.launch {
                manager.listOfJobsEvents
                    .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                    .first()
                completed.complete(Unit)
            }

            try {
                manager.boot()
                queue.boot()
                withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 2 } }
                queue.setMaxConcurrent(1)
                manager.restoreInterruptedRetries { queue.resume(it) }

                delay(200)
                assertEquals(0, fixture.requestsFor("/file/31.bin"))
                assertFalse(manager.downloadJobs.single().isDownloadActive.value)
                assertTrue(manager.downloadJobs.single().status.value is DownloadJobStatus.Retrying)

                withTimeout(20_000) { completed.await() }
                assertContentEquals(fixture.fileBytes(item.id), File(item.folder, item.name).readBytes())
                assertNull(item.retryAt)
            } finally {
                completionJob.cancel()
                queue.clearQueue()
                queueScope.cancel()
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun zeroTimestampHlsRetryIsAdmittedOnlyAfterListenersAreReady() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("immediate-hls-output-")
            val data = createTempDir("immediate-hls-data-")
            val item = HLSDownloadItem(
                link = "${fixture.baseUrl}/playlist/32.m3u8",
                id = 32,
                folder = output.path,
                name = "32.ts",
                dateAdded = 32,
                retryAttempt = 1,
                retryAt = 0,
                preferredConnectionCount = 1,
            )
            val manager = createHlsManager(listOf(item), fixture.client, data)

            try {
                runRestoredJobsWithLimitOne(manager, 1)
                assertContentEquals(fixture.segmentBytes(item.id), File(item.folder, item.name).readBytes())
                assertNull(item.retryAt)
                assertEquals(0, item.retryAttempt)
                assertTrue(fixture.requestsFor("/playlist/32.m3u8") >= 1)
                assertTrue(fixture.requestsFor("/segment/32.ts") >= 1)
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun restoredHttpJobsCompleteThroughManualQueueOneAtATime() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("restored-http-output-")
            val data = createTempDir("restored-http-data-")
            val items = (1L..5L).map { id ->
                HttpDownloadItem(
                    link = "${fixture.baseUrl}/file/$id.bin",
                    id = id,
                    folder = output.path,
                    name = "$id.bin",
                    dateAdded = id,
                    retryAttempt = 1,
                    retryAt = System.currentTimeMillis() - 1,
                    preferredConnectionCount = 1,
                )
            }
            val manager = createHttpManager(items, fixture.client, data)

            try {
                runRestoredJobsWithLimitOne(manager, items.size)

                items.forEach { item ->
                    assertContentEquals(
                        fixture.fileBytes(item.id),
                        File(item.folder, item.name).readBytes(),
                    )
                    assertNull(item.retryAt)
                    assertEquals(0, item.retryAttempt)
                    assertTrue(fixture.requestsFor("/file/${item.id}.bin") >= 2)
                }
                assertEquals(1, fixture.peakTransfers.get())
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    @Test
    fun restoredHlsJobsCompleteThroughManualQueueOneAtATime() = withRetryServer { fixture ->
        runBlocking {
            val output = createTempDir("restored-hls-output-")
            val data = createTempDir("restored-hls-data-")
            val items = (11L..15L).map { id ->
                HLSDownloadItem(
                    link = "${fixture.baseUrl}/playlist/$id.m3u8",
                    id = id,
                    folder = output.path,
                    name = "$id.ts",
                    dateAdded = id,
                    retryAttempt = 1,
                    retryAt = System.currentTimeMillis() - 1,
                    preferredConnectionCount = 1,
                )
            }
            val manager = createHlsManager(items, fixture.client, data)

            try {
                runRestoredJobsWithLimitOne(manager, items.size)

                items.forEach { item ->
                    assertContentEquals(
                        fixture.segmentBytes(item.id),
                        File(item.folder, item.name).readBytes(),
                    )
                    assertNull(item.retryAt)
                    assertEquals(0, item.retryAttempt)
                    assertTrue(fixture.requestsFor("/playlist/${item.id}.m3u8") >= 1)
                    assertTrue(fixture.requestsFor("/segment/${item.id}.ts") >= 1)
                }
                assertEquals(1, fixture.peakTransfers.get())
            } finally {
                closeManager(manager)
                output.deleteRecursively()
                data.deleteRecursively()
            }
        }
    }

    private suspend fun runRestoredJobsWithLimitOne(
        manager: DownloadManager,
        expectedCount: Int,
    ) {
        val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ManualDownloadQueue(manager, queueScope)
        val completed = CompletableDeferred<Unit>()
        val completionJob = queueScope.launch {
            manager.listOfJobsEvents
                .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                .take(expectedCount)
                .collect()
            completed.complete(Unit)
        }
        try {
            manager.boot()
            assertEquals(expectedCount, manager.downloadJobs.size)
            assertEquals(expectedCount, manager.downloadJobs.map { it.id }.distinct().size)
            queue.boot()
            withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 2 } }
            queue.setMaxConcurrent(1)
            manager.restoreInterruptedRetries { queue.resume(it) }
            withTimeout(20_000) { completed.await() }
        } finally {
            completionJob.cancel()
            queue.clearQueue()
            queueScope.cancel()
        }
    }

    private suspend fun runFutureRetryStoppedBeforeAdmission(
        manager: DownloadManager,
        item: ir.amirab.downloader.downloaditem.IDownloadItem,
    ) {
        val dueAt = requireNotNull(item.retryAt)
        val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ManualDownloadQueue(manager, queueScope)
        try {
            manager.boot()
            val job = manager.downloadJobs.single()
            queue.boot()
            withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 1 } }
            queue.setMaxConcurrent(1)
            manager.restoreInterruptedRetries { queue.resume(it) }
            delay(100)

            assertTrue(job.status.value is DownloadJobStatus.Retrying)
            assertFalse(job.isDownloadActive.value)
            withTimeout(10_000) { manager.stopAll() }
            delay((dueAt - System.currentTimeMillis() + 300).coerceAtLeast(300))

            assertTrue(job.status.value is DownloadJobStatus.Canceled)
            assertFalse(job.isDownloadActive.value)
            assertNull(item.retryAt)
            assertEquals(0, item.retryAttempt)
        } finally {
            queue.clearQueue()
            queueScope.cancel()
        }
    }

    private suspend fun runRestoredJobOwnedByStoppedQueue(
        manager: DownloadManager,
        itemId: Long,
        assertBeforeStart: () -> Unit,
    ) {
        val persisted = InMemoryQueueData(
            QueueModel(
                id = 77,
                name = "Stopped restored retry",
                maxConcurrent = 1,
                queueItems = listOf(itemId),
            )
        )
        val queue = DownloadQueue(
            persistedModel = persisted.model,
            persistedData = persisted,
            downloadEvents = manager,
            onQueueEvent = {},
        )
        val completed = CompletableDeferred<Unit>()
        val completionJob = manager.scope.launch {
            manager.listOfJobsEvents
                .filterIsInstance<DownloadManagerEvents.OnJobCompleted>()
                .first()
            completed.complete(Unit)
        }
        try {
            manager.boot()
            queue.boot()
            withTimeout(5_000) { manager.listOfJobsEvents.subscriptionCount.first { it >= 2 } }
            manager.restoreInterruptedRetries { queue.onRetryDue(it) }
            delay(200)
            assertBeforeStart()
            assertFalse(queue.isQueueActive)
            assertFalse(manager.downloadJobs.single().isDownloadActive.value)

            queue.start()
            withTimeout(20_000) { completed.await() }
        } finally {
            completionJob.cancel()
            queue.dispose()
        }
    }

    private suspend fun createHttpManager(
        items: List<HttpDownloadItem>,
        client: OkHttpHttpDownloaderClient,
        dataFolder: File,
        listDb: MemoryDownloadListDB? = null,
        partDb: MemoryDownloadPartStatesDB? = null,
        emptyFileCreator: EmptyFileCreator? = null,
    ): DownloadManager = createManager(
        items = items,
        dataFolder = dataFolder,
        registry = DownloaderRegistry().apply { add(HttpDownloader(lazyOf(client))) },
        listDb = listDb,
        partDb = partDb,
        emptyFileCreator = emptyFileCreator,
    )

    private suspend fun createHlsManager(
        items: List<HLSDownloadItem>,
        client: OkHttpHttpDownloaderClient,
        dataFolder: File,
    ): DownloadManager = createManager(
        items = items,
        dataFolder = dataFolder,
        registry = DownloaderRegistry().apply { add(HLSDownloader(lazyOf(client))) },
    )

    private suspend fun createManager(
        items: List<ir.amirab.downloader.downloaditem.IDownloadItem>,
        dataFolder: File,
        registry: DownloaderRegistry,
        listDb: MemoryDownloadListDB? = null,
        partDb: MemoryDownloadPartStatesDB? = null,
        emptyFileCreator: EmptyFileCreator? = null,
    ): DownloadManager {
        val actualListDb = listDb ?: MemoryDownloadListDB()
        items.forEach { item ->
            if (actualListDb.getById(item.id) == null) actualListDb.add(item)
        }
        return DownloadManager(
            dlListDb = actualListDb,
            partListDb = partDb ?: MemoryDownloadPartStatesDB(),
            settings = DownloadSettings(
                defaultThreadCount = 1,
                maxDownloadRetryCount = 2,
            ),
            emptyFileCreator = emptyFileCreator ?: EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = registry,
            downloadDataFolder = dataFolder,
        )
    }

    private fun closeManager(manager: DownloadManager) {
        manager.downloadJobs.forEach(DownloadJob::close)
        manager.scope.cancel()
    }

    private fun createTempDir(prefix: String): File =
        java.nio.file.Files.createTempDirectory(prefix).toFile()

    private fun <T> withRetryServer(block: (RetryServerFixture) -> T): T {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val fixture = RetryServerFixture(server)
        server.executor = executor
        server.createContext("/") { exchange -> fixture.respond(exchange) }
        server.start()
        return try {
            block(fixture)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private class RetryServerFixture(
        private val server: HttpServer,
    ) {
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
        val peakTransfers = AtomicInteger()
        private val activeTransfers = AtomicInteger()
        private val requests = ConcurrentHashMap<String, AtomicInteger>()
        private val partRequests = ConcurrentHashMap<String, AtomicInteger>()
        private val requestTimes = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()
        private val activePartTransfers = AtomicInteger()
        private val peakPartTransferCount = AtomicInteger()
        private val activePartTransfersByPath = ConcurrentHashMap<String, AtomicInteger>()
        private val peakPartTransfersByPath = ConcurrentHashMap<String, AtomicInteger>()
        private val blockedInfoEntered = CountDownLatch(1)
        private val blockedInfoRelease = CountDownLatch(1)

        val client = OkHttpHttpDownloaderClient(
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build(),
            customUserAgentProvider = object : UserAgentProvider {
                override fun getUserAgent() = "restored-retry-test"
            },
            proxyStrategyProvider = object : ProxyStrategyProvider {
                override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
            },
            systemProxySelectorProvider = object : SystemProxySelectorProvider {
                override fun getSystemProxySelector(): ProxySelector? = null
            },
            autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
        )

        fun requestsFor(path: String): Int = requests[path]?.get() ?: 0

        fun partRequestsFor(path: String): Int = partRequests[path]?.get() ?: 0

        fun peakPartTransfers(): Int = peakPartTransferCount.get()

        fun peakPartTransfersFor(path: String): Int = peakPartTransfersByPath[path]?.get() ?: 0

        fun requestTimesFor(path: String): List<Long> = requestTimes[path]?.toList().orEmpty()

        fun activeTransferCount(): Int = activeTransfers.get()

        fun awaitBlockedInfoRequest(): Boolean = blockedInfoEntered.await(10, TimeUnit.SECONDS)

        fun releaseBlockedInfoRequest() {
            blockedInfoRelease.countDown()
        }

        fun fileBytes(id: Long): ByteArray = ByteArray(
            when (id) {
                71L, 72L -> 2 * 1024 * 1024
                41L, 61L, 91L, 92L, 101L, 102L, 103L, 104L -> 512 * 1024
                else -> 4_096
            }
        ) { index ->
            ((index * 31 + id.toInt()) and 0xff).toByte()
        }

        fun segmentBytes(id: Long): ByteArray = ByteArray(
            if (id == 42L || id == 52L || id == 62L) 512 * 1024 else 2_048
        ) { index ->
            ((index * 17 + id.toInt()) and 0xff).toByte()
        }

        fun respond(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val requestNumber = requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            requestTimes.computeIfAbsent(path) { CopyOnWriteArrayList() }.add(System.nanoTime())
            val range = exchange.requestHeaders.getFirst("Range")
            val isPartRequest = path.startsWith("/file/") && range != null && range != "bytes=0-255"
            if (isPartRequest) {
                partRequests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            }
            if (path == "/file/101.bin" && range == "bytes=0-255") {
                blockedInfoEntered.countDown()
                check(blockedInfoRelease.await(10, TimeUnit.SECONDS)) {
                    "timed out waiting to release blocked metadata response"
                }
            }
            when {
                path == "/file/81.bin" && isPartRequest -> {
                    respondFailure(exchange, 503, "0")
                    return
                }
                path == "/file/86.bin" && isPartRequest -> {
                    when (partRequestsFor(path)) {
                        1 -> respondFailure(exchange, 503, "0")
                        2 -> respondTruncatedRange(exchange, fileBytes(86))
                        else -> respondFailure(exchange, 503, "0")
                    }
                    return
                }
                path == "/file/82.bin" && requestNumber == 1 -> {
                    respondFailure(exchange, 429, "1")
                    return
                }
                path == "/file/83.bin" && requestNumber == 1 -> {
                    respondFailure(exchange, 503, "3600")
                    return
                }
                path == "/file/84.bin" -> {
                    respondFailure(exchange, 404)
                    return
                }
                path == "/segment/85.ts" && requestNumber == 1 -> {
                    respondFailure(exchange, 503, "1")
                    return
                }
            }
            when {
                path.startsWith("/file/") -> respondRanged(exchange, fileBytes(idFrom(path)))
                path.startsWith("/playlist/") -> respondPlaylist(exchange, idFrom(path))
                path.startsWith("/segment/") -> respondRanged(
                    exchange,
                    segmentBytes(idFrom(path)),
                    "video/mp2t",
                )
                else -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
            }
        }

        private fun respondFailure(
            exchange: HttpExchange,
            status: Int,
            retryAfter: String? = null,
        ) {
            retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }

        private fun respondRanged(
            exchange: HttpExchange,
            bytes: ByteArray,
            contentType: String = "application/octet-stream",
        ) {
            val range = exchange.requestHeaders.getFirst("Range")
            val match = range?.let(RANGE_PATTERN::matchEntire)
            val start = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val requestedEnd = match?.groupValues?.get(2)?.toIntOrNull() ?: bytes.lastIndex
            val end = requestedEnd.coerceAtMost(bytes.lastIndex)
            val body = bytes.copyOfRange(start, end + 1)
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("ETag", "\"retry-fixture\"")
            if (range != null) {
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
                respondBytes(exchange, body, contentType, 206)
            } else {
                respondBytes(exchange, body, contentType, 200)
            }
        }

        private fun respondTruncatedRange(
            exchange: HttpExchange,
            bytes: ByteArray,
        ) {
            val range = requireNotNull(exchange.requestHeaders.getFirst("Range"))
            val match = requireNotNull(RANGE_PATTERN.matchEntire(range))
            val start = match.groupValues[1].toInt()
            val requestedEnd = match.groupValues[2].toIntOrNull() ?: bytes.lastIndex
            val end = requestedEnd.coerceAtMost(bytes.lastIndex)
            val body = bytes.copyOfRange(start, end + 1)
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("ETag", "\"retry-fixture\"")
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(206, body.size.toLong())
            exchange.responseBody.use { output ->
                output.write(body, 0, body.size / 2)
                output.flush()
            }
            exchange.close()
        }

        private fun respondPlaylist(exchange: HttpExchange, id: Long) {
            val body = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:1
                #EXT-X-MEDIA-SEQUENCE:0
                #EXTINF:1.0,
                ../segment/$id.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            respondBytes(exchange, body, "application/vnd.apple.mpegurl")
        }

        private fun respondBytes(
            exchange: HttpExchange,
            bytes: ByteArray,
            contentType: String,
            status: Int = 200,
        ) {
            val active = activeTransfers.incrementAndGet()
            peakTransfers.updateAndGet { previous -> maxOf(previous, active) }
            val path = exchange.requestURI.path
            val range = exchange.requestHeaders.getFirst("Range")
            val isFilePartTransfer = path.startsWith("/file/") &&
                range != null && range != "bytes=0-255"
            val activeForPath = if (isFilePartTransfer) {
                val activeParts = activePartTransfers.incrementAndGet()
                peakPartTransferCount.updateAndGet { previous -> maxOf(previous, activeParts) }
                activePartTransfersByPath
                    .computeIfAbsent(path) { AtomicInteger() }
                    .incrementAndGet()
                    .also { pathActive ->
                        peakPartTransfersByPath
                            .computeIfAbsent(path) { AtomicInteger() }
                            .updateAndGet { previous -> maxOf(previous, pathActive) }
                    }
            } else {
                null
            }
            try {
                exchange.responseHeaders.add("Content-Type", contentType)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { output ->
                    // Real 2 KiB flushes can trigger Windows TCP delayed-ACK
                    // on every write (~40-80 ms), pushing 256 tiny HLS chunks
                    // beyond the fixed 20 s lifecycle test even while receiving.
                    // Still stream in many chunks with the original 2 ms pacing;
                    // avoid Byte boxing and use larger 8 KiB HLS frames.
                    val chunkSize = when {
                        path.startsWith("/segment/") && bytes.size >= 512 * 1024 -> 8 * 1024
                        bytes.size >= 512 * 1024 -> 2_048
                        else -> 512
                    }
                    var offset = 0
                    while (offset < bytes.size) {
                        val length = minOf(chunkSize, bytes.size - offset)
                        output.write(bytes, offset, length)
                        output.flush()
                        Thread.sleep(2)
                        offset += length
                    }
                }
            } finally {
                if (activeForPath != null) {
                    activePartTransfers.decrementAndGet()
                    activePartTransfersByPath.getValue(path).decrementAndGet()
                }
                activeTransfers.decrementAndGet()
                exchange.close()
            }
        }

        private fun idFrom(path: String): Long =
            requireNotNull(ID_PATTERN.find(path)).groupValues[1].toLong()
    }

    private class InMemoryQueueData(
        var model: QueueModel,
    ) : DownloadQueuePersistedDataAccess {
        override suspend fun getModel(): QueueModel = model

        override suspend fun setModel(model: QueueModel) {
            this.model = model
        }
    }

    private class BlockingSparseFile : ISparseFile {
        private val createEntered = CountDownLatch(1)
        private val createRelease = CountDownLatch(1)

        override fun canWeCreateSparseFile(file: File): Boolean = true

        override fun createSparseFile(file: File): Boolean {
            createEntered.countDown()
            check(createRelease.await(10, TimeUnit.SECONDS)) {
                "timed out waiting to release sparse file creation"
            }
            return file.createNewFile() || file.exists()
        }

        fun awaitCreate(): Boolean = createEntered.await(10, TimeUnit.SECONDS)

        fun releaseCreate() {
            createRelease.countDown()
        }
    }

    private companion object {
        val RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d*)")
        val ID_PATTERN = Regex("/(\\d+)\\.")
    }
}
