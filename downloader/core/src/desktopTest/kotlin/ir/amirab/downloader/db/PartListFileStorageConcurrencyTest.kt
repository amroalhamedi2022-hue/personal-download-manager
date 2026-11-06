package ir.amirab.downloader.db

import ir.amirab.downloader.part.RangedPart
import ir.amirab.downloader.part.RangedParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartListFileStorageConcurrencyTest {
    private fun snapshot(cursor: Long) = RangedParts(
        listOf(RangedPart(from = 0, to = 4095, current = cursor))
    )

    @Test
    fun simultaneousWritesForOneDownloadNeverCorruptSharedTempOrBackup() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-same-id-").toFile()
        try {
            val saver = TransactionalFileSaver(Json)
            val database = PartListFileStorage(folder, saver)
            (1..16).map { worker ->
                async(Dispatchers.Default) {
                    repeat(12) { iteration ->
                        database.setParts(77, snapshot((worker * 12 + iteration).toLong()))
                        val reread = database.getParts(77) as RangedParts
                        assertTrue(reread.list.single().current in 0L..4095L)
                    }
                }
            }.awaitAll()

            val file = database.getFileForId(77)
            assertTrue(file.isFile)
            assertNotNull(database.getParts(77))
            assertFalse(saver.getBakFile(file).exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun sixteenIndependentDownloadIdsKeepTheirOwnPersistedRanges() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-distinct-ids-").toFile()
        try {
            val database = PartListFileStorage(folder, TransactionalFileSaver(Json))
            (1L..16L).map { id ->
                async(Dispatchers.Default) {
                    repeat(8) { iteration ->
                        database.setParts(id, snapshot(id * 100 + iteration))
                    }
                }
            }.awaitAll()
            (1L..16L).forEach { id ->
                val persisted = database.getParts(id) as RangedParts
                assertEquals(id * 100 + 7, persisted.list.single().current)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun removalWaitsForInFlightWriteAndClearsRecoveryCopies() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-removal-").toFile()
        try {
            val saver = TransactionalFileSaver(Json)
            val database = PartListFileStorage(folder, saver)
            database.setParts(91, snapshot(1))
            (1..8).map { worker ->
                async(Dispatchers.Default) {
                    database.setParts(91, snapshot(worker.toLong()))
                }
            }.awaitAll()
            database.removeParts(91)
            assertNull(database.getParts(91))
            val file = database.getFileForId(91)
            assertFalse(file.exists())
            assertFalse(saver.getRecoveryFile(file).exists())
            assertFalse(saver.getBakFile(file).exists())
        } finally {
            folder.deleteRecursively()
        }
    }
}
