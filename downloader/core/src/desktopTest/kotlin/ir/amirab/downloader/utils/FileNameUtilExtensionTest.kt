package ir.amirab.downloader.utils

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
}
