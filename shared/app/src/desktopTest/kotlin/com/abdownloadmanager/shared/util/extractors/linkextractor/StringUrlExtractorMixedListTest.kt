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

    @Test fun tenThousandMixedListLinksRetainOrderAndQueryIdentity() {
        // Bounded parser-only stress; this is NOT a 10k-item download/GUI test.
        val total = 10_000
        val input = buildString(total * 85) {
            repeat(total) { index ->
                if (index % 2 == 0) append("URL=")
                append("https://batch.example/file?id=")
                append(index)
                append("&token=")
                append(index % 17)
                append('\n')
            }
        }
        val startNanos = System.nanoTime()
        val links = StringUrlExtractor.extract(input)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        assertEquals(total, links.size)
        assertEquals("https://batch.example/file?id=0&token=0", links.first())
        assertEquals("https://batch.example/file?id=9999&token=3", links.last())
        assertEquals(total, links.toSet().size)
        println("HyperGet 10k mixed URL extractor (parser only): ${elapsedMs} ms")
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
