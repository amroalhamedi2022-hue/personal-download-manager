package ir.amirab.downloader.part

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.destination.DestWriter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.Buffer
import okio.FileSystem
import okio.Source
import okio.Timeout
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartDownloaderIoLifecycleTest {
    private class CloseUnblocksSource : Source {
        private val closeSignal = CountDownLatch(1)
        val readEntered = CountDownLatch(1)

        @Volatile
        var closed = false
            private set

        override fun read(sink: Buffer, byteCount: Long): Long {
            readEntered.countDown()
            while (!closed) {
                try {
                    closeSignal.await()
                } catch (_: InterruptedException) {
                    // Intentionally ignore interruption: closing the connection must unblock
                    // a blocking Source even when the implementation does not honor interrupts.
                }
            }
            throw IOException("test source closed")
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
            closeSignal.countDown()
        }
    }

    private object TestResponseInfo : IResponseInfo {
        override val isSuccessFul = true
        override val requiresAuth = false
        override val requireBasicAuth = false
        override val resumeSupport = true
        override val isWebPage = false
        override val unsuccessFullException: Throwable? = null
    }

    private class TestPart : DownloadPart {
        override var current: Long = 0L
        override val statusFlow = MutableStateFlow<PartDownloadStatus>(PartDownloadStatus.IDLE)
        override val isCompleted: Boolean get() = false
        override val percent: Int? get() = null
        override fun howMuchProceed(): Long = current
        override fun resetCurrent() { current = 0L }
        override fun getID(): Long = 1L
    }

    private class TestDownloader(
        part: TestPart,
        private val connection: Connection<TestResponseInfo>,
        writer: DestWriter,
    ) : PartDownloader<TestPart>(part, { writer }) {
        override fun howMuchCanRead(maxAllowed: Long): Long = maxAllowed
        override suspend fun connectAndVerify(): Connection<TestResponseInfo> = connection
    }

    @Test
    fun stopRequestedBeforeCoroutineDispatchPreventsAnyDownloadAttempt() {
        assertTrue(shouldContinuePartWorker(coroutineActive = true, stopRequested = false))
        assertFalse(shouldContinuePartWorker(coroutineActive = true, stopRequested = true))
        assertFalse(shouldContinuePartWorker(coroutineActive = false, stopRequested = false))
        assertFalse(shouldContinuePartWorker(coroutineActive = false, stopRequested = true))
    }

    @Test
    fun sharedBodyCopyPermitNeverExceedsConfiguredParallelism() = runBlocking {
        assertEquals(60, PART_BODY_COPY_PARALLELISM)
        val taskCount = PART_BODY_COPY_PARALLELISM * 2
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val ready = CountDownLatch(taskCount)
        val entered = CountDownLatch(PART_BODY_COPY_PARALLELISM)
        val overflowEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val jobs = List(taskCount) {
                launch(Dispatchers.Default) {
                    ready.countDown()
                    withPartBodyCopyPermit {
                        val now = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, now) }
                        if (now > PART_BODY_COPY_PARALLELISM) {
                            overflowEntered.complete(Unit)
                        }
                        entered.countDown()
                        try {
                            release.await()
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            try {
                assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "all body-copy contenders should reach the permit gate",
                )
                assertTrue(
                    entered.await(5, TimeUnit.SECONDS),
                    "the shared permit pool should admit its full configured capacity",
                )
                assertEquals(
                    PART_BODY_COPY_PARALLELISM,
                    active.get(),
                    "no more than the configured number of body copies may hold permits",
                )
                val overflowWasAdmitted = withTimeoutOrNull(250L) {
                    overflowEntered.await()
                    true
                } ?: false
                assertFalse(
                    overflowWasAdmitted,
                    "the 61st body copy must remain suspended while all 60 permits are held",
                )
            } finally {
                release.complete(Unit)
            }
            jobs.joinAll()
        }

        assertEquals(0, active.get())
        assertEquals(PART_BODY_COPY_PARALLELISM, peak.get())
    }


    @Test
    fun primaryRangesCannotBeStarvedByEarlierMultiPartDownloads() = runBlocking {
        assertEquals(16, PART_PRIMARY_COPY_RESERVE)
        val secondaryCapacity = PART_BODY_COPY_PARALLELISM - PART_PRIMARY_COPY_RESERVE
        val secondaryEntered = CountDownLatch(secondaryCapacity)
        val primaryEntered = CountDownLatch(PART_PRIMARY_COPY_RESERVE)
        val extraSecondaryEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val secondaries = List(secondaryCapacity) {
                launch(Dispatchers.Default) {
                    withFairPartBodyCopyPermit(isPrimaryPart = false) {
                        secondaryEntered.countDown()
                        release.await()
                    }
                }
            }
            val primaries = mutableListOf<kotlinx.coroutines.Job>()
            var extraSecondary: kotlinx.coroutines.Job? = null
            try {
                assertTrue(secondaryEntered.await(5, TimeUnit.SECONDS))
                repeat(PART_PRIMARY_COPY_RESERVE) {
                    primaries += launch(Dispatchers.Default) {
                        withFairPartBodyCopyPermit(isPrimaryPart = true) {
                            primaryEntered.countDown()
                            release.await()
                        }
                    }
                }
                assertTrue(
                    primaryEntered.await(5, TimeUnit.SECONDS),
                    "all 16 first ranges must start despite 44 occupied secondary slots",
                )
                extraSecondary = launch(Dispatchers.Default) {
                    withFairPartBodyCopyPermit(isPrimaryPart = false) {
                        extraSecondaryEntered.complete(Unit)
                    }
                }
                assertFalse(
                    withTimeoutOrNull(250L) { extraSecondaryEntered.await(); true } ?: false,
                    "a 45th secondary must not consume a first-range reservation",
                )
            } finally {
                release.complete(Unit)
            }
            (secondaries + primaries).joinAll()
            extraSecondary?.join()
        }
    }

    @Test
    fun connectionAdmissionWaitsForSharedPermit() = runBlocking {
        val releaseHolders = CompletableDeferred<Unit>()
        val allHoldersEntered = CountDownLatch(PART_BODY_COPY_PARALLELISM)
        val connected = CompletableDeferred<Unit>()
        val connectionCalls = AtomicInteger(0)
        val holders = List(PART_BODY_COPY_PARALLELISM) {
            launch(Dispatchers.Default) {
                withPartBodyCopyPermit {
                    allHoldersEntered.countDown()
                    releaseHolders.await()
                }
            }
        }
        val downloader = object : PartDownloader<TestPart>(
            TestPart(),
            { error("no destination writer should open in this test") },
        ) {
            override fun howMuchCanRead(maxAllowed: Long): Long = maxAllowed
            override suspend fun connectAndVerify(): Connection<*> {
                connectionCalls.incrementAndGet()
                connected.complete(Unit)
                awaitCancellation()
            }
        }
        downloader.onTooManyErrors = { throw it }

        try {
            assertTrue(allHoldersEntered.await(5, TimeUnit.SECONDS))
            downloader.start()
            withTimeout(2_000L) {
                downloader.statusFlow.first { it == PartDownloadStatus.Connecting }
            }
            assertFalse(
                withTimeoutOrNull(300L) { connected.await(); true } ?: false,
                "a waiting part must not open an HTTP socket before a copy slot is free",
            )
            assertEquals(0, connectionCalls.get())

            releaseHolders.complete(Unit)
            holders.joinAll()
            withTimeout(2_000L) { connected.await() }
            assertEquals(1, connectionCalls.get(), "admission must resume when a slot is released")
        } finally {
            releaseHolders.complete(Unit)
            holders.forEach { it.cancel() }
            holders.joinAll()
            downloader.stop()
            withTimeout(2_000L) { downloader.join() }
        }
    }

    @Test
    fun bodyCopyPermitIsReleasedAfterCopyFailure() = runBlocking {
        val failed = runCatching {
            withPartBodyCopyPermit<Unit> {
                throw IOException("synthetic body-copy failure")
            }
        }
        assertTrue(failed.isFailure, "the synthetic copy failure should propagate")
        assertTrue(
            withTimeout(1_000L) { withPartBodyCopyPermit { true } },
            "a failed copy must not leak its shared permit",
        )
    }

    @Test
    fun bodyCopyPermitIsReleasedAfterCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val holder = launch {
            withPartBodyCopyPermit {
                entered.complete(Unit)
                awaitCancellation()
            }
        }

        try {
            withTimeout(1_000L) { entered.await() }
        } finally {
            holder.cancelAndJoin()
        }

        assertTrue(
            withTimeout(1_000L) { withPartBodyCopyPermit { true } },
            "a cancelled body copy must release its shared permit",
        )
    }

    @Test
    fun connectionCloseCancelsTransportAndClosesSource() {
        val source = CloseUnblocksSource()
        var cancelRequested = false
        val connection = Connection(
            source = source,
            contentLength = 1024L,
            responseInfo = TestResponseInfo,
            cancelTransport = { cancelRequested = true },
        )

        connection.close()

        assertTrue(cancelRequested, "closing a response must cancel its underlying transport call")
        assertTrue(source.closed, "closing a response must also close its Okio source")
    }

    @Test
    fun stopClosesBlockingSourceAndJoinsWorker() = runBlocking {
        val file = Files.createTempFile("hyperget-part-io-", ".bin").toFile()
        val fileHandle = FileSystem.SYSTEM.openReadWrite(file.toOkioPath())
        val source = CloseUnblocksSource()
        val connection = Connection(source, 1024L, TestResponseInfo)
        val downloader = TestDownloader(
            part = TestPart(),
            connection = connection,
            writer = DestWriter(1L, file, 0L, fileHandle),
        )
        downloader.onTooManyErrors = { throw it }

        try {
            downloader.start()
            assertTrue(
                source.readEntered.await(2, TimeUnit.SECONDS),
                "the downloader should enter the blocking response-body read",
            )

            downloader.stop()
            withTimeout(2_000L) { downloader.join() }

            assertTrue(source.closed, "stop must close the active response source")
            assertFalse(downloader.active, "the part worker must finish after its source is closed")
        } finally {
            downloader.stop()
            fileHandle.close()
            file.delete()
        }
    }
}
