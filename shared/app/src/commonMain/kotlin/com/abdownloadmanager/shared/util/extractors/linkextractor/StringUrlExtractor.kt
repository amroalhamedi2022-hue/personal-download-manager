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

    // Windows Internet Shortcut .url files may contain non-ASCII path segments
    // and long signed query strings. Extract the entire URL= value first;
    // the ASCII fallback regex would otherwise silently truncate Arabic URLs.
    private val internetShortcutLine = Regex("""^URL\\s*=\\s*(.+)$""", RegexOption.IGNORE_CASE)

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
                val shortcutUrl = internetShortcutLine.matchEntire(fullLine)
                    ?.groupValues?.get(1)?.trim()
                if (isDownloadUrl(fullLine)) {
                    sequenceOf(fullLine)
                } else if (shortcutUrl != null) {
                    // Reject a malformed shortcut as a whole; do not import a
                    // plausible-looking but truncated ASCII substring.
                    if (isDownloadUrl(shortcutUrl)) sequenceOf(shortcutUrl) else emptySequence()
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
