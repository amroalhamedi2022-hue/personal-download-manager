package ir.amirab.downloader.part

import java.io.Closeable
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PartResponseDeadlineTest {
    @Test
    fun stalledResponseHeaderAcquisitionExpiresWithoutWaitingForEntireBody() = runBlocking<Unit> {
        val failure = runCatching {
            awaitPartResponseWithDeadline(25L) {
                delay(300)
                Closeable {}
            }
        }.exceptionOrNull()
        assertTrue(failure is SocketTimeoutException, "Timeout should be retried as socket failure")
    }

    @Test
    fun parentCancellationIsNotRewrittenAsRetryableSocketTimeout() = runBlocking<Unit> {
        val worker = async {
            awaitPartResponseWithDeadline(2_000L) {
                delay(5_000L)
                Closeable {}
            }
        }
        delay(25)
        worker.cancel()
        val failure = runCatching { worker.await() }.exceptionOrNull()
        assertTrue(failure is CancellationException, "User cancellation must remain cancellation")
        assertTrue(failure !is SocketTimeoutException)
    }

    @Test
    fun successfulResponseAcquisitionReturnsOriginalCloseable() = runBlocking<Unit> {
        val connection = Closeable {}
        val result = awaitPartResponseWithDeadline(2_000L) { connection }
        assertSame(connection, result)
    }
}
