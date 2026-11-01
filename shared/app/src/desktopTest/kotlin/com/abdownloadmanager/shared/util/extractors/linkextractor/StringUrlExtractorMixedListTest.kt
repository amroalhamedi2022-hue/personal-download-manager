package com.abdownloadmanager.shared.util.extractors.linkextractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringUrlExtractorMixedListTest {
    @Test fun retainsDirectAndWindowsShortcutUrlsInOneBatch() {
        val input = """
            [InternetShortcut]
            URL=https://shortcut.example/archive.zip
            https://plain.example/backup.tar
            More: https://inline.example/readme.txt
        """.trimIndent()
        assertEquals(
            listOf(
                "https://shortcut.example/archive.zip",
                "https://plain.example/backup.tar",
                "https://inline.example/readme.txt",
            ),
            StringUrlExtractor.extract(input)
        )
    }

    @Test fun preservesFullFtpFilenameWithSpacesInMixedInput() {
        val ftp = "ftp://127.0.0.1:2121/pub/photo [final] (2).bin"
        val input = "$ftp\nURL=https://shortcut.example/file.zip"
        assertEquals(
            listOf(ftp, "https://shortcut.example/file.zip"),
            StringUrlExtractor.extract(input)
        )
    }

    @Test fun removesDuplicatesAcrossPlainAndEmbeddedLines() {
        val link = "https://same.example/file.zip"
        assertEquals(
            listOf(link),
            StringUrlExtractor.extract("$link\nURL=$link\nDownload from $link")
        )
    }

    @Test fun doesNotExtractUnsupportedOrCredentialBearingFtpLinks() {
        val input = """
            ftp://user:password@host.test/private.zip
            ftp://host.test/
            https://valid.example/safe.zip
        """.trimIndent()
        val result = StringUrlExtractor.extract(input)
        assertEquals(listOf("https://valid.example/safe.zip"), result)
        assertFalse(result.any { it.contains("password") })
        assertTrue(result.none { it.startsWith("ftp://") })
    }
}
