package ir.amirab.downloader.connection.response.headers

import ir.amirab.downloader.connection.response.HttpResponseInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RangeHeaderExtractorTest {
    private fun parse(value: String): ContentRangeValue? =
        HttpResponseInfo(
            statusCode = 206,
            message = "Partial Content",
            requestUrl = "https://example.test/archive.bin",
            responseHeaders = mapOf("content-range" to value),
        ).contentRange

    @Test
    fun contentRangeRejectsReversedBounds() {
        assertNull(parse("bytes 9-2/10"))
        assertNull(parse("bytes 10-9/*"))
        assertNull(parse("bytes 0-10/10"))
        assertNull(parse("bytes 0-0/0"))
        assertNull(parse("bytes -1-2/10"))
    }

    @Test
    fun contentRangeAcceptsValidAndLegacyResponses() {
        val standard = assertNotNull(parse("bytes 0-9/10"))
        assertEquals(0L..9L, standard.range)
        assertEquals(10L, standard.fullSize)

        val legacy = assertNotNull(parse("0-9/10"))
        assertEquals(0L..9L, legacy.range)
        assertEquals(10L, legacy.fullSize)

        val upper = assertNotNull(parse("BYTES 4-8/9"))
        assertEquals(4L..8L, upper.range)
        assertEquals(9L, upper.fullSize)
    }

    @Test
    fun contentRangeAllowsUnknownTotalAndUnsatisfiedByteRange() {
        val unknownSize = assertNotNull(parse("bytes 11-20/*"))
        assertEquals(11L..20L, unknownSize.range)
        assertNull(unknownSize.fullSize)

        val unsatisfied = assertNotNull(parse("bytes */10"))
        assertNull(unsatisfied.range)
        assertEquals(10L, unsatisfied.fullSize)

        val empty = assertNotNull(parse("bytes */0"))
        assertNull(empty.range)
        assertEquals(0L, empty.fullSize)
        assertNull(parse("bytes */*"))
    }

    @Test
    fun contentRangeRejectsMalformedFieldsAndIntegerOverflow() {
        for (value in listOf(
            "",
            "bytes",
            "bytes ",
            "bytes 0-9",
            "bytes 0-9/10/11",
            "bytes 0-9/10x",
            "bytes 0--9/10",
            "bytes 0-9/-10",
            "bytes 0-9/+10",
            "bytes 0-9/0",
            "bytes 0-9/18446744073709551616",
            "bytes 18446744073709551616-18446744073709551617/*",
            "items 0-9/10",
            "bytes 0-9/10\r\nX-Injected: true",
        )) {
            assertNull(parse(value), "Malformed Content-Range was accepted: $value")
        }
    }

    @Test
    fun contentRangeTrimsOutsideWhitespaceButNotMalformedInterior() {
        val range = assertNotNull(parse("  bytes 0-1/2  "))
        assertEquals(0L..1L, range.range)
        assertEquals(2L, range.fullSize)
        assertNull(parse("bytes 0 -1/2"))
        assertNull(parse("bytes 0-1/ 2"))
    }
}
