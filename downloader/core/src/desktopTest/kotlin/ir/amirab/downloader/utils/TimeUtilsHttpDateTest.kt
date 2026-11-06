package ir.amirab.downloader.utils

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeUtilsHttpDateTest {
    private val validHeader = "Wed, 21 Oct 2015 07:28:00 GMT"
    private val expected = Instant.parse("2015-10-21T07:28:00Z").toEpochMilli()

    @Test
    fun parsesValidUtcLastModifiedExactly() {
        assertEquals(expected, TimeUtils.convertLastModifiedHeaderToTimestamp(validHeader))
    }

    @Test
    fun sixteenConcurrentResponseHandlersReadIdenticalTime() = runBlocking {
        val parsed = (1..16).map {
            async(Dispatchers.Default) {
                TimeUtils.convertLastModifiedHeaderToTimestamp(validHeader)
            }
        }.awaitAll()
        assertEquals(List(16) { expected }, parsed)
    }

    @Test
    fun rejectsInvalidWeekdayAndImpossibleCalendarDates() {
        for (invalid in listOf(
            "Tue, 21 Oct 2015 07:28:00 GMT",
            "Wed, 32 Oct 2015 07:28:00 GMT",
            "Wed, 21 Oct 2015 25:28:00 GMT",
            "Wed, 29 Feb 2015 07:28:00 GMT",
        )) {
            assertFailsWith<IllegalArgumentException>(invalid) {
                TimeUtils.convertLastModifiedHeaderToTimestamp(invalid)
            }
        }
    }

    @Test
    fun rejectsTrailingTextControlCharactersAndNonGmtZone() {
        for (invalid in listOf(
            "$validHeader injected",
            "$validHeader\r\nLast-Modified: Wed, 21 Oct 2015 08:28:00 GMT",
            "$validHeader ",
            "Wed, 21 Oct 2015 07:28:00 PST",
            "",
        )) {
            assertFailsWith<IllegalArgumentException>(invalid) {
                TimeUtils.convertLastModifiedHeaderToTimestamp(invalid)
            }
        }
    }
}
