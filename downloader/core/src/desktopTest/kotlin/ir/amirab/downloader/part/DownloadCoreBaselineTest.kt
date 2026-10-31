package ir.amirab.downloader.part

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.DownloadManagerEvents
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.shouldDeleteDownloadOutput
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.connection.response.expectSuccess
import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.db.PartListFileStorage
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.persistProgressAfterFlush
import ir.amirab.downloader.downloaditem.http.validateRemoteFileIdentity
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.DownloadItemContext
import ir.amirab.downloader.downloaditem.calculateRetryDelayMillis
import ir.amirab.downloader.downloaditem.retryAfterHeaderOrNull
import ir.amirab.downloader.exception.FileChangedException
import ir.amirab.downloader.exception.NoSpaceInStorageException
import ir.amirab.downloader.exception.UnSuccessfulResponseException
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.FileNameUtil
import ir.amirab.downloader.utils.IDiskStat
import ir.amirab.downloader.utils.ISparseFile
import ir.amirab.downloader.utils.splitToRange
import ir.amirab.downloader.utils.speedlimiter.OkioCustomizedThrottler
import ir.amirab.downloader.utils.hostlimits.HostLimitRegistry
import ir.amirab.downloader.utils.hostlimits.HostLimits
import ir.amirab.downloader.queue.ManualDownloadQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DownloadCoreBaselineTest {
    @Test
    fun emptyFileCreationRejectsInsufficientSpaceBeforeMutation() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("download-no-space-").toFile()
        val target = folder.resolve("existing.bin")
        val original = byteArrayOf(1, 2, 3, 4)
        target.writeBytes(original)
        val creator = EmptyFileCreator(
            diskStat = object : IDiskStat {
                override fun getRemainingSpace(path: File): Long = 5
            },
            useSparseFile = { false },
        )

        try {
            val failure = runCatching {
                creator.prepareFile(target, length = 10) {}
            }.exceptionOrNull()

            assertTrue(failure is NoSpaceInStorageException)
            assertEquals(5L, failure.available)
            assertEquals(6L, failure.required)
            assertContentEquals(original, target.readBytes())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun zeroLengthDestinationNeverSeeksBeforeFileStart() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("download-zero-length-").toFile()
        val target = folder.resolve("empty.bin")
        val creator = EmptyFileCreator(
            diskStat = unlimitedDiskStat(),
            useSparseFile = { true },
            sparseFile = testSparseFile(),
        )

        try {
            creator.prepareFile(target, length = 0) {}

            assertTrue(target.exists())
            assertEquals(0L, target.length())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun conflictingArabicNamesAreNumberedWithoutLosingExtension() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("download-arabic-name-").toFile()
        val original = folder.resolve("ملف التحميل.zip")

        try {
            original.writeBytes(byteArrayOf(1))
            val firstCandidate = FileNameUtil.numberedIfExists(original).first()
            assertEquals("ملف التحميل_1.zip", firstCandidate.name)

            firstCandidate.writeBytes(byteArrayOf(2))
            val secondCandidate = FileNameUtil.numberedIfExists(original).first()
            assertEquals("ملف التحميل_2.zip", secondCandidate.name)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun longArabicDestinationPathCanBePrepared() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("download-long-path-").toFile()
        val nested = (1..12).fold(root) { parent, index ->
            parent.resolve("segment_${index}_12345678901234567890")
        }
        val target = nested.resolve("ملف_تجريبي_نهائي.bin")
        val creator = EmptyFileCreator(
            diskStat = unlimitedDiskStat(),
            useSparseFile = { false },
        )

        try {
            assertTrue(target.absolutePath.length > 300)
            DownloadDestination.prepareDestinationFolder(target)
            creator.prepareFile(target, length = 0) {}

            assertTrue(target.exists())
            assertEquals(0L, target.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sparseDestinationSupportsLengthsBeyondFourGigabytes() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("download-large-sparse-").toFile()
        val target = folder.resolve("large.bin")
        val expectedLength = 5L * 1024 * 1024 * 1024 + 17
        val creator = EmptyFileCreator(
            diskStat = unlimitedDiskStat(),
            useSparseFile = { true },
            sparseFile = testSparseFile(),
        )

        try {
            creator.prepareFile(target, expectedLength) {}

            assertEquals(expectedLength, target.length())
            RandomAccessFile(target, "r").use {
                it.seek(expectedLength - 1)
                assertEquals(0, it.read())
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun splitPreservesCoverageWithoutOverlap() {
        val original = RangedPart(from = 0, to = 999)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })

        val second = split.splitPart()

        assertEquals(0L..499L, original.range)
        assertEquals(500L..999L, second?.range)
        assertEquals(original.to?.plus(1), second?.from)
    }

    @Test
    fun splitNeverMovesBeforeTheSafeZone() {
        val original = RangedPart(from = 0, to = 999, current = 400)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })
        split.safeZone = 599

        val second = split.splitPart()

        assertEquals(799L, original.to)
        assertEquals(800L, second?.from)
        assertTrue(split.safeZone <= original.to!!)
    }

    @Test
    fun splitRejectsPartsBelowTheConfiguredMinimum() {
        val original = RangedPart(from = 0, to = 199)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 200 })

        assertFalse(split.canSplit())
        assertNull(split.splitPart())
        assertEquals(0L..199L, original.range)
    }

    @Test
    fun initialSplitNeverCreatesPartsBelowTheConfiguredMinimum() {
        val totalSize = 700L * 1024
        val minimumPartSize = 512L * 1024

        val ranges = splitToRange(
            size = totalSize,
            minPartSize = minimumPartSize,
            maxPartCount = 8,
        )

        assertEquals(listOf(0L..totalSize - 1), ranges)
    }

    @Test
    fun initialSplitHonorsConnectionCapAndKeepsExactCoverage() {
        val totalSize = 10L * 1024 * 1024
        val minimumPartSize = 512L * 1024

        val ranges = splitToRange(
            size = totalSize,
            minPartSize = minimumPartSize,
            maxPartCount = 4,
        )

        assertEquals(4, ranges.size)
        assertEquals(0L, ranges.first().first)
        assertEquals(totalSize - 1, ranges.last().last)
        assertTrue(ranges.all { range -> range.last - range.first + 1 >= minimumPartSize })
        ranges.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.last + 1, next.first)
        }
    }

    @Test
    fun oneSpeedLimiterSharesItsBudgetAcrossConnections() {
        val now = 1_000_000_000L
        val limiter = OkioCustomizedThrottler(allocatedUntil = now)
        limiter.bytesPerSecond(
            bytesPerSecond = 100,
            waitByteCount = 10,
            maxByteCount = 20,
            changeImmediately = false,
        )

        assertEquals(20, limiter.byteCountOrWaitNanos(now = now, byteCount = 20))
        assertEquals(-100_000_000, limiter.byteCountOrWaitNanos(now = now, byteCount = 20))
        assertEquals(
            10,
            limiter.byteCountOrWaitNanos(now = now + 100_000_000, byteCount = 20),
        )
    }

    @Test
    fun hostRegistryCapsConcurrentConnectionsAcrossJobs() = runBlocking {
        val registry = HostLimitRegistry()
        registry.configure(
            host = "EXAMPLE.test.",
            limits = HostLimits(maxConnections = 2),
        )
        val active = AtomicInteger()
        val peak = AtomicInteger()

        coroutineScope {
            List(6) {
                async {
                    registry.withConnectionPermit("example.TEST") {
                        val current = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, current) }
                        delay(40)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(2, peak.get())
        assertEquals(0, active.get())
        assertEquals(0, registry.activeConnections("example.test"))
    }

    @Test
    fun hostPermitIsReleasedExactlyOnceWhenConnectionSourceCloses() = runBlocking {
        val registry = HostLimitRegistry()
        registry.configure(
            host = "example.test",
            limits = HostLimits(maxConnections = 1, bytesPerSecond = 64 * 1024),
        )
        val permit = requireNotNull(
            registry.acquireForUrl("https://EXAMPLE.test/file.bin")
        )
        val source = permit.releaseWhenClosed(Buffer().writeUtf8("payload"))

        assertEquals(1, registry.activeConnections("example.test"))
        assertTrue(
            registry.speedLimiterForHost("example.test") ===
                registry.speedLimiterForUrl("https://example.test/another.bin")
        )
        source.close()
        source.close()

        assertEquals(0, registry.activeConnections("example.test"))
    }

    @Test
    fun hostRegistryAppliesWildcardRulesAndPrefersExactHostRules() = runBlocking {
        val registry = HostLimitRegistry()
        registry.replaceLimits(
            mapOf(
                "*.example.test" to HostLimits(maxConnections = 1),
                "cdn.example.test" to HostLimits(maxConnections = 2),
            )
        )

        suspend fun measurePeak(host: String): Int {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            coroutineScope {
                List(4) {
                    async {
                        registry.withConnectionPermit(host) {
                            val current = active.incrementAndGet()
                            peak.updateAndGet { previous -> maxOf(previous, current) }
                            delay(30)
                            active.decrementAndGet()
                        }
                    }
                }.awaitAll()
            }
            return peak.get()
        }

        assertEquals(1, measurePeak("api.example.test"))
        assertEquals(2, measurePeak("CDN.EXAMPLE.TEST."))
    }

    @Test
    fun replacingHostRulesReconfiguresExistingEntries() = runBlocking {
        val registry = HostLimitRegistry()
        registry.configure("*.example.test", HostLimits(maxConnections = 1))

        assertEquals(1, run {
            val first = registry.acquireForHost("api.example.test")
            try {
                registry.activeConnections("api.example.test")
            } finally {
                first.close()
            }
        })

        registry.replaceLimits(
            mapOf("*.example.test" to HostLimits(maxConnections = 3))
        )
        val permits = List(3) { registry.acquireForHost("api.example.test") }
        try {
            assertEquals(3, registry.activeConnections("api.example.test"))
        } finally {
            permits.forEach { it.close() }
        }
    }

    @Test
    fun queueAndHostLimitsComposeWithoutStarvingPendingDownloads() = runBlocking {
        val registry = HostLimitRegistry().apply {
            configure("files.example.test", HostLimits(maxConnections = 3))
        }
        val items = (1L..3L).associateWith { id ->
            HttpDownloadItem(
                link = "https://files.example.test/$id.bin",
                id = id,
                folder = ".",
                name = "$id.bin",
            )
        }
        val events = MutableSharedFlow<DownloadManagerEvents>(extraBufferCapacity = 32)
        val activeJobs = AtomicInteger()
        val peakJobs = AtomicInteger()
        val activeHostConnections = AtomicInteger()
        val peakHostConnections = AtomicInteger()
        val perJobActiveConnections = items.keys.associateWith { AtomicInteger() }
        val perJobPeakConnections = items.keys.associateWith { AtomicInteger() }
        val started = CopyOnWriteArrayList<Long>()
        val completed = CopyOnWriteArrayList<Long>()

        val controls = object : DownloadManagerMinimalControl {
            override val listOfJobsEvents: SharedFlow<DownloadManagerEvents> = events

            override suspend fun startJob(id: Long, context: DownloadItemContext) {
                val item = requireNotNull(items[id])
                started += id
                events.emit(DownloadManagerEvents.OnJobStarting(item, context))
                events.emit(DownloadManagerEvents.OnJobStarted(item, context))
                val jobCount = activeJobs.incrementAndGet()
                peakJobs.updateAndGet { previous -> maxOf(previous, jobCount) }
                try {
                    coroutineScope {
                        List(2) {
                            async {
                                registry.withConnectionPermit("files.example.test") {
                                    val hostCount = activeHostConnections.incrementAndGet()
                                    peakHostConnections.updateAndGet { previous ->
                                        maxOf(previous, hostCount)
                                    }
                                    val jobConnectionCount =
                                        requireNotNull(perJobActiveConnections[id]).incrementAndGet()
                                    requireNotNull(perJobPeakConnections[id]).updateAndGet { previous ->
                                        maxOf(previous, jobConnectionCount)
                                    }
                                    try {
                                        delay(80)
                                    } finally {
                                        requireNotNull(perJobActiveConnections[id]).decrementAndGet()
                                        activeHostConnections.decrementAndGet()
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    completed += id
                    events.emit(DownloadManagerEvents.OnJobCompleted(item, context))
                } finally {
                    activeJobs.decrementAndGet()
                }
            }

            override suspend fun stopJob(id: Long, context: DownloadItemContext) = Unit

            override fun canActivateJob(id: Long): Boolean = id !in completed
        }
        val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ManualDownloadQueue(controls, queueScope)

        try {
            queue.boot()
            withTimeout(1.seconds) {
                events.subscriptionCount.first { it > 0 }
            }
            queue.setMaxConcurrent(2)
            items.keys.forEach(queue::resume)

            withTimeout(5.seconds) {
                while (completed.size < items.size) {
                    delay(20)
                }
            }

            assertEquals(items.keys, started.toSet())
            assertEquals(items.keys, completed.toSet())
            assertEquals(items.size, started.size)
            assertEquals(2, peakJobs.get())
            assertEquals(3, peakHostConnections.get())
            assertTrue(perJobPeakConnections.values.all { it.get() in 1..2 })
            assertEquals(0, registry.activeConnections("files.example.test"))
        } finally {
            queue.clearQueue()
            queueScope.cancel()
        }
    }

    @Test
    fun partialResponseKeepsIdentityAndRangeMetadata() {
        val response = HttpResponseInfo(
            statusCode = 206,
            message = "Partial Content",
            requestUrl = "https://example.test/file.bin",
            responseHeaders = mapOf(
                "content-length" to "4",
                "content-range" to "bytes 2-5/10",
                "etag" to "\"baseline-v1\"",
                "last-modified" to "Wed, 14 Oct 2026 20:00:00 GMT",
            ),
        )

        assertTrue(response.isPartial)
        assertTrue(response.resumeSupport)
        assertEquals(10L, response.totalLength)
        assertEquals("\"baseline-v1\"", response.etag)
        assertEquals("Wed, 14 Oct 2026 20:00:00 GMT", response.lastModified)
    }

    @Test
    fun rangedConnectionReadsExactBytesAndMetadata() = withHttpFixture(
        rangeMode = RangeMode.Supported,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 2,
                end = 5,
            )
        }

        connection.use {
            assertContentEquals(byteArrayOf(2, 3, 4, 5), it.readAllBytes())
            assertEquals(206, it.responseInfo.statusCode)
            assertEquals("bytes=2-5", it.responseInfo.requestHeaders["range"])
            assertEquals(10, it.responseInfo.totalLength)
            assertEquals(TEST_ETAG, it.responseInfo.etag)
            assertEquals(TEST_LAST_MODIFIED, it.responseInfo.lastModified)
            assertTrue(it.responseInfo.resumeSupport)
        }
    }

    @Test
    fun serverIgnoringRangeDoesNotAdvertiseResumeSupport() = withHttpFixture(
        rangeMode = RangeMode.Ignored,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 2,
                end = 5,
            )
        }

        connection.use {
            assertContentEquals(TEST_BYTES, it.readAllBytes())
            assertEquals(200, it.responseInfo.statusCode)
            assertEquals(10, it.responseInfo.totalLength)
            assertFalse(it.responseInfo.resumeSupport)
        }
    }

    @Test
    fun rangeNotSatisfiableIsExposedAsResponseFailure() = withHttpFixture(
        rangeMode = RangeMode.NotSatisfiable,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 10,
                end = 20,
            )
        }

        connection.use {
            assertEquals(416, it.responseInfo.statusCode)
            val failure = assertFailsWith<UnSuccessfulResponseException> {
                it.responseInfo.expectSuccess()
            }
            assertEquals(416, failure.code)
            assertFalse(it.responseInfo.resumeSupport)
        }
    }

    @Test
    fun liveTooManyRequestsResponsePreservesRetryAfterDelay() = withHttpFixture(
        rangeMode = RangeMode.TooManyRequests,
    ) { fixture ->
        val connection = runBlocking {
            fixture.client.connect(
                credentials = HttpDownloadCredentials(fixture.url),
                start = 0,
                end = 9,
            )
        }

        connection.use {
            val failure = assertFailsWith<UnSuccessfulResponseException> {
                it.responseInfo.expectSuccess()
            }

            assertEquals(429, failure.code)
            assertEquals("7", failure.retryAfterHeader)
            assertEquals(
                7_000,
                calculateRetryDelayMillis(
                    retryIndex = 0,
                    retryAfterHeader = failure.retryAfterHeader,
                ),
            )
        }
    }

    @Test
    fun matchingRemoteIdentityAllowsResume() {
        validateRemoteFileIdentity(
            expectedLength = 10,
            expectedETag = TEST_ETAG,
            actualLength = 10,
            actualETag = TEST_ETAG,
        )
    }

    @Test
    fun changedRemoteLengthBlocksResume() {
        val failure = assertFailsWith<FileChangedException.LengthChangedException> {
            validateRemoteFileIdentity(
                expectedLength = 10,
                expectedETag = TEST_ETAG,
                actualLength = 11,
                actualETag = TEST_ETAG,
            )
        }

        assertEquals(10, failure.lastContentLength)
        assertEquals(11, failure.newContentLength)
    }

    @Test
    fun missingRemoteLengthBlocksResume() {
        val failure = assertFailsWith<FileChangedException.LengthChangedException> {
            validateRemoteFileIdentity(
                expectedLength = 10,
                expectedETag = TEST_ETAG,
                actualLength = null,
                actualETag = TEST_ETAG,
            )
        }

        assertEquals(-1, failure.newContentLength)
    }

    @Test
    fun changedRemoteETagBlocksResume() {
        val failure = assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(
                expectedLength = 10,
                expectedETag = TEST_ETAG,
                actualLength = 10,
                actualETag = "\"fixture-v2\"",
            )
        }

        assertEquals(TEST_ETAG, failure.oldETag)
        assertEquals("\"fixture-v2\"", failure.newETag)
    }

    @Test
    fun absentETagPreservesLengthBasedResumeCompatibility() {
        validateRemoteFileIdentity(
            expectedLength = 10,
            expectedETag = TEST_ETAG,
            actualLength = 10,
            actualETag = null,
        )
    }

    @Test
    fun prematurelyClosedRangedResponseMustNotFinishOrLoseResumeOffset() = runBlocking {
        val file = java.nio.file.Files.createTempFile("hyperget-early-eof-main-", ".bin").toFile()
        val handle = FileSystem.SYSTEM.openReadWrite(file.toOkioPath())
        val part = RangedPart(from = 0L, to = 255L)
        val premature = AtomicReference<Throwable?>(null)
        val completed = AtomicInteger()
        val responseInfo = object : ir.amirab.downloader.connection.IResponseInfo {
            override val isSuccessFul = true
            override val requiresAuth = false
            override val requireBasicAuth = false
            override val resumeSupport = true
            override val isWebPage = false
            override val unsuccessFullException: Throwable? = null
        }
        val downloader = object : PartDownloader<RangedPart>(
            part = part,
            getDestWriter = { DestWriter(part.getID(), file, part.current, handle) },
        ) {
            override fun howMuchCanRead(maxAllowed: Long): Long =
                minOf(maxAllowed, (part.to!! - part.current + 1L).coerceAtLeast(0L))

            override suspend fun connectAndVerify() =
                ir.amirab.downloader.connection.Connection(Buffer().write(ByteArray(64)), 64L, responseInfo)

            override fun onCanceled(e: Throwable) {
                if (e is java.io.EOFException) {
                    premature.compareAndSet(null, e)
                    stop() // Prevent another attempt from racing the recorded resume offset.
                }
                super.onCanceled(e)
            }

            override fun onFinish() {
                completed.incrementAndGet()
                super.onFinish()
            }
        }
        downloader.onTooManyErrors = { /* cancellation is expected; no terminal retry budget */ }
        try {
            downloader.start()
            withTimeout(5.seconds) {
                while (premature.get() == null) {
                    delay(10)
                }
            }
            downloader.stop()
            withTimeout(5.seconds) { downloader.join() }

            assertTrue(premature.get() is java.io.EOFException)
            assertEquals(64L, part.current, "64 valid bytes must remain resumable")
            assertFalse(part.isCompleted, "short response must never publish completion")
            assertEquals(0, completed.get(), "short response cannot signal successful completion")
            assertEquals(64L, file.length(), "only received bytes should be written")
        } finally {
            downloader.stop()
            withTimeoutOrNull(5.seconds) { downloader.join() }
            handle.close()
            file.delete()
        }
    }

    @Test
    fun threeRealHttpPartsAssembleTheExpectedFile() = withHttpFixture(
        rangeMode = RangeMode.Supported,
    ) { fixture ->
        val target = File.createTempFile("download-core-multipart-", ".bin")
        val ranges = listOf(0L..3L, 4L..6L, 7L..9L)
        val writers = mutableListOf<DestWriter>()
        val partSplitLock = Any()

        try {
            RandomAccessFile(target, "rw").use { it.setLength(TEST_BYTES.size.toLong()) }
            val downloaders = ranges.map { range ->
                val part = RangedPart(from = range.first, to = range.last)
                val writer = DestWriter(
                    id = part.getID(),
                    file = target,
                    seekPos = part.current,
                    writer = FileSystem.SYSTEM.openReadWrite(target.toOkioPath()),
                ).also(writers::add)

                HttpPartDownloader(
                    credentials = HttpDownloadCredentials(fixture.url),
                    getDestWriter = { writer },
                    part = part,
                    client = fixture.client,
                    speedLimiters = emptyList(),
                    strictMode = true,
                    partSplitLock = partSplitLock,
                    minPartProvider = MinPartSizeProvider { 1 },
                ).also { downloader ->
                    downloader.onTooManyErrors = { throw AssertionError(it) }
                }
            }

            runBlocking {
                try {
                    withTimeout(30.seconds) {
                        downloaders.forEach(HttpPartDownloader::start)
                        downloaders.map { downloader ->
                            async { downloader.awaitFinishOrError() }
                        }.awaitAll().forEach { status ->
                            assertEquals(PartDownloadStatus.Completed, status)
                        }
                        downloaders.forEach { it.join() }
                    }
                } finally {
                    downloaders.forEach(HttpPartDownloader::stop)
                    withTimeoutOrNull(5.seconds) {
                        downloaders.forEach { it.join() }
                    }
                }
            }

            val assembled = target.readBytes()
            assertContentEquals(TEST_BYTES, assembled)
            assertEquals(TEST_SHA256, assembled.sha256())
        } finally {
            writers.forEach { it.writer.close() }
            target.delete()
        }
    }

    @Test
    fun progressIsPersistedOnlyAfterDestinationFlush() = runBlocking {
        val events = mutableListOf<String>()

        persistProgressAfterFlush(
            flushDestination = { events += "flush" },
            persistSnapshot = { events += "persist" },
        )

        assertEquals(listOf("flush", "persist"), events)
    }

    @Test
    fun failedDestinationFlushNeverPersistsProgress() = runBlocking {
        var persisted = false

        assertFailsWith<IllegalStateException> {
            persistProgressAfterFlush(
                flushDestination = { error("flush failed") },
                persistSnapshot = { persisted = true },
            )
        }

        assertFalse(persisted)
    }

    @Test
    fun committedPartProgressSurvivesRestartAndIgnoresInterruptedTempFile() {
        val folder = java.nio.file.Files.createTempDirectory("download-parts-state-").toFile()
        val fileSaver = TransactionalFileSaver(Json)
        val expected = RangedParts(
            listOf(
                RangedPart(from = 0, to = 4, current = 3),
                RangedPart(from = 5, to = 9, current = 7),
            )
        )

        try {
            runBlocking {
                PartListFileStorage(folder, fileSaver).setParts(TEST_DOWNLOAD_ID, expected)
            }
            fileSaver.getBakFile(folder.resolve("$TEST_DOWNLOAD_ID.json"))
                .writeText("interrupted replacement")

            val restored = runBlocking {
                PartListFileStorage(folder, TransactionalFileSaver(Json)).getParts(TEST_DOWNLOAD_ID)
            }

            assertEquals(expected, restored)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun partProgressWriteFailureIsNotSilentlyIgnored() {
        val fileInsteadOfFolder = File.createTempFile("download-parts-not-a-folder-", ".tmp")

        try {
            assertFailsWith<Exception> {
                runBlocking {
                    PartListFileStorage(
                        folder = fileInsteadOfFolder,
                        fileSaver = TransactionalFileSaver(Json),
                    ).setParts(
                        id = TEST_DOWNLOAD_ID,
                        parts = RangedParts(listOf(RangedPart(from = 0, to = 9, current = 4))),
                    )
                }
            }
        } finally {
            fileInsteadOfFolder.delete()
        }
    }

    @Test
    fun removingCompletedEntryKeepsFileUnlessUserChecksDeleteFile() {
        assertFalse(
            shouldDeleteDownloadOutput(
                status = DownloadStatus.Completed,
                removeCompletedFile = false,
            )
        )
        assertTrue(
            shouldDeleteDownloadOutput(
                status = DownloadStatus.Completed,
                removeCompletedFile = true,
            )
        )
    }

    @Test
    fun removingUnfinishedEntryAlwaysDeletesItsPartialFile() {
        listOf(
            DownloadStatus.Added,
            DownloadStatus.Downloading,
            DownloadStatus.Paused,
            DownloadStatus.Error,
        ).forEach { status ->
            assertTrue(
                shouldDeleteDownloadOutput(
                    status = status,
                    removeCompletedFile = false,
                )
            )
        }
    }

    @Test
    fun stoppedPartResumesFromItsExactOffsetAndKeepsFileIntegrity() {
        val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }

        withSlowRangeFixture(bytes) { fixture ->
            val target = File.createTempFile("download-core-resume-", ".bin")
            val part = RangedPart(from = 0, to = bytes.lastIndex.toLong())
            val partSplitLock = Any()

            fun createDownloader(): Pair<HttpPartDownloader, DestWriter> {
                val writer = DestWriter(
                    id = part.getID(),
                    file = target,
                    seekPos = part.current,
                    writer = FileSystem.SYSTEM.openReadWrite(target.toOkioPath()),
                )
                val downloader = HttpPartDownloader(
                    credentials = HttpDownloadCredentials(fixture.url),
                    getDestWriter = { writer },
                    part = part,
                    client = fixture.client,
                    speedLimiters = emptyList(),
                    strictMode = true,
                    partSplitLock = partSplitLock,
                    minPartProvider = MinPartSizeProvider { 1 },
                ).also {
                    it.onTooManyErrors = { failure -> throw AssertionError(failure) }
                }
                return downloader to writer
            }

            var firstWriter: DestWriter? = null
            var resumedWriter: DestWriter? = null
            try {
                RandomAccessFile(target, "rw").use { it.setLength(bytes.size.toLong()) }
                val (firstDownloader, writer) = createDownloader()
                firstWriter = writer

                runBlocking {
                    withTimeout(15.seconds) {
                        firstDownloader.start()
                        assertTrue(firstDownloader.awaitToEnsureDataBeingTransferred())
                        delay(40)
                        firstDownloader.stop()
                        firstDownloader.join()
                        firstDownloader.awaitIdle()
                    }
                }
                writer.writer.close()
                firstWriter = null

                val resumeOffset = part.current
                assertTrue(resumeOffset in 1 until bytes.lastIndex.toLong())
                assertEquals(PartDownloadStatus.IDLE, part.status)

                val (resumedDownloader, writerAfterResume) = createDownloader()
                resumedWriter = writerAfterResume
                runBlocking {
                    try {
                        withTimeout(15.seconds) {
                            resumedDownloader.start()
                            assertEquals(
                                PartDownloadStatus.Completed,
                                resumedDownloader.awaitFinishOrError(),
                            )
                            resumedDownloader.join()
                        }
                    } finally {
                        resumedDownloader.stop()
                        withTimeoutOrNull(5.seconds) { resumedDownloader.join() }
                    }
                }

                val assembled = target.readBytes()
                assertContentEquals(bytes, assembled)
                assertEquals(SLOW_TEST_SHA256, assembled.sha256())
                assertEquals("bytes=0-${bytes.lastIndex}", fixture.requestedRanges.first())
                assertEquals("bytes=$resumeOffset-${bytes.lastIndex}", fixture.requestedRanges.last())
            } finally {
                firstWriter?.writer?.close()
                resumedWriter?.writer?.close()
                target.delete()
            }
        }
    }

    @Test
    fun retryDelayUsesBoundedExponentialBackoff() {
        assertEquals(3_000, calculateRetryDelayMillis(retryIndex = 0))
        assertEquals(6_000, calculateRetryDelayMillis(retryIndex = 1))
        assertEquals(12_000, calculateRetryDelayMillis(retryIndex = 2))
        assertEquals(60_000, calculateRetryDelayMillis(retryIndex = 20))
    }

    @Test
    fun retryAfterDeltaSecondsOverridesShorterBackoffAndIsBounded() {
        assertEquals(
            120_000,
            calculateRetryDelayMillis(retryIndex = 0, retryAfterHeader = "120"),
        )
        assertEquals(
            15 * 60_000,
            calculateRetryDelayMillis(retryIndex = 0, retryAfterHeader = "999999999"),
        )
    }

    @Test
    fun retryAfterHttpDateAndNestedResponseFailureArePreserved() {
        val now = 1_792_008_000_000L // Wed, 14 Oct 2026 20:00:00 GMT
        val response = HttpResponseInfo(
            statusCode = 429,
            message = "Too Many Requests",
            requestUrl = "https://example.test/file.bin",
            responseHeaders = mapOf("retry-after" to "Wed, 14 Oct 2026 20:00:10 GMT"),
        )
        val failure = assertFailsWith<UnSuccessfulResponseException> {
            response.expectSuccess()
        }
        val nested = IllegalStateException("part failed", failure)

        assertEquals("Wed, 14 Oct 2026 20:00:10 GMT", nested.retryAfterHeaderOrNull())
        assertEquals(
            10_000,
            calculateRetryDelayMillis(
                retryIndex = 0,
                retryAfterHeader = nested.retryAfterHeaderOrNull(),
                nowEpochMillis = now,
            ),
        )
    }

    @Test
    fun partRecoversFromOne503WithoutCorruptingTheFile() {
        val requestedRanges = downloadAfterTransientFault(TransientFault.ServerError)

        assertTrue(requestedRanges.size >= 2)
        assertEquals(requestedRanges.first(), requestedRanges.last())
    }

    @Test
    fun partRecoversFromOneReadTimeoutWithoutCorruptingTheFile() {
        val requestedRanges = downloadAfterTransientFault(
            fault = TransientFault.ReadTimeout,
            readTimeoutMillis = 100,
        )

        assertTrue(requestedRanges.size >= 2)
        assertEquals(requestedRanges.first(), requestedRanges.last())
    }

    @Test
    fun partRecoversFromMidStreamDisconnectAtTheExactWrittenOffset() {
        val requestedRanges = downloadAfterTransientFault(TransientFault.MidStreamDisconnect)
        val resumedStart = requireNotNull(
            RANGE_PATTERN.matchEntire(requestedRanges.last())
        ).groupValues[1].toLong()

        assertTrue(requestedRanges.size >= 2)
        assertEquals("bytes=0-${TRANSIENT_TEST_BYTES.lastIndex}", requestedRanges.first())
        assertTrue(resumedStart in 1 until TRANSIENT_TEST_BYTES.lastIndex.toLong())
    }

    private fun downloadAfterTransientFault(
        fault: TransientFault,
        readTimeoutMillis: Long = 10_000,
    ): List<String> = withTransientFaultFixture(
        bytes = TRANSIENT_TEST_BYTES,
        fault = fault,
        readTimeoutMillis = readTimeoutMillis,
    ) { fixture ->
        val target = File.createTempFile("download-core-transient-", ".bin")
        val part = RangedPart(from = 0, to = TRANSIENT_TEST_BYTES.lastIndex.toLong())
        val writer = DestWriter(
            id = part.getID(),
            file = target,
            seekPos = part.current,
            writer = FileSystem.SYSTEM.openReadWrite(target.toOkioPath()),
        )
        val terminalFailure = AtomicReference<Throwable?>()
        val downloader = HttpPartDownloader(
            credentials = HttpDownloadCredentials(fixture.url),
            getDestWriter = { writer },
            part = part,
            client = fixture.client,
            speedLimiters = emptyList(),
            strictMode = true,
            partSplitLock = Any(),
            minPartProvider = MinPartSizeProvider { 1 },
        ).also {
            it.onTooManyErrors = terminalFailure::set
        }

        try {
            RandomAccessFile(target, "rw").use {
                it.setLength(TRANSIENT_TEST_BYTES.size.toLong())
            }
            runBlocking {
                try {
                    withTimeout(20.seconds) {
                        downloader.start()
                        downloader.join()
                        assertEquals(PartDownloadStatus.Completed, part.status)
                    }
                } finally {
                    downloader.stop()
                    withTimeoutOrNull(5.seconds) { downloader.join() }
                }
            }

            val assembled = target.readBytes()
            assertNull(terminalFailure.get())
            assertTrue(part.isCompleted)
            assertContentEquals(TRANSIENT_TEST_BYTES, assembled)
            assertEquals(TRANSIENT_TEST_SHA256, assembled.sha256())
            fixture.requestedRanges.toList()
        } finally {
            writer.writer.close()
            target.delete()
        }
    }

    private fun <T> withHttpFixture(
        rangeMode: RangeMode,
        block: (HttpFixture) -> T,
    ): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.bin") { exchange ->
            respond(exchange, rangeMode)
        }
        server.start()
        return try {
            block(
                HttpFixture(
                    url = "http://127.0.0.1:${server.address.port}/file.bin",
                    client = createHttpClient(),
                )
            )
        } finally {
            server.stop(0)
        }
    }

    private fun respond(exchange: HttpExchange, rangeMode: RangeMode) {
        exchange.responseHeaders.add("ETag", TEST_ETAG)
        exchange.responseHeaders.add("Last-Modified", TEST_LAST_MODIFIED)
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")

        when (rangeMode) {
            RangeMode.Supported -> {
                val requestedRange = requireNotNull(exchange.requestHeaders.getFirst("Range"))
                val match = requireNotNull(RANGE_PATTERN.matchEntire(requestedRange))
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
                    ?: TEST_BYTES.lastIndex
                val body = TEST_BYTES.copyOfRange(start, end + 1)
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${TEST_BYTES.size}")
                exchange.sendResponseHeaders(206, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            RangeMode.Ignored -> {
                assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
                exchange.sendResponseHeaders(200, TEST_BYTES.size.toLong())
                exchange.responseBody.use { it.write(TEST_BYTES) }
            }

            RangeMode.NotSatisfiable -> {
                assertEquals("bytes=10-20", exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.add("Content-Range", "bytes */10")
                exchange.sendResponseHeaders(416, -1)
            }

            RangeMode.TooManyRequests -> {
                assertEquals("bytes=0-9", exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.add("Retry-After", "7")
                exchange.sendResponseHeaders(429, -1)
            }
        }
        exchange.close()
    }

    private fun <T> withSlowRangeFixture(
        bytes: ByteArray,
        block: (SlowHttpFixture) -> T,
    ): T {
        val requestedRanges = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow.bin") { exchange ->
            val requestedRange = requireNotNull(exchange.requestHeaders.getFirst("Range"))
            requestedRanges += requestedRange
            val match = requireNotNull(RANGE_PATTERN.matchEntire(requestedRange))
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
                ?: bytes.lastIndex

            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            try {
                exchange.responseBody.use { output ->
                    var offset = start
                    while (offset <= end) {
                        val length = minOf(8_192, end - offset + 1)
                        output.write(bytes, offset, length)
                        output.flush()
                        offset += length
                        Thread.sleep(5)
                    }
                }
            } catch (_: IOException) {
                // Expected when the first range connection is deliberately stopped.
            } finally {
                exchange.close()
            }
        }
        server.start()
        return try {
            block(
                SlowHttpFixture(
                    url = "http://127.0.0.1:${server.address.port}/slow.bin",
                    client = createHttpClient(),
                    requestedRanges = requestedRanges,
                )
            )
        } finally {
            server.stop(0)
        }
    }

    private fun <T> withTransientFaultFixture(
        bytes: ByteArray,
        fault: TransientFault,
        readTimeoutMillis: Long,
        block: (FaultyHttpFixture) -> T,
    ): T {
        val requestCount = AtomicInteger()
        val requestedRanges = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/faulty.bin") { exchange ->
            val requestedRange = requireNotNull(exchange.requestHeaders.getFirst("Range"))
            requestedRanges += requestedRange
            val match = requireNotNull(RANGE_PATTERN.matchEntire(requestedRange))
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
                ?: bytes.lastIndex
            val attempt = requestCount.incrementAndGet()

            try {
                if (fault == TransientFault.ServerError && attempt == 1) {
                    exchange.sendResponseHeaders(503, -1)
                    return@createContext
                }

                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
                exchange.sendResponseHeaders(206, (end - start + 1).toLong())

                if (fault == TransientFault.ReadTimeout && attempt == 1) {
                    Thread.sleep(350)
                }

                exchange.responseBody.use { output ->
                    if (fault == TransientFault.MidStreamDisconnect && attempt == 1) {
                        val length = minOf(32 * 1024, end - start + 1)
                        output.write(bytes, start, length)
                        output.flush()
                    } else {
                        output.write(bytes, start, end - start + 1)
                    }
                }
            } catch (_: IOException) {
                // The timeout/disconnect attempt is intentionally closed by the client.
            } finally {
                exchange.close()
            }
        }
        server.start()
        return try {
            block(
                FaultyHttpFixture(
                    url = "http://127.0.0.1:${server.address.port}/faulty.bin",
                    client = createHttpClient(readTimeoutMillis),
                    requestedRanges = requestedRanges,
                )
            )
        } finally {
            server.stop(0)
        }
    }

    private fun createHttpClient(readTimeoutMillis: Long = 10_000) = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient.Builder()
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent() = "download-manager-test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )

    private fun ir.amirab.downloader.connection.Connection<HttpResponseInfo>.readAllBytes(): ByteArray {
        val buffer = Buffer()
        while (true) {
            val count = source.read(buffer, 8_192)
            if (count == -1L) break
        }
        return buffer.readByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private data class HttpFixture(
        val url: String,
        val client: OkHttpHttpDownloaderClient,
    )

    private data class SlowHttpFixture(
        val url: String,
        val client: OkHttpHttpDownloaderClient,
        val requestedRanges: List<String>,
    )

    private data class FaultyHttpFixture(
        val url: String,
        val client: OkHttpHttpDownloaderClient,
        val requestedRanges: List<String>,
    )

    private enum class TransientFault {
        ServerError,
        ReadTimeout,
        MidStreamDisconnect,
    }

    private enum class RangeMode {
        Supported,
        Ignored,
        NotSatisfiable,
        TooManyRequests,
    }

    private companion object {
        fun unlimitedDiskStat(): IDiskStat = object : IDiskStat {
            override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
        }

        fun testSparseFile(): ISparseFile = object : ISparseFile {
            override fun createSparseFile(file: File): Boolean = file.createNewFile()

            override fun canWeCreateSparseFile(file: File): Boolean = true
        }

        val TEST_BYTES = ByteArray(10) { it.toByte() }
        const val TEST_ETAG = "\"fixture-v1\""
        const val TEST_LAST_MODIFIED = "Wed, 14 Oct 2026 20:00:00 GMT"
        const val TEST_DOWNLOAD_ID = 42L
        const val TEST_SHA256 = "1f825aa2f0020ef7cf91dfa30da4668d791c5d4824fc8e41354b89ec05795ab3"
        const val SLOW_TEST_SHA256 = "61d1d9c5745bdaa4fab39240651bc242a5186b15393fd475082fcf6e84f400ab"
        val TRANSIENT_TEST_BYTES = ByteArray(128 * 1024) { (it % 239).toByte() }
        const val TRANSIENT_TEST_SHA256 = "a543dd6017d1b53148dd67eba279f6ae618ea88e6c1b98436ff497e37429a4a0"
        val RANGE_PATTERN = Regex("""bytes=(\d+)-(\d*)""")
    }
}
