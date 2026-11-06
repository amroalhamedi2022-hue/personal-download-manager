package ir.amirab.downloader.db

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryDownloadListDBTest {
    @Test
    fun removingUnknownIdFromEmptyDatabaseIsSafe() = runBlocking {
        val db = MemoryDownloadListDB()
        db.removeById(42L)
        assertTrue(db.getAll().isEmpty())
        assertEquals(-1L, db.getLastId())
    }

    @Test
    fun repeatedRemovalOfUnknownIdIsIdempotent() = runBlocking {
        val db = MemoryDownloadListDB()
        repeat(3) { db.removeById(Long.MAX_VALUE) }
        db.onDbUpdate()
        assertTrue(db.getAll().isEmpty())
    }
}
