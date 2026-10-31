package ir.amirab.downloader.part

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.destination.DestWriter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.Timeout
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PartTransportCancellationTest {
    private val info = object : IResponseInfo {
        override val isSuccessFul = true
        override val requiresAuth = false
        override val requireBasicAuth = false
        override val resumeSupport = true
        override val isWebPage = false
        override val unsuccessFullException: Throwable? = null
    }

    @Test
    fun closingConnectionCancelsTransportBeforeReleasingSource() {
        val order = mutableListOf<String>()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = -1
            override fun timeout() = Timeout.NONE
            override fun close() { order.add("source") }
        }
        Connection(source, 0L, info, cancelTransport = { order.add("network") }).close()
        assertEquals(listOf("network", "source"), order)
    }

    @Test
    fun throwingTransportCancelStillClosesSource() {
        val closed = AtomicBoolean()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long) = -1L
            override fun timeout() = Timeout.NONE
            override fun close() { closed.set(true) }
        }
        assertFailsWith<IOException> {
            Connection(source, 0L, info, cancelTransport = { throw IOException("cancel failed") }).close()
        }
        assertTrue(closed.get())
    }

    @Test
    fun stoppingWorkerClosesSourceThatIgnoresThreadInterrupts() = runBlocking<Unit> {
        val file = Files.createTempFile("hyperget-stop-transport-", ".part").toFile()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val sourceClosed = AtomicBoolean()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                entered.countDown()
                while (!sourceClosed.get()) {
                    try { unblock.await(100, TimeUnit.MILLISECONDS) }
                    catch (_: InterruptedException) { /* Deliberately ignore interrupts. */ }
                }
                throw IOException("closed on stop")
            }
            override fun timeout() = Timeout.NONE
            override fun close() {
                sourceClosed.set(true)
                unblock.countDown()
            }
        }
        val handle = FileSystem.SYSTEM.openReadWrite(file.toOkioPath())
        val part = RangedPart(from = 0L, to = 1023L)
        val downloader = object : PartDownloader<RangedPart>(
            part = part,
            getDestWriter = { DestWriter(part.getID(), file, part.current, handle) }
        ) {
            override fun howMuchCanRead(maxAllowed: Long) = maxAllowed
            override suspend fun connectAndVerify() = Connection(source, 1024L, info)
        }
        downloader.onTooManyErrors = {}
        try {
            downloader.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Worker never entered body read")
            downloader.stop()
            withTimeout(5_000L) { downloader.join() }
            assertTrue(sourceClosed.get(), "stop() did not close the response stream")
            assertFalse(downloader.active, "stopped worker retained active status")
        } finally {
            downloader.stop()
            unblock.countDown()
            withTimeout(5_000L) { downloader.join() }
            handle.close()
            file.delete()
        }
    }
}
