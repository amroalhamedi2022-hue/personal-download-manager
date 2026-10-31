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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class RecoverySnapshot(val value: Int)

class TransactionalRecoveryContractTest {
    @Test fun missingFileIsNotTheSameAsCorruptJson() {
        val dir = Files.createTempDirectory("hyperget-missing-corrupt-").toFile()
        try {
            val file = dir.resolve("state.json")
            val saver = TransactionalFileSaver(Json)
            assertNull(saver.readObject<RecoverySnapshot>(file))
            file.writeText("{broken")
            val error = assertFailsWith<StoredFileReadException> {
                saver.readObject<RecoverySnapshot>(file)
            }
            assertEquals(StoredFileReadFailureReason.MALFORMED_JSON, error.reason)
            assertEquals("{broken", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun validJsonWithWrongSchemaIsNotSilentlyIgnored() {
        val dir = Files.createTempDirectory("hyperget-wrong-schema-").toFile()
        try {
            val file = dir.resolve("state.json")
            file.writeText("""{"notValue":"oops"}""")
            val error = assertFailsWith<StoredFileReadException> {
                TransactionalFileSaver(Json).readObject<RecoverySnapshot>(file)
            }
            assertEquals(StoredFileReadFailureReason.UNSUPPORTED_SCHEMA, error.reason)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun corruptPrimaryRecoversCommittedBackupWithoutDestroyingEvidence() {
        val dir = Files.createTempDirectory("hyperget-recovery-").toFile()
        try {
            val file = dir.resolve("state.json")
            val saver = TransactionalFileSaver(Json)
            saver.writeObject(file, RecoverySnapshot(1))
            saver.writeObject(file, RecoverySnapshot(2))
            assertTrue(saver.getRecoveryFile(file).isFile)
            file.writeText("{corrupted")
            assertEquals(RecoverySnapshot(1), saver.readObject<RecoverySnapshot>(file))
            assertEquals(RecoverySnapshot(1), saver.readObject<RecoverySnapshot>(file))
            assertTrue(dir.listFiles().orEmpty().any {
                it.name.startsWith("state.json.corrupt-") && it.readText() == "{corrupted"
            })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun interruptedReplaceCanRecoverLastCommittedSnapshot() {
        val dir = Files.createTempDirectory("hyperget-replacement-").toFile()
        try {
            val file = dir.resolve("state.json")
            TransactionalFileSaver(Json).writeObject(file, RecoverySnapshot(1))
            val failing = TransactionalFileSaver(Json) { source, destination ->
                if (destination == file) {
                    destination.delete()
                    throw IOException("simulated interrupted replace")
                }
                source.tryAtomicMove(destination)
            }
            assertFailsWith<IOException> { failing.writeObject(file, RecoverySnapshot(2)) }
            assertEquals(RecoverySnapshot(1), TransactionalFileSaver(Json).readObject<RecoverySnapshot>(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun explicitDeleteCannotResurrectOldRecordFromBackup() {
        val dir = Files.createTempDirectory("hyperget-delete-").toFile()
        try {
            val file = dir.resolve("state.json")
            val saver = TransactionalFileSaver(Json)
            saver.writeObject(file, RecoverySnapshot(1))
            saver.writeObject(file, RecoverySnapshot(2))
            saver.delete(file)
            assertFalse(file.exists())
            assertFalse(saver.getRecoveryFile(file).exists())
            assertFalse(saver.getBakFile(file).exists())
            assertNull(saver.readObject<RecoverySnapshot>(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun queueEnumerationIncludesRecoverableBackupButNeverHidesCorruption() = runBlocking {
        val dir = Files.createTempDirectory("hyperget-queues-recovery-").toFile()
        try {
            val saver = TransactionalFileSaver(Json)
            val storage = DownloadQueueFileStorageDatabase(saver, dir)
            storage.addQueue(QueueModel(id = 17L, name = "old"))
            storage.updateQueue(QueueModel(id = 17L, name = "new"))
            dir.resolve("17.json").delete()
            assertEquals(listOf(17L), storage.getAllQueueIds())
            assertEquals("old", storage.getAllQueues().single().name)
            dir.resolve("18.json").writeText("{broken")
            assertFailsWith<StoredFileReadException> {
                storage.getAllQueues()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
