package com.abdownloadmanager.shared.pages.home

import java.io.File

/**
 * Reads small dropped link-list files only. Returns text to the EXISTING link
 * extractor, so duplicate links are filtered by the same import flow.
 *
 * Untrusted drag sources can contain directories, unreadable/binary files or
 * thousands of paths. Bound both count and total I/O; never follow directory
 * trees, log contents, or accidentally import links from large documents.
 */
fun readDroppedLinkFiles(
    files: List<File>,
    maxFiles: Int = 16,
    maxTotalBytes: Long = 1024L * 1024L,
): String {
    if (maxFiles <= 0 || maxTotalBytes <= 0L) return ""
    val seen = HashSet<String>()
    var inspectedCount = 0
    var remaining = maxTotalBytes
    return buildString {
        for (file in files) {
            // Count ALL inspected paths, including invalid/missing/duplicate
            // entries. Otherwise a drag containing 10k unreadable paths can
            // block the Compose UI despite maxFiles = 16.
            if (inspectedCount >= maxFiles || remaining <= 0L) break
            inspectedCount++
            if (!seen.add(file.absolutePath)) continue
            val decoded = runCatching {
                if (!file.isFile || !file.canRead()) return@runCatching null
                val size = file.length()
                if (size <= 0L || size > remaining) return@runCatching null
                // Files may change after metadata inspection. Use a bounded
                // stream to prevent racing a growing/untrusted file.
                val raw = file.inputStream().use { input ->
                    val maxRead = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    input.readNBytes(maxRead + 1)
                }
                if (raw.size.toLong() > remaining || raw.isEmpty()) return@runCatching null
                // Old Windows Notepad .txt/.url lists are sometimes UTF-16.
                // Accept UTF-16 ONLY with an explicit BOM, not arbitrary binary
                // data containing zero bytes. UTF-8 BOM is also supported.
                val text = when {
                    raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte() -> {
                        if ((raw.size - 2) % 2 != 0) return@runCatching null
                        raw.copyOfRange(2, raw.size).toString(Charsets.UTF_16LE)
                    }
                    raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte() -> {
                        if ((raw.size - 2) % 2 != 0) return@runCatching null
                        raw.copyOfRange(2, raw.size).toString(Charsets.UTF_16BE)
                    }
                    else -> {
                        if (raw.any { it == 0.toByte() }) return@runCatching null
                        val prefix = if (raw.size >= 3 &&
                            raw[0] == 0xEF.toByte() &&
                            raw[1] == 0xBB.toByte() &&
                            raw[2] == 0xBF.toByte()) 3 else 0
                        raw.copyOfRange(prefix, raw.size).toString(Charsets.UTF_8)
                    }
                }
                if ('\u0000' in text) return@runCatching null
                text to raw.size.toLong()
            }.getOrNull() ?: continue
            val (text, bytesRead) = decoded
            if (text.isBlank()) continue
            // Charge the RAW byte count to the budget, not the UTF-8
            // re-encoded length (which can differ for UTF-16/BOM files).
            remaining -= bytesRead
            if (isNotEmpty()) append('\n')
            append(text)
        }
    }
}
