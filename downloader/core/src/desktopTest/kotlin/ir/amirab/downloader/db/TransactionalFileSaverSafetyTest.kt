package ir.amirab.downloader.db

import ir.amirab.util.tryAtomicMove
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
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
            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(file)
            }
            assertEquals(StoredFileReadFailureReason.MALFORMED_JSON, failure.reason)
            assertEquals("{not-json", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun validJsonWithUnsupportedSchemaIsDistinctFromMalformedJson() {
        val folder = Files.createTempDirectory("transactional-schema-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            file.writeText("{\"unexpected\":\"value\"}")

            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(file)
            }

            assertEquals(StoredFileReadFailureReason.UNSUPPORTED_SCHEMA, failure.reason)
            assertEquals("{\"unexpected\":\"value\"}", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun ioFailureIsDistinctFromMissingAndInvalidData() {
        val folder = Files.createTempDirectory("transactional-io-").toFile()
        val directoryInPlaceOfFile = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            assertTrue(directoryInPlaceOfFile.mkdir())

            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(directoryInPlaceOfFile)
            }

            assertEquals(StoredFileReadFailureReason.IO, failure.reason)
            assertTrue(directoryInPlaceOfFile.isDirectory)
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
    fun forcedProcessTerminationKeepsLastCommittedSnapshotReadable() = runBlocking {
        val folder = Files.createTempDirectory("transactional-process-kill-").toFile()
        val file = folder.resolve("state.json")
        val marker = folder.resolve("commit-ready.marker")
        val childLog = folder.resolve("child.log")
        val saver = TransactionalFileSaver(Json)
        var process: Process? = null
        try {
            saver.writeObject(file, Snapshot(1))
            val classpath = testProcessClasspath()
            assertTrue(classpath.isNotBlank())

            val childProcess = ProcessBuilder(
                javaExecutable().absolutePath,
                "-cp",
                classpath,
                TransactionalFileSaverCrashWriter::class.java.name,
                file.absolutePath,
                marker.absolutePath,
            )
                .redirectErrorStream(true)
                .redirectOutput(childLog)
                .start()
            process = childProcess

            withTimeout(20_000) {
                while (!marker.exists()) {
                    check(childProcess.isAlive) {
                        "Crash writer exited before the commit barrier: ${childLog.takeIf(File::exists)?.readText()}"
                    }
                    delay(20)
                }
            }

            childProcess.destroyForcibly()
            assertTrue(childProcess.waitFor(10, TimeUnit.SECONDS))
            assertEquals(Snapshot(1), TransactionalFileSaver(Json).readObject<Snapshot>(file))
            assertEquals("{\"value\":2}", saver.getBakFile(file).readText())
        } finally {
            process?.destroyForcibly()
            folder.deleteRecursively()
        }
        Unit
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
            Unit
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
            Unit
        } finally {
            folder.deleteRecursively()
        }
    }

    @Serializable
    private data class Snapshot(val value: Int)

    private fun javaExecutable(): File {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        return File(File(System.getProperty("java.home"), "bin"), executable)
    }

    private fun testProcessClasspath(): String {
        val entries = linkedSetOf<String>()
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filterTo(entries) { it.isNotBlank() }

        var loader: ClassLoader? = TransactionalFileSaverSafetyTest::class.java.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) {
                loader.urLs
                    .filter { it.protocol == "file" }
                    .mapTo(entries) { File(it.toURI()).absolutePath }
            }
            loader = loader.parent
        }

        listOf(
            TransactionalFileSaverCrashWriter::class.java,
            TransactionalFileSaver::class.java,
            Json::class.java,
            FileSystem::class.java,
            Unit::class.java,
        ).mapNotNullTo(entries) { type ->
            type.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }?.absolutePath
        }
        return entries.joinToString(File.pathSeparator)
    }
}

/** Child-process entry point used to stop a transactional write exactly before primary commit. */
object TransactionalFileSaverCrashWriter {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2)
        val target = File(args[0]).canonicalFile
        val marker = File(args[1])
        val staging = File("$target.tmp").canonicalFile
        val saver = TransactionalFileSaver(Json) { source, destination ->
            if (source.canonicalFile == staging && destination.canonicalFile == target) {
                FileOutputStream(marker).use { output ->
                    output.write("ready".encodeToByteArray())
                    output.fd.sync()
                }
                while (true) {
                    Thread.sleep(1_000)
                }
            }
            source.tryAtomicMove(destination)
        }
        saver.writeText(target, "{\"value\":2}")
    }
}
