package ir.amirab.downloader.connection.response.headers

import ir.amirab.downloader.connection.response.HttpResponseInfo

data class ContentRangeValue(
    val range: LongRange?,
    val fullSize: Long?,
)

/**
 * Parse the HTTP Content-Range header without trusting malformed server bounds.
 *
 * Accept standard "bytes 0-9/10", the 416 unsatisfied-range form, and legacy
 * "0-9/10" form used by some servers. Unknown total size ("*") remains
 * supported for satisfied byte ranges; it must never mask an invalid range.
 *
 * Invalid values return null instead of creating negative, reversed or
 * out-of-file ranges that could be used for partial download writes.
 */
fun HttpResponseInfo.getContentRange(): ContentRangeValue? {
    val raw = responseHeaders["content-range"]?.trim() ?: return null
    if (raw.isEmpty() || '\r' in raw || '\n' in raw) return null

    val payload = if (raw.startsWith("bytes", ignoreCase = true)) {
        val suffix = raw.substring(5)
        if (suffix.isEmpty() || !suffix.first().isWhitespace()) return null
        suffix.trimStart()
    } else {
        // Preserve compatibility with servers omitting the bytes unit.
        raw
    }

    val fields = payload.split('/', limit = 3)
    if (fields.size != 2) return null
    val sizeText = fields[1]
    val fullSize = if (sizeText == "*") {
        null
    } else {
        sizeText.nonNegativeLongOrNull() ?: return null
    }

    val range = if (fields[0] == "*") {
        // Unsatisfied range, as returned with HTTP 416.
        if (fullSize == null) return null
        null
    } else {
        val bounds = fields[0].split('-', limit = 3)
        if (bounds.size != 2) return null

        val start = bounds[0].nonNegativeLongOrNull() ?: return null
        val end = bounds[1].nonNegativeLongOrNull() ?: return null
        if (end < start) return null
        if (fullSize != null && end >= fullSize) return null

        start..end
    }
    return ContentRangeValue(range = range, fullSize = fullSize)
}

private fun String.nonNegativeLongOrNull(): Long? {
    if (isEmpty() || any { it !in '0'..'9' }) return null
    return toLongOrNull()
}
