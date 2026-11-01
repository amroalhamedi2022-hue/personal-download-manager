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

    @Test fun limitsInspectionOfInvalidPathsAsWellAsAcceptedFiles() = withTempDir { dir ->
        val invalid = (0..31).map { n -> dir.resolve("missing$n.txt") }
        val valid = dir.resolve("last.txt").apply { writeText("https://never.example") }
        assertEquals("", readDroppedLinkFiles(invalid + valid, maxFiles = 16))
        assertEquals(
            "https://never.example",
            readDroppedLinkFiles(invalid.take(2) + valid, maxFiles = 3)
        )
    }

    @Test fun acceptsUtf16LittleAndBigEndianBomAndUtf8Bom() = withTempDir { dir ->
        val le = dir.resolve("old-notepad-LE.txt").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                "https://utf16le.example/ملف".toByteArray(Charsets.UTF_16LE))
        }
        val be = dir.resolve("old-notepad-BE.txt").apply {
            writeBytes(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
                "https://utf16be.example/file".toByteArray(Charsets.UTF_16BE))
        }
        val utf8bom = dir.resolve("utf8bom.txt").apply {
            writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "https://utf8.example".toByteArray(Charsets.UTF_8))
        }
        val result = readDroppedLinkFiles(listOf(le, be, utf8bom))
        assertTrue(result.contains("https://utf16le.example/ملف"))
        assertTrue(result.contains("https://utf16be.example/file"))
        assertTrue(result.contains("https://utf8.example"))
        assertFalse(result.contains("\uFEFF"))
    }

    @Test fun rejectsOddUtf16AndDecodedNulContent() = withTempDir { dir ->
        val malformed = dir.resolve("odd.txt").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x68))
        }
        val nul = dir.resolve("nul-utf16.txt").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                "a\u0000b".toByteArray(Charsets.UTF_16LE))
        }
        val valid = dir.resolve("ok.txt").apply { writeText("https://valid.example") }
        assertEquals("https://valid.example", readDroppedLinkFiles(listOf(malformed, nul, valid)))
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
