package ir.amirab.downloader.part

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
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
import ir.amirab.downloader.db.PartListFileStorage
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.persistProgressAfterFlush
import ir.amirab.downloader.downloaditem.http.validateRemoteFileIdentity
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.exception.FileChangedException
import ir.amirab.downloader.exception.UnSuccessfulResponseException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.security.MessageDigest
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
        }
        exchange.close()
    }

    private fun createHttpClient() = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient(),
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

    private enum class RangeMode {
        Supported,
        Ignored,
        NotSatisfiable,
    }

    private companion object {
        val TEST_BYTES = ByteArray(10) { it.toByte() }
        const val TEST_ETAG = "\"fixture-v1\""
        const val TEST_LAST_MODIFIED = "Wed, 14 Oct 2026 20:00:00 GMT"
        const val TEST_DOWNLOAD_ID = 42L
        const val TEST_SHA256 = "1f825aa2f0020ef7cf91dfa30da4668d791c5d4824fc8e41354b89ec05795ab3"
        val RANGE_PATTERN = Regex("""bytes=(\d+)-(\d*)""")
    }
}
