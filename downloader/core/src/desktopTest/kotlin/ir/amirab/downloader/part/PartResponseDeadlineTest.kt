package ir.amirab.downloader.part

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PartResponseDeadlineTest {
    private class TestConnection : Closeable {
        var closed = false
        override fun close() { closed = true }
    }

    @Test fun fastResponseIsReturnedWithoutClosingTheStream() = runBlocking {
        val connection = TestConnection()
        val returned = awaitPartResponseWithDeadline(500L) {
            delay(10)
            connection
        }
        assertTrue(returned === connection)
        assertEquals(false, connection.closed)
        returned.close()
        assertTrue(connection.closed)
        Unit
    }

    @Test fun noResponseBecomesRetryableIOExceptionRatherThanSilentConnecting() = runBlocking {
        val failure = runCatching {
            awaitPartResponseWithDeadline(25L) {
                awaitCancellation()
                TestConnection()
            }
        }.exceptionOrNull()
        assertIs<SocketTimeoutException>(failure)
        Unit
    }

    @Test fun explicitCancellationIsNotConvertedIntoRetryableSocketTimeout() = runBlocking {
        val task = async {
            awaitPartResponseWithDeadline(10_000L) {
                awaitCancellation()
                TestConnection()
            }
        }
        delay(20)
        task.cancel()
        val failure = runCatching { task.await() }.exceptionOrNull()
        assertIs<CancellationException>(failure)
        Unit
    }

    @Test fun outerDeadlineRemainsCancellationRatherThanNetworkRetry() = runBlocking {
        val failure = runCatching {
            withTimeout(25L) {
                awaitPartResponseWithDeadline(10_000L) {
                    awaitCancellation()
                    TestConnection()
                }
            }
        }.exceptionOrNull()
        assertIs<TimeoutCancellationException>(failure)
        Unit
    }
}
