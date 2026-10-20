package ir.amirab.downloader.db

import ir.amirab.util.tryAtomicMove
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionalFileSaverSafetyTest {
    @Test
    fun missingFileIsDistinctFromCorruptFile() {
        val folder = Files.createTempDirectory("transactional-missing-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            assertNull(saver.readObject<Snapshot>(file))

            file.writeText("{not-json")
            assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(file)
            }
            assertEquals("{not-json", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptPrimaryIsQuarantinedAndRecoveredFromLastCommittedBackup() {
        val folder = Files.createTempDirectory("transactional-recovery-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            saver.writeObject(file, Snapshot(1))
            saver.writeObject(file, Snapshot(2))
            file.writeText("{broken")

            assertEquals(Snapshot(1), saver.readObject<Snapshot>(file))
            assertEquals(Snapshot(1), saver.readObject<Snapshot>(file))
            assertTrue(
                folder.listFiles().orEmpty().any {
                    it.name.startsWith("state.json.corrupt-") && it.readText() == "{broken"
                }
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun failedReplacementCanRecoverPreviousCommittedSnapshot() {
        val folder = Files.createTempDirectory("transactional-commit-failure-").toFile()
        val file = folder.resolve("state.json")
        try {
            TransactionalFileSaver(Json).writeObject(file, Snapshot(1))
            val failingSaver = TransactionalFileSaver(Json) { source, destination ->
                if (destination == file) {
                    destination.delete()
                    throw IOException("injected replacement failure")
                }
                source.tryAtomicMove(destination)
            }

            assertFailsWith<IOException> {
                failingSaver.writeObject(file, Snapshot(2))
            }

            assertEquals(Snapshot(1), TransactionalFileSaver(Json).readObject<Snapshot>(file))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun explicitDeleteRemovesPrimaryStagingAndRecoveryFiles() {
        val folder = Files.createTempDirectory("transactional-delete-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            saver.writeObject(file, Snapshot(1))
            saver.writeObject(file, Snapshot(2))
            saver.getBakFile(file).writeText("uncommitted")

            saver.delete(file)

            assertNull(saver.readObject<Snapshot>(file))
            assertTrue(!file.exists())
            assertTrue(!saver.getBakFile(file).exists())
            assertTrue(!saver.getRecoveryFile(file).exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptDownloadRecordIsReportedInsteadOfFilteredOut() = runBlocking {
        val folder = Files.createTempDirectory("download-list-corrupt-").toFile()
        val saver = TransactionalFileSaver(Json)
        try {
            val storage = DownloadListFileStorage(folder, saver)
            folder.resolve("2.json").writeText("{broken")

            assertFailsWith<StoredFileReadException> {
                storage.getAll()
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptQueueRecordIsReportedInsteadOfFilteredOut() = runBlocking {
        val folder = Files.createTempDirectory("queue-list-corrupt-").toFile()
        val saver = TransactionalFileSaver(Json)
        try {
            folder.resolve("7.json").writeText("{broken")

            assertFailsWith<StoredFileReadException> {
                DownloadQueueFileStorageDatabase(saver, folder).getAllQueues()
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Serializable
    private data class Snapshot(val value: Int)
}
