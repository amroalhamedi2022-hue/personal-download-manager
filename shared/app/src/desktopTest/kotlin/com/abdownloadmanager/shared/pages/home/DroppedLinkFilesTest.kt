package com.abdownloadmanager.shared.pages.home

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DroppedLinkFilesTest {
    private inline fun withTempDir(block: (java.io.File) -> Unit) {
        val dir = Files.createTempDirectory("hyperget-multidrop-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun joinsMultipleDroppedLinkListsInOrder() = withTempDir { dir ->
        val first = dir.resolve("first.txt").apply { writeText("https://one.example/file\n") }
        val second = dir.resolve("second.txt").apply { writeText("https://two.example/ملف") }
        val result = readDroppedLinkFiles(listOf(first, second, first))
        assertEquals(1, result.split("https://one.example/file").size - 1)
        assertTrue(result.contains("https://two.example/ملف"))
        assertTrue(result.indexOf("one.example") < result.indexOf("two.example"))
    }

    @Test fun ignoresDirectoriesMissingFilesAndBinaryContent() = withTempDir { dir ->
        val broken = dir.resolve("missing.txt")
        val binary = dir.resolve("binary.txt").apply { writeBytes(byteArrayOf(65, 0, 66)) }
        val valid = dir.resolve("valid.txt").apply { writeText("https://safe.example/a") }
        assertEquals("https://safe.example/a", readDroppedLinkFiles(listOf(dir, broken, binary, valid)))
    }

    @Test fun respectsTotalSizeBudgetWithoutReadingHugeFiles() = withTempDir { dir ->
        val large = dir.resolve("large.txt").apply { writeText("x".repeat(2048)) }
        val small = dir.resolve("small.txt").apply { writeText("https://ok.example") }
        val result = readDroppedLinkFiles(listOf(large, small), maxTotalBytes = 64)
        assertEquals("https://ok.example", result)
    }

    @Test fun limitsNumberOfFilesAndRejectsZeroBudgets() = withTempDir { dir ->
        val files = (0..18).map { n ->
            dir.resolve("f$n.txt").apply { writeText("https://item$n.example") }
        }
        val result = readDroppedLinkFiles(files, maxFiles = 16)
        assertTrue(result.contains("item15.example"))
        assertFalse(result.contains("item16.example"))
        assertEquals("", readDroppedLinkFiles(files, maxFiles = 0))
        assertEquals("", readDroppedLinkFiles(files, maxTotalBytes = 0))
    }
}
