package ir.amirab.downloader.db

import ir.amirab.downloader.part.MediaSegment
import ir.amirab.downloader.part.MediaSegments
import ir.amirab.downloader.part.RangedPart
import ir.amirab.downloader.part.RangedParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class MutablePartSnapshotIsolationTest {
    @Test
    fun rangeCloneDoesNotShareMutableProgressOrBounds() {
        val original = RangedParts(listOf(RangedPart(from = 0, to = 1023, current = 100)))
        val snapshot = original.clone() as RangedParts
        assertNotSame(original.list, snapshot.list)
        assertNotSame(original.list.single(), snapshot.list.single())
        assertNotSame(original.list.single().statusFlow, snapshot.list.single().statusFlow)

        snapshot.list.single().current = 900
        snapshot.list.single().to = 950
        assertEquals(100L, original.list.single().current)
        assertEquals(1023L, original.list.single().to)
    }

    @Test
    fun mediaCloneDoesNotShareSegmentCompletionOrMetadata() {
        val original = MediaSegments(
            listOf(MediaSegment(segmentIndex = 1, link = "https://example.test/1.ts", duration = 4.5))
        )
        val snapshot = original.clone() as MediaSegments
        assertNotSame(original.list.single(), snapshot.list.single())
        assertNotSame(original.list.single().statusFlow, snapshot.list.single().statusFlow)

        snapshot.list.single().isCompleted = true
        snapshot.list.single().current = 128
        snapshot.list.single().duration = 6.0
        snapshot.list.single().length = 256
        assertEquals(false, original.list.single().isCompleted)
        assertEquals(0L, original.list.single().current)
        assertEquals(4.5, original.list.single().duration)
        assertEquals(null, original.list.single().length)
    }

    @Test
    fun databaseSnapshotsAreIndependentFromInputAndEachOther() = runBlocking {
        val database = MemoryDownloadPartStatesDB()
        val input = RangedParts(listOf(RangedPart(from = 0, to = 4095, current = 512)))
        database.setParts(5, input)
        input.list.single().current = 2048

        val first = database.getParts(5) as RangedParts
        assertEquals(512L, first.list.single().current)
        first.list.single().current = 4096
        first.list.single().to = 4095

        val second = database.getParts(5) as RangedParts
        assertNotSame(first.list.single(), second.list.single())
        assertEquals(512L, second.list.single().current)
    }

    @Test
    fun sixteenConcurrentWorkerSnapshotsCannotMutateStoredParts() = runBlocking {
        val database = MemoryDownloadPartStatesDB()
        database.setParts(99, RangedParts(listOf(RangedPart(from = 0, to = 16383, current = 32))))
        (1..16).map { worker ->
            async(Dispatchers.Default) {
                repeat(100) {
                    val snapshot = database.getParts(99) as RangedParts
                    snapshot.list.single().current = worker.toLong() * 100
                    assertEquals(32L, (database.getParts(99) as RangedParts).list.single().current)
                }
            }
        }.awaitAll()
        assertEquals(32L, (database.getParts(99) as RangedParts).list.single().current)
    }
}
