package ir.amirab.downloader.utils.speedlimiter

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedLimitedSourceZeroReadTest {
    @Test
    fun zeroByteReadDoesNotConsumeDataWhenUnlimited() {
        val source = SpeedLimiter().source(Buffer().writeUtf8("hyperget"))
        val received = Buffer()
        source.use {
            assertEquals(0L, it.read(received, 0L))
            assertEquals(8L, it.read(received, 8L))
        }
        assertEquals("hyperget", received.readUtf8())
    }

    @Test
    fun zeroByteReadDoesNotConsumeDataWhenLimited() {
        val limiter = SpeedLimiter().apply { bytesPerSecond(10_000L) }
        val source = limiter.source(Buffer().writeUtf8("download"))
        val received = Buffer()
        source.use { throttled ->
            repeat(16) { assertEquals(0L, throttled.read(received, 0L)) }
            assertEquals(8L, throttled.read(received, 8L))
        }
        assertEquals("download", received.readUtf8())
    }
}
