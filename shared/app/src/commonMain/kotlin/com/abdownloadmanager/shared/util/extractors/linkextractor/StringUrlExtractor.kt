package com.abdownloadmanager.shared.util.extractors.linkextractor

import com.abdownloadmanager.shared.util.extractors.Extractor
import ir.amirab.util.HttpUrlUtils
import ir.amirab.downloader.ftp.FtpDownloadCredentials

object StringUrlExtractor : Extractor<String, List<String>> {
    private fun isDownloadUrl(link: String): Boolean {
        if (HttpUrlUtils.isValidUrl(link)) return true
        return link.startsWith("ftp://", ignoreCase = true) &&
            runCatching { FtpDownloadCredentials(link).validateCredentials() }.isSuccess
    }

    private val urlRegex by lazy { Regex("""\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]""") }
    override fun extract(input: String): List<String> {
        // Process each line independently. Previously, if ANY line was an
        // exact URL, all embedded URLs elsewhere (e.g. Windows Internet
        // Shortcut lines "URL=https://...") were silently discarded.
        // Preserve full validated links (including spaces in FTP filenames)
        // rather than replacing them with truncated regex captures.
        return input.lineSequence()
            .flatMap { line ->
                val fullLine = line.trim()
                if (isDownloadUrl(fullLine)) {
                    sequenceOf(fullLine)
                } else {
                    urlRegex.findAll(line)
                        .map { it.value }
                        .filter { isDownloadUrl(it) }
                }
            }
            .distinct()
            .toList()
    }
}
