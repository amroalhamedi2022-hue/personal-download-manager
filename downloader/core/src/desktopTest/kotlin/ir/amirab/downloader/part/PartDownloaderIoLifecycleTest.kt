package ir.amirab.downloader.part

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.destination.DestWriter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.Test
import kotlin.test.assertFalse
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
