package ir.amirab.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionUtilsRegressionTest {
    @Test
    fun missingSwapMemberPreservesOriginalSet() {
        val original = linkedSetOf("first", "second", "third")
        assertEquals(original, original.swapped("first", "missing"))
        assertEquals(original, original.swapped("missing", "second"))
        assertEquals(original, original.swapped("missing-a", "missing-b"))
    }

    @Test
    fun presentMembersStillSwapWithoutChangingMembership() {
        val original = linkedSetOf("first", "second", "third")
        val swapped = original.swapped("first", "third")
        assertEquals(listOf("third", "second", "first"), swapped.toList())
        assertEquals(original, swapped)
        assertEquals(listOf("first", "second", "third"), original.toList())
    }
}
