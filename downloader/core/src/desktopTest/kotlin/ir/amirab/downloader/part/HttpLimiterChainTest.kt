package ir.amirab.downloader.part

import ir.amirab.downloader.utils.speedlimiter.SpeedLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpLimiterChainTest {
    @Test
    fun sixteenWorkersPreserveBytesWithSharedHostLimiter() = runBlocking {
        val global = SpeedLimiter()
        val perDownload = listOf(SpeedLimiter(), SpeedLimiter())
        (1..16).map { id ->
            async(Dispatchers.Default) {
                val expected = "worker-$id-".repeat(64)
                val source = chainSpeedLimiters(
                    Buffer().writeUtf8(expected),
                    perDownload,
                    global,
                )
                val received = Buffer()
                source.use { stream ->
                    while (true) {
                        if (stream.read(received, 37L) == -1L) break
                    }
                }
                assertEquals(expected, received.readUtf8())
            }
        }.awaitAll()
    }

    @Test
    fun limiterChainClosesOriginalTransportExactlyOnce() {
        var closes = 0
        val transport = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = -1L
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() { closes++ }
        }
        chainSpeedLimiters(
            transport,
            listOf(SpeedLimiter(), SpeedLimiter()),
            SpeedLimiter(),
        ).close()
        assertEquals(1, closes)
    }

    @Test
    fun noLimitersReturnsTheOriginalSourceWithoutNewWrapper() {
        val source: Source = Buffer().writeUtf8("passthrough")
        val result = chainSpeedLimiters(source, emptyList(), null)
        kotlin.test.assertSame(source, result)
    }
}
