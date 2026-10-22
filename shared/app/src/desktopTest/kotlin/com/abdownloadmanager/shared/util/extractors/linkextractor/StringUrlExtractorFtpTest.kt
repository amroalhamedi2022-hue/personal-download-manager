package com.abdownloadmanager.shared.util.extractors.linkextractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringUrlExtractorFtpTest {
    @Test
    fun ftpUrlFromClipboardIsAcceptedRatherThanRejectedByHttpValidation() {
        val link = "ftp://127.0.0.1:2121/pub/archive.zip"
        assertEquals(listOf(link), StringUrlExtractor.extract(link))
        assertEquals(listOf(link), StringUrlExtractor.extract("Get file from $link please"))
    }

    @Test
    fun ftpCredentialLeaksAndUnsupportedPathsAreNotExtracted() {
        assertTrue(StringUrlExtractor.extract("ftp://user:password@host.test/file.zip").isEmpty())
        assertTrue(StringUrlExtractor.extract("ftp://host.test/").isEmpty())
        assertTrue(StringUrlExtractor.extract("ftp://host.test/file.zip?bad=query").isEmpty())
    }

    @Test
    fun oldHttpExtractionKeepsWorking() {
        val link = "https://example.com/download.zip"
        assertEquals(listOf(link), StringUrlExtractor.extract(link))
    }
}
