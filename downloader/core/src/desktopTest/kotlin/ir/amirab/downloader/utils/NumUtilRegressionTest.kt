package ir.amirab.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class NumUtilRegressionTest {
    @Test
    fun unknownAndInvalidLengthsDoNotClaimCompletion() {
        assertEquals(0, calcPercent(100L, 0L))
        assertEquals(0, calcPercent(100, -1))
        assertEquals(0, calcPercent(100.0, Double.NaN))
        assertEquals(0, calcPercent(Double.POSITIVE_INFINITY, 100.0))
    }

    @Test
    fun veryLargeIncompleteTransfersNeverReportComplete() {
        assertEquals(99, calcPercent(Long.MAX_VALUE - 1L, Long.MAX_VALUE))
        assertEquals(100, calcPercent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0, calcPercent(0L, Long.MAX_VALUE))
        assertEquals(99, calcPercent(999L, 1000L))
    }

    @Test
    fun percentagesStayWithinBounds() {
        assertEquals(0, calcPercent(-1L, 100L))
        assertEquals(100, calcPercent(101, 100))
        assertEquals(100, calcPercent(Double.MAX_VALUE, 1.0))
        assertEquals(25, calcPercent(1L, 4L))
        assertEquals(50, calcPercent(1, 2))
        assertEquals(75, calcPercent(3.0, 4.0))
    }
}
