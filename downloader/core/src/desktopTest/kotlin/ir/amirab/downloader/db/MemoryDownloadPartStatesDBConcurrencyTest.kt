package ir.amirab.downloader.db

import ir.amirab.downloader.part.RangedParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryDownloadPartStatesDBConcurrencyTest {
    @Test
    fun concurrentSixteenWorkersKeepIndependentPartStates() = runBlocking {
        val db = MemoryDownloadPartStatesDB()
        (1L..16L).map { id ->
            async(Dispatchers.Default) {
                repeat(200) {
                    db.setParts(id, RangedParts(emptyList()))
                    assertEquals(RangedParts(emptyList()), db.getParts(id))
                }
            }
        }.awaitAll()
        (1L..16L).forEach { id ->
            assertEquals(RangedParts(emptyList()), db.getParts(id))
        }
    }

    @Test
    fun concurrentRemovalAndClearNeverLeaveStaleEntries() = runBlocking {
        val db = MemoryDownloadPartStatesDB()
        (1L..16L).map { id ->
            async(Dispatchers.Default) {
                repeat(100) {
                    db.setParts(id, RangedParts(emptyList()))
                    db.removeParts(id)
                }
            }
        }.awaitAll()
        db.clear()
        (1L..16L).forEach { id -> assertNull(db.getParts(id)) }
    }
}
