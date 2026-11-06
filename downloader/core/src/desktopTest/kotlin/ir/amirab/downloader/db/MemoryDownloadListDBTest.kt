package ir.amirab.downloader.db

import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    @Test
    fun concurrentNotificationsPublishAConsistentSnapshot() = runBlocking {
        val db = MemoryDownloadListDB()
        (1..16).map {
            async(Dispatchers.Default) {
                db.onDbUpdate()
            }
        }.awaitAll()
        assertEquals(db.getAll(), db.allAsFlow().first())
    }

    @Test
    fun repeatedNotificationsKeepLatestReplayAvailable() = runBlocking {
        val db = MemoryDownloadListDB()
        repeat(32) { db.onDbUpdate() }
        assertEquals(emptyList(), db.allAsFlow().first())
    }

    @Test
    fun newSubscribersReceiveInitialEmptySnapshotWithoutWaitingForMutation() = runBlocking {
        val db = MemoryDownloadListDB()
        val snapshot = withTimeout(2_000) { db.allAsFlow().first() }
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun parallelMutationsAndNotificationsCannotLeaveStaleReplay() = runBlocking {
        val db = MemoryDownloadListDB()
        (1L..16L).map { id ->
            async(Dispatchers.Default) {
                db.add(HttpDownloadItem(
                    link = "https://example.test/$id",
                    id = id,
                    folder = ".",
                    name = "$id.bin",
                ))
                db.onDbUpdate()
            }
        }.awaitAll()
        assertEquals((1L..16L).toList(), db.allAsFlow().first().map { it.id }.sorted())
    }

}
