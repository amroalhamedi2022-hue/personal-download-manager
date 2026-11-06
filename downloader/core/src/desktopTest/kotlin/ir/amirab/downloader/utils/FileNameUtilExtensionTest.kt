package ir.amirab.downloader.utils

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNameUtilExtensionTest {
    @Test fun hiddenFileWithoutAnotherDotHasNoExtension() {
        assertNull(FileNameUtil.getExtensionOrNull(".env"))
        assertNull(FileNameUtil.getExtensionOrNull(".gitignore"))
        assertEquals(".env.txt", FileNameUtil.replaceExtension(".env", "txt"))
    }

    @Test fun hiddenFileWithActualSuffixKeepsExtension() {
        assertEquals("json", FileNameUtil.getExtensionOrNull(".config.json"))
        assertEquals(".config.txt", FileNameUtil.replaceExtension(".config.json", "txt"))
    }

    @Test fun trailingDotAndOrdinaryFileRemainSafe() {
        assertNull(FileNameUtil.getExtensionOrNull("report."))
        assertEquals("zip", FileNameUtil.getExtensionOrNull("archive.tar.zip"))
        assertEquals("report.pdf", FileNameUtil.replaceExtension("report.txt", "pdf"))
    }
    @Test
    fun duplicateHiddenFileKeepsFullNameInsteadOfEmptyStem() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-hidden-name-").toFile()
        try {
            val original = folder.resolve(".env").apply { writeText("existing") }
            val candidates = FileNameUtil.numberedIfExists(original).take(2).toList()
            assertEquals(listOf(".env_1", ".env_2"), candidates.map { it.name })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun duplicateHiddenFileWithRealExtensionPreservesItsExtension() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-hidden-extension-").toFile()
        try {
            val original = folder.resolve(".config.json").apply { writeText("existing") }
            folder.resolve(".config_1.json").writeText("also existing")
            val candidate = FileNameUtil.numberedIfExists(original).take(1).toList().single()
            assertEquals(".config_2.json", candidate.name)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun ordinaryMultiDotFilenameStillNumbersBeforeFinalExtension() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-numbered-").toFile()
        try {
            val original = folder.resolve("archive.tar.gz").apply { writeText("existing") }
            val candidate = FileNameUtil.numberedIfExists(original).take(1).toList().single()
            assertEquals("archive.tar_1.gz", candidate.name)
        } finally {
            folder.deleteRecursively()
        }
    }

}
