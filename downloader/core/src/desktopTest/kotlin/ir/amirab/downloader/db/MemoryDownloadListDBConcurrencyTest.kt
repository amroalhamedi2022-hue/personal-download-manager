package ir.amirab.downloader.db

import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryDownloadListDBConcurrencyTest {
    private fun item(id: Long) = HttpDownloadItem(
        link = "https://example.test/files/$id.bin",
        id = id,
        folder = ".",
        name = "$id.bin",
    )

    @Test
    fun sixteenConcurrentAddsKeepAllUniqueDownloadIds() = runBlocking {
        val database = MemoryDownloadListDB()
        (1L..16L).map { id ->
            async(Dispatchers.Default) {
                database.add(item(id))
                repeat(50) {
                    assertNotNull(database.getById(id))
                    database.getAll()
                }
            }
        }.awaitAll()

        val all = database.getAll()
        assertEquals(16, all.size)
        assertEquals(16, all.map { it.id }.toSet().size)
        assertEquals(16L, database.getLastId())
    }

    @Test
    fun concurrentDuplicateInsertHasExactlyOneWinner() = runBlocking {
        val database = MemoryDownloadListDB()
        val results = (1..16).map {
            async(Dispatchers.Default) {
                runCatching { database.add(item(7)) }.isSuccess
            }
        }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(listOf(7L), database.getAll().map { it.id })
    }

    @Test
    fun concurrentRemovalsDoNotCorruptPreviouslyReturnedSnapshots() = runBlocking {
        val database = MemoryDownloadListDB()
        (1L..16L).forEach { database.add(item(it)) }
        val before = database.getAll()

        (1L..16L).map { id ->
            async(Dispatchers.Default) {
                database.removeById(id)
                database.removeById(id)
            }
        }.awaitAll()

        assertTrue(database.getAll().isEmpty())
        assertEquals(16, before.size)
        assertEquals(-1L, database.getLastId())
    }
}
