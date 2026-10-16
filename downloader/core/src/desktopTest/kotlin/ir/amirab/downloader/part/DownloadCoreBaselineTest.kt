package ir.amirab.downloader.part

import ir.amirab.downloader.connection.response.HttpResponseInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadCoreBaselineTest {
    @Test
    fun splitPreservesCoverageWithoutOverlap() {
        val original = RangedPart(from = 0, to = 999)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })

        val second = split.splitPart()

        assertEquals(0L..499L, original.range)
        assertEquals(500L..999L, second?.range)
        assertEquals(original.to?.plus(1), second?.from)
    }

    @Test
    fun splitNeverMovesBeforeTheSafeZone() {
        val original = RangedPart(from = 0, to = 999, current = 400)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 100 })
        split.safeZone = 599

        val second = split.splitPart()

        assertEquals(799L, original.to)
        assertEquals(800L, second?.from)
        assertTrue(split.safeZone <= original.to!!)
    }

    @Test
    fun splitRejectsPartsBelowTheConfiguredMinimum() {
        val original = RangedPart(from = 0, to = 199)
        val split = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 200 })

        assertFalse(split.canSplit())
        assertNull(split.splitPart())
        assertEquals(0L..199L, original.range)
    }

    @Test
    fun partialResponseKeepsIdentityAndRangeMetadata() {
        val response = HttpResponseInfo(
            statusCode = 206,
            message = "Partial Content",
            requestUrl = "https://example.test/file.bin",
            responseHeaders = mapOf(
                "content-length" to "4",
                "content-range" to "bytes 2-5/10",
                "etag" to "\"baseline-v1\"",
                "last-modified" to "Wed, 14 Oct 2026 20:00:00 GMT",
            ),
        )

        assertTrue(response.isPartial)
        assertTrue(response.resumeSupport)
        assertEquals(10L, response.totalLength)
        assertEquals("\"baseline-v1\"", response.etag)
        assertEquals("Wed, 14 Oct 2026 20:00:00 GMT", response.lastModified)
    }
}
