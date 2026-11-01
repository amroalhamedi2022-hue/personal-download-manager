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
    var readCount = 0
    var remaining = maxTotalBytes
    return buildString {
        for (file in files) {
            if (readCount >= maxFiles || remaining <= 0L) break
            if (!seen.add(file.absolutePath)) continue
            val text = runCatching {
                if (!file.isFile || !file.canRead()) return@runCatching null
                val size = file.length()
                if (size <= 0L || size > remaining) return@runCatching null
                // Files may change after metadata inspection. Use a bounded
                // stream to prevent racing a growing/untrusted file.
                val raw = file.inputStream().use { input ->
                    val maxRead = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    input.readNBytes(maxRead + 1)
                }
                if (raw.size.toLong() > remaining || raw.isEmpty() ||
                    raw.any { it == 0.toByte() }) return@runCatching null
                raw.toString(Charsets.UTF_8)
            }.getOrNull() ?: continue
            if (text.isBlank()) continue
            val length = text.toByteArray(Charsets.UTF_8).size.toLong()
            if (length > remaining) continue
            remaining -= length
            readCount++
            if (isNotEmpty()) append('\n')
            append(text)
        }
    }
}
