package ir.amirab.downloader.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmptyFileCreatorResumeSafetyTest {
    private val unlimitedDisk = object : IDiskStat {
        override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
    }

    @Test
    fun extendingExistingSparseDestinationMustPreserveCommittedPrefix() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-preserve-resume-").toFile()
        val sparseCalls = AtomicInteger()
        val fakeSparse = object : ISparseFile {
            override fun canWeCreateSparseFile(file: File): Boolean = true
            override fun createSparseFile(file: File): Boolean {
                sparseCalls.incrementAndGet()
                return true
            }
        }
        try {
            val target = folder.resolve("resume.bin")
            val committed = byteArrayOf(9, 0, 7, 11, -1, 32)
            target.writeBytes(committed)
            val creator = EmptyFileCreator(unlimitedDisk, { true }, fakeSparse)

            creator.prepareFile(target, 4096L) {}

            assertEquals(4096L, target.length())
            val recovered = ByteArray(committed.size)
            RandomAccessFile(target, "r").use { it.readFully(recovered) }
            assertContentEquals(committed, recovered)
            assertEquals(0, sparseCalls.get(), "Existing resumable output must never be recreated")
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun sixteenIndependentResumesRetainCommittedBytes() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-16-sparse-resumes-").toFile()
        val creationCalls = AtomicInteger()
        val sparse = object : ISparseFile {
            override fun canWeCreateSparseFile(file: File): Boolean = true
            override fun createSparseFile(file: File): Boolean {
                creationCalls.incrementAndGet()
                return true
            }
        }
        try {
            val creator = EmptyFileCreator(unlimitedDisk, { true }, sparse)
            (1..16).map { worker ->
                async(Dispatchers.Default) {
                    val target = folder.resolve("part-$worker.bin")
                    val prefix = byteArrayOf(worker.toByte(), (worker + 12).toByte())
                    target.writeBytes(prefix)
                    creator.prepareFile(target, 1024L + worker) {}
                    val actualPrefix = ByteArray(prefix.size)
                    RandomAccessFile(target, "r").use { it.readFully(actualPrefix) }
                    assertContentEquals(prefix, actualPrefix)
                    assertEquals(1024L + worker, target.length())
                }
            }.awaitAll()
            assertEquals(0, creationCalls.get())
        } finally {
            folder.deleteRecursively()
        }
        Unit
    }

    @Test
    fun concurrentCreationOfForeignFileCannotBeOverwrittenBySparseFallback() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-sparse-create-race-").toFile()
        val foreign = byteArrayOf(99, 2, 3, 4)
        val fakeSparse = object : ISparseFile {
            override fun canWeCreateSparseFile(file: File): Boolean = true
            override fun createSparseFile(file: File): Boolean {
                // Deterministic race: another writer publishes between exists()
                // and our createNewFile() fallback.
                file.writeBytes(foreign)
                return false
            }
        }
        try {
            val target = folder.resolve("foreign.bin")
            val creator = EmptyFileCreator(unlimitedDisk, { true }, fakeSparse)
            assertFailsWith<IOException> { creator.prepareFile(target, 4096L) {} }
            assertContentEquals(foreign, target.readBytes())
        } finally {
            folder.deleteRecursively()
        }
    }
}
