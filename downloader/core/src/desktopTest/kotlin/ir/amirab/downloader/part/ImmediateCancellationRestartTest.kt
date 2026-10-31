package ir.amirab.downloader.part

import ir.amirab.downloader.connection.Connection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ImmediateCancellationRestartTest {
    @Test
    fun immediatelyCancelledPartWorkersReleaseActiveStateAndCanRestart() = runBlocking<Unit> {
        val part = RangedPart(from = 0L, to = 4095L)
        val downloader = object : PartDownloader<RangedPart>(
            part = part,
            getDestWriter = { error("An immediately stopped part should not write output") },
        ) {
            override fun howMuchCanRead(maxAllowed: Long): Long = maxAllowed

            override suspend fun connectAndVerify(): Connection<*> = awaitCancellation()
        }
        downloader.onTooManyErrors = { throw it }
        try {
            repeat(64) { cycle ->
                downloader.start()
                downloader.stop()
                withTimeout(2_000L) {
                    downloader.join()
                    while (downloader.active) delay(1)
                }
                assertFalse(downloader.active, "Cycle $cycle left a phantom active worker")
                assertEquals(PartDownloadStatus.IDLE, part.statusFlow.value)
            }
        } finally {
            downloader.stop()
            withTimeout(2_000L) { downloader.join() }
        }
    }
}
