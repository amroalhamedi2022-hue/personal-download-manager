package ir.amirab.downloader.utils

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyFileCreatorRelativePathTest {
    @Test
    fun parentlessRelativeFileCanBeCreatedAndFilledSafely() = runBlocking {
        val target = File("hyperget-relative-space-${System.nanoTime()}.bin")
        val inspections = AtomicInteger()
        val creator = EmptyFileCreator(
            diskStat = object : IDiskStat {
                override fun getRemainingSpace(path: File): Long {
                    assertTrue(path.isAbsolute)
                    assertEquals(target.absoluteFile.parentFile, path)
                    inspections.incrementAndGet()
                    return Long.MAX_VALUE
                }
            },
            useSparseFile = { false },
        )
        try {
            creator.prepareFile(target, 1024L) {}
            assertEquals(1024L, target.length())
            assertTrue(inspections.get() >= 2)
        } finally {
            target.delete()
        }
    }

    @Test
    fun extendingRelativeExistingFilePreservesPreviouslyDownloadedPrefix() = runBlocking {
        val target = File("hyperget-relative-resume-${System.nanoTime()}.bin")
        val existing = byteArrayOf(1, 10, -1, 0, 42)
        val creator = EmptyFileCreator(
            diskStat = object : IDiskStat {
                override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
            },
            useSparseFile = { false },
        )
        try {
            target.writeBytes(existing)
            creator.prepareFile(target, 2048L) {}
            assertEquals(2048L, target.length())
            val prefix = ByteArray(existing.size)
            RandomAccessFile(target, "r").use { it.readFully(prefix) }
            assertContentEquals(existing, prefix)
        } finally {
            target.delete()
        }
    }
}
