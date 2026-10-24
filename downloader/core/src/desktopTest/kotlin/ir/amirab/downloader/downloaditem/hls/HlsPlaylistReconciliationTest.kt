package ir.amirab.downloader.downloaditem.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deferred desktopTest: do not invoke full Gradle compilation before final readiness. */
class HlsPlaylistReconciliationTest {
    @Test
    fun sameSegmentKeepsCompletionAndProgress() {
        val previous = listOf(HlsSegmentSnapshot(5, "s5.ts", 4.0, completed = true, length = 4096, current = 4096))
        val updated = reconcileHlsSegments(previous, listOf(HlsSegmentSnapshot(5, "s5.ts", 4.0)))
        assertTrue(updated.single().completed)
        assertEquals(4096L, updated.single().length)
        assertEquals(4096L, updated.single().current)
    }

    @Test
    fun changedUriOrDurationDoesNotReuseCompletedBytes() {
        val previous = listOf(HlsSegmentSnapshot(5, "s5.ts?sig=old", 4.0, completed = true, length = 4096, current = 4096))
        val changedUri = reconcileHlsSegments(previous, listOf(HlsSegmentSnapshot(5, "s5.ts?sig=new", 4.0)))
        val changedDuration = reconcileHlsSegments(previous, listOf(HlsSegmentSnapshot(5, "s5.ts?sig=old", 5.0)))
        for (segment in changedUri + changedDuration) {
            assertFalse(segment.completed)
            assertEquals(null, segment.length)
            assertEquals(0L, segment.current)
        }
    }

    @Test
    fun adoptsNewOrderAndDropsMissingSegments() {
        val previous = listOf(HlsSegmentSnapshot(1, "one", 1.0), HlsSegmentSnapshot(2, "two", 2.0))
        val updated = reconcileHlsSegments(previous, listOf(HlsSegmentSnapshot(2, "two", 2.0), HlsSegmentSnapshot(3, "three", 3.0)))
        assertEquals(listOf(2L, 3L), updated.map { it.sequence })
    }
}
