package ir.amirab.downloader.part

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RangedPartProgressSafetyTest {
    @Test
    fun nearlyFinishedHugePartNeverRoundsUpToOneHundred() {
        val part = RangedPart(
            from = 0,
            to = Long.MAX_VALUE - 1,
            current = Long.MAX_VALUE - 2,
        )
        assertEquals(99, part.percent)
        assertFalse(part.isCompleted)
    }

    @Test
    fun smallRangedPartProgressRetainsExactBoundarySemantics() {
        val part = RangedPart(from = 0, to = 99, current = 99)
        assertEquals(99, part.percent)
        assertFalse(part.isCompleted)
        part.current = 100
        assertEquals(100, part.percent)
        assertTrue(part.isCompleted)
    }

    @Test
    fun unknownLengthDoesNotReportCompletionOrInventPercentage() {
        val part = RangedPart(from = 0, to = null, current = 400)
        assertNull(part.percent)
        assertFalse(part.isCompleted)
    }

    @Test
    fun overflowedMaxEndNeverTreatsWrappedNegativeCursorAsComplete() {
        val part = RangedPart(
            from = 1,
            to = Long.MAX_VALUE,
            current = Long.MIN_VALUE,
        )
        assertFalse(part.isCompleted)
        assertEquals(0, part.percent)
    }
}
