package ir.amirab.downloader.part

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RangedPartLengthSafetyTest {
    @Test
    fun unrepresentablyLongInclusiveRangeSaturatesWithoutWrappingNegative() {
        val part = RangedPart(from = 0, to = Long.MAX_VALUE, current = 0)
        assertEquals(Long.MAX_VALUE, part.partLength)
        assertEquals(Long.MAX_VALUE, part.remainingLength)
        assertEquals(0, part.percent)
        part.current = Long.MAX_VALUE
        assertEquals(1L, part.remainingLength)
        assertEquals(99, part.percent)
        assertFalse(part.isCompleted)
    }

    @Test
    fun largestRepresentableRangeLengthIsExact() {
        val part = RangedPart(from = 0, to = Long.MAX_VALUE - 1, current = Long.MAX_VALUE - 50)
        assertEquals(Long.MAX_VALUE, part.partLength)
        assertEquals(50L, part.remainingLength)
        assertEquals(99, part.percent)
        assertFalse(part.isCompleted)
    }

    @Test
    fun completedRangeHasZeroRemainingBytesAndTrueCompletion() {
        val part = RangedPart(from = 0, to = 9, current = 10)
        assertEquals(10L, part.partLength)
        assertEquals(0L, part.remainingLength)
        assertEquals(100, part.percent)
        assertTrue(part.isCompleted)
    }

    @Test
    fun corruptCursorBeyondEndCannotCreateNegativeRemainingLength() {
        val part = RangedPart(from = 0, to = 9, current = 25)
        assertEquals(0L, part.remainingLength)
        assertEquals(99, part.percent)
        assertFalse(part.isCompleted)
    }

    @Test
    fun blindPartRetainsUnknownLengthWithoutOverflow() {
        val part = RangedPart(from = 0, to = null)
        assertNull(part.partLength)
        assertNull(part.remainingLength)
        assertNull(part.percent)
    }
}
