package ir.amirab.downloader.downloaditem

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryPolicyRobustnessTest {
    @Test
    fun retryAfterSecondsRespectBackoffAndSafetyCap() {
        assertEquals(3_000L, calculateRetryDelayMillis(0))
        assertEquals(6_000L, calculateRetryDelayMillis(1))
        assertEquals(60_000L, calculateRetryDelayMillis(9))
        assertEquals(3_000L, calculateRetryDelayMillis(-10))
        assertEquals(23_000L, calculateRetryDelayMillis(0, "23"))
        assertEquals(6_000L, calculateRetryDelayMillis(1, "0"))
        assertEquals(MAX_SERVER_RETRY_DELAY_MILLIS, calculateRetryDelayMillis(0, "900"))
        assertEquals(MAX_SERVER_RETRY_DELAY_MILLIS, calculateRetryDelayMillis(0, "999999999999999999999999999999"))
        assertEquals(MAX_SERVER_RETRY_DELAY_MILLIS, calculateRetryDelayMillis(0, "  999999999999999999999999  "))
    }

    @Test
    fun validHttpDateUsesServerDeadlineWithoutExceedingCap() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        assertEquals(
            60_000L,
            calculateRetryDelayMillis(0, "Wed, 21 Oct 2015 07:28:00 GMT", now),
        )
        assertEquals(
            MAX_SERVER_RETRY_DELAY_MILLIS,
            calculateRetryDelayMillis(0, "Wed, 21 Oct 2015 07:28:00 GMT", now - 5_000_000L),
        )
        assertEquals(
            3_000L,
            calculateRetryDelayMillis(0, "Wed, 21 Oct 2015 07:28:00 GMT", now + 600_000L),
        )
    }

    @Test
    fun malformedHttpDatesCannotSetAnArbitraryRetryDelay() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        val invalid = listOf(
            "Tue, 21 Oct 2015 07:28:00 GMT", // wrong weekday
            "Wed, 32 Oct 2015 07:28:00 GMT",
            "Wed, 21 Oct 2015 25:28:00 GMT",
            "Wed, 21 Oct 2015 07:28:00 PST",
            "Wed, 21 Oct 2015 07:28:00 GMT; 900",
            "Wed, 21 Oct 2015 07:28:00 GMT EXTRA",
            "Wed, 21 Oct 2015 07:28:00 +0000",
            "-300",
            "+300",
            "3.5",
            "١٠",
        )
        invalid.forEach { header ->
            assertEquals(
                3_000L,
                calculateRetryDelayMillis(0, header, now),
                "Malformed Retry-After should fall back to the bounded job backoff",
            )
        }
    }

    @Test
    fun dateSubtractionCannotOverflowForCorruptSystemClock() {
        assertEquals(
            MAX_SERVER_RETRY_DELAY_MILLIS,
            calculateRetryDelayMillis(0, "Wed, 21 Oct 2015 07:28:00 GMT", Long.MIN_VALUE),
        )
        assertEquals(
            3_000L,
            calculateRetryDelayMillis(0, "Wed, 21 Oct 2015 07:28:00 GMT", Long.MAX_VALUE),
        )
    }

    @Test
    fun retryAfterRejectsControlCharactersButAllowsHttpOws() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        assertEquals(23_000L, calculateRetryDelayMillis(0, "\t 23 \t", now))
        val invalid = listOf(
            "23\r\nRetry-After: 900",
            "23\n",
            "23\r",
            "23\u0000",
            "23\u007f",
            "\u200323",
            "Wed, 21 Oct 2015 07:28:00 GMT\n",
        )
        invalid.forEach { header ->
            assertEquals(3_000L, calculateRetryDelayMillis(0, header, now), "Invalid header: $header")
        }
    }

    @Test
    fun cyclicExceptionCausesNeverHangRetryAfterInspection() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        first.initCause(second)
        second.initCause(first)
        assertNull(first.retryAfterHeaderOrNull())
    }
}
