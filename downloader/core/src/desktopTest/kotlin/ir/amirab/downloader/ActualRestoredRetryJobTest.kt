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
import ir.amirab.downloader.downloaditem.hls.HLSDownloadItem
import ir.amirab.downloader.downloaditem.hls.HLSDownloader
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.queue.ManualDownloadQueue
import ir.amirab.downloader.queue.DownloadQueue
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
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
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap
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
                    while (job.status.value != DownloadJobStatus.Downloading || fixture.activeTransferCount() == 0) {
                        delay(5)
                    }
                }

                manager.stopAll()
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                val requestsAfterStop = fixture.requestsFor("/file/61.bin")
                delay(250)

                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
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
                    while (job.status.value != DownloadJobStatus.Downloading || fixture.activeTransferCount() == 0) {
                        delay(5)
                    }
                }

                manager.stopAll()
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                val requestsAfterStop = fixture.requestsFor("/playlist/62.m3u8") +
                    fixture.requestsFor("/segment/62.ts")
                delay(250)

                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
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
                withTimeout(20_000) { completed.await() }

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
                    while (job.status.value != DownloadJobStatus.Downloading || fixture.activeTransferCount() == 0) {
                        delay(5)
                    }
                }

                val oldFile = File(oldOutput, "before.bin")
                val newFile = File(newOutput, "after.bin")
                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.folder = newOutput.path
                    updated.name = newFile.name
                }

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
                withTimeout(20_000) { completed.await() }
                assertContentEquals(fixture.fileBytes(item.id), newFile.readBytes())
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
                    while (job.status.value != DownloadJobStatus.Downloading || fixture.activeTransferCount() == 0) {
                        delay(5)
                    }
                }

                val oldFile = File(oldOutput, "before.ts")
                val newFile = File(newOutput, "after.ts")
                manager.updateDownloadItem(item.id, null) { updated ->
                    updated.folder = newOutput.path
                    updated.name = newFile.name
                }

                assertFalse(job.isDownloadActive.value)
                assertTrue(job.status.value is DownloadJobStatus.Canceled)
                withTimeout(5_000) {
                    while (fixture.activeTransferCount() != 0) delay(5)
                }
                assertFalse(oldFile.exists())
                delay(150)
                assertFalse(oldFile.exists())

                manager.resume(item.id)
                withTimeout(20_000) { completed.await() }
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
            manager.stopAll()
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
    ): DownloadManager = createManager(
        items = items,
        dataFolder = dataFolder,
        registry = DownloaderRegistry().apply { add(HttpDownloader(lazyOf(client))) },
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
    ): DownloadManager {
        val listDb = MemoryDownloadListDB().also { db -> items.forEach { db.add(it) } }
        return DownloadManager(
            dlListDb = listDb,
            partListDb = MemoryDownloadPartStatesDB(),
            settings = DownloadSettings(defaultThreadCount = 1),
            emptyFileCreator = EmptyFileCreator(
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

        fun activeTransferCount(): Int = activeTransfers.get()

        fun fileBytes(id: Long): ByteArray = ByteArray(
            if (id == 41L || id == 61L) 512 * 1024 else 4_096
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
            requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            when {
                path.startsWith("/file/") -> respondRanged(exchange, fileBytes(idFrom(path)))
                path.startsWith("/playlist/") -> respondPlaylist(exchange, idFrom(path))
                path.startsWith("/segment/") -> respondBytes(exchange, segmentBytes(idFrom(path)), "video/mp2t")
                else -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
            }
        }

        private fun respondRanged(exchange: HttpExchange, bytes: ByteArray) {
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
                respondBytes(exchange, body, "application/octet-stream", 206)
            } else {
                respondBytes(exchange, body, "application/octet-stream", 200)
            }
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
            try {
                exchange.responseHeaders.add("Content-Type", contentType)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { output ->
                    bytes.asList().chunked(512).forEach { chunk ->
                        output.write(chunk.toByteArray())
                        output.flush()
                        Thread.sleep(2)
                    }
                }
            } finally {
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

    private companion object {
        val RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d*)")
        val ID_PATTERN = Regex("/(\\d+)\\.")
    }
}
