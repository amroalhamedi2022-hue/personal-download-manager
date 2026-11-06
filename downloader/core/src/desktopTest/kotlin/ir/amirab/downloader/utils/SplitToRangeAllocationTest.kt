package ir.amirab.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitToRangeAllocationTest {
    @Test
    fun sixteenActivePartsCoverFileWithoutGapsOrUndersizedParts() {
        val size = 16L * 1024 * 1024 + 7
        val minPart = 512L * 1024
        val parts = splitToRange(size, minPart, 16)
        assertEquals(16, parts.size)
        assertEquals(0L, parts.first().first)
        assertEquals(size - 1, parts.last().last)
        assertTrue(parts.all { it.last - it.first + 1 >= minPart })
        parts.zipWithNext().forEach { (left, right) ->
            assertEquals(left.last + 1, right.first)
        }
    }

    @Test
    fun largeRequestedPartCountDoesNotChangeMinimumSizeOrRangeCoverage() {
        val parts = splitToRange(1025, 512, Long.MAX_VALUE)
        assertEquals(listOf(0L..512L, 513L..1024L), parts)
    }

    @Test
    fun smallerThanMinimumStaysSinglePart() {
        assertEquals(listOf(0L..6L), splitToRange(7, 512, 16))
    }
}
