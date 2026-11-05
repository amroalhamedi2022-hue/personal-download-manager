package ir.amirab.downloader

import ir.amirab.util.FileNameValidator
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isWindows
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsFilenameValidationTest {
    @Test
    fun windowsRejectsReservedDevicesIncludingExtensionsAndSuperscriptPorts() {
        if (!Platform.isWindows()) return
        listOf(
            "CON", "NUL", "prn", "AUX.txt", "con .pdf", "COM1", "COM9.bin",
            "LPT1.txt", "lpt².log", "com¹.txt", "CLOCK$", "CONIN$.bin",
            "CONOUT$", "nul.backup",
        ).forEach { filename ->
            assertFalse(FileNameValidator.isValidFileName(filename), "Reserved Windows filename must be rejected")
        }
    }

    @Test
    fun windowsRejectsInvalidCharsTrailingDotsSpacesAndOversizedFileComponents() {
        if (!Platform.isWindows()) return
        listOf(
            ".", "..", "report.", "report ", "file:name.txt",
            "folder\\file", "folder/file", "a|b", "a?b", "a*b",
            "a\"b", "a<b", "a>b", "a\u0001b", "a\u0000b",
            "x".repeat(256),
        ).forEach { filename ->
            assertFalse(FileNameValidator.isValidFileName(filename), "Unsafe Windows output name must be rejected")
        }
    }

    @Test
    fun windowsAcceptsLegitimateUtf8RtlNamesAndLeadingDots() {
        if (!Platform.isWindows()) return
        listOf(
            "HyperGet Setup.exe",
            "تقرير_التحميل_٢٠٢٦.pdf",
            "Sherlock Holmes — Episode 1.epub",
            ".gitignore",
            "Archive.backup.2026",
            "a".repeat(250),
        ).forEach { filename ->
            assertTrue(FileNameValidator.isValidFileName(filename), "Valid Unicode Windows filename should remain accepted")
        }
    }
}
