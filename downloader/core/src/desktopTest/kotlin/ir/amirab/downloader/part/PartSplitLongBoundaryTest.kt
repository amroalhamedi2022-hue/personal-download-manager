package ir.amirab.downloader.part

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartSplitLongBoundaryTest {
    @Test
    fun oneByteAfterSafeZoneCannotProduceWrappedRange() {
        val original = RangedPart(
            from = Long.MAX_VALUE - 1,
            to = Long.MAX_VALUE,
            current = Long.MAX_VALUE,
        )
        val support = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 1L })
        assertNull(support.splitPart())
        assertEquals(Long.MAX_VALUE, original.to)
        assertEquals(Long.MAX_VALUE - 1, original.from)
    }

    @Test
    fun splittingNearMaximumLongKeepsBothRangesContiguous() {
        val original = RangedPart(
            from = Long.MAX_VALUE - 4,
            to = Long.MAX_VALUE,
        )
        val support = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 1L })
        val second = requireNotNull(support.splitPart())
        assertEquals(Long.MAX_VALUE - 2, original.to)
        assertEquals(Long.MAX_VALUE - 1, second.from)
        assertEquals(Long.MAX_VALUE, second.to)
        assertTrue(original.to!! < second.from)
    }

    @Test
    fun singleByteRangeAtZeroNeverSplitsIntoEmptyOrNegativePart() {
        val original = RangedPart(from = 0L, to = 0L)
        val support = PartSplitSupport(original, minPartSizeProvider = MinPartSizeProvider { 1L })
        assertNull(support.splitPart())
        assertEquals(0L..0L, original.range)
    }
}
