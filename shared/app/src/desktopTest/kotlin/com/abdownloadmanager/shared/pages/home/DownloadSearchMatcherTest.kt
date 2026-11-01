package com.abdownloadmanager.shared.pages.home

import ir.amirab.downloader.monitor.CompletedDownloadItemState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadSearchMatcherTest {
    private fun item(
        name: String = "lecture-notes.pdf",
        url: String = "https://example.org/downloads/lecture-notes.pdf?chapter=5",
    ) = CompletedDownloadItemState(
        id = 1L,
        folder = "/downloads",
        name = name,
        downloadLink = url,
        contentLength = 2048L,
        saveLocation = name,
        dateAdded = 1L,
        startTime = 2L,
        completeTime = 3L,
    )

    @Test fun matchesFileNameWithoutCaseOrWhitespaceSensitivity() {
        assertTrue(matchesDownloadSearch(item(), "  LECTURE-NOTES  "))
        assertTrue(matchesDownloadSearch(item(), "pdf"))
        assertFalse(matchesDownloadSearch(item(), "unrelated"))
    }

    @Test fun findsCompletedDownloadsByUrlDomainAndQuery() {
        assertTrue(matchesDownloadSearch(item(), "EXAMPLE.ORG"))
        assertTrue(matchesDownloadSearch(item(), "chapter=5"))
        assertFalse(matchesDownloadSearch(item(), "chapter=6"))
    }

    @Test fun supportsMixedArabicNamesAndLatinUrls() {
        val entry = item(
            name = "قصة-مترجمة.pdf",
            url = "https://books.example/arabic/قصص/novella?lang=ar"
        )
        assertTrue(matchesDownloadSearch(entry, "قصة"))
        assertTrue(matchesDownloadSearch(entry, "NOVELLA"))
        assertTrue(matchesDownloadSearch(entry, "قصص"))
    }

    @Test fun blankQueryShowsAllDownloads() {
        assertTrue(matchesDownloadSearch(item(), ""))
        assertTrue(matchesDownloadSearch(item(), "  \t  "))
    }
}
