package ir.amirab.downloader.db

import ir.amirab.downloader.part.RangedPart
import ir.amirab.downloader.part.RangedParts
import ir.amirab.util.tryAtomicMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartListFileStorageClearBarrierTest {
    private fun parts(cursor: Long) = RangedParts(listOf(RangedPart(from = 0, to = 4095, current = cursor)))

    @Test
    fun clearWaitsForCommittedInFlightWriterInsteadOfDeletingItsTempFile() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-clear-race-").toFile()
        val enteringCommit = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val stallOnce = AtomicBoolean(true)
        try {
            val saver = TransactionalFileSaver(Json) { from, to ->
                if (to.name == "7.json" && stallOnce.compareAndSet(true, false)) {
                    enteringCommit.countDown()
                    if (!releaseCommit.await(15, TimeUnit.SECONDS)) {
                        throw IOException("Timed out waiting for the controlled writer")
                    }
                }
                from.tryAtomicMove(to)
            }
            val database = PartListFileStorage(folder, saver)
            val writing = async(Dispatchers.IO) { database.setParts(7, parts(300)) }
            assertTrue(withContext(Dispatchers.IO) { enteringCommit.await(15, TimeUnit.SECONDS) })

            val clearing = async(Dispatchers.Default) { database.clear() }
            delay(100)
            assertFalse(clearing.isCompleted, "Clear must wait for a writer holding a transactional .tmp")
            releaseCommit.countDown()
            withTimeout(15_000) {
                writing.await()
                clearing.await()
            }
            assertNull(database.getParts(7))
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally {
            releaseCommit.countDown()
            folder.deleteRecursively()
        }
    }

    @Test
    fun sixteenIndependentWritersCanFinishBeforeExclusiveClear() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-clear-sixteen-").toFile()
        try {
            val database = PartListFileStorage(folder, TransactionalFileSaver(Json))
            (1L..16L).map { id ->
                async(Dispatchers.Default) {
                    repeat(8) { database.setParts(id, parts(id * 100 + it)) }
                }
            }.awaitAll()
            database.clear()
            (1L..16L).forEach { assertNull(database.getParts(it)) }
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun concurrentClearRequestsLeaveNoPrimaryOrRecoveryFiles() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-part-clear-many-").toFile()
        try {
            val database = PartListFileStorage(folder, TransactionalFileSaver(Json))
            (1L..16L).forEach { database.setParts(it, parts(it)) }
            (1..16).map { async(Dispatchers.Default) { database.clear() } }.awaitAll()
            assertTrue(folder.listFiles().orEmpty().isEmpty())
            withTimeout(15_000) {
                database.setParts(42, parts(42))
                assertTrue(database.getFileForId(42).isFile)
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
