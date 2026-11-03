package ir.amirab.downloader.downloaditem.hls

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Hard cap applies to actual bytes received, not just Content-Length. */
internal const val MAX_HLS_PLAYLIST_BYTES: Int = 2 * 1024 * 1024

/** Reads at most [maxBytes] bytes, rejecting an oversized chunked response. */
internal fun readBoundedHlsPlaylist(
    input: InputStream,
    maxBytes: Int = MAX_HLS_PLAYLIST_BYTES,
): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val chunk = ByteArray(8192)
    var total = 0
    while (true) {
        // Read one byte beyond the limit to distinguish an exact-sized body
        // from an oversized body without buffering the entire response.
        val read = input.read(chunk, 0, minOf(chunk.size.toLong(), maxBytes.toLong() - total + 1L).toInt())
        if (read < 0) break
        if (read == 0) {
            val single = input.read()
            if (single < 0) break
            if (total == maxBytes) throw IOException("HLS playlist exceeds maximum allowed size")
            output.write(single)
            total++
            continue
        }
        if (read > maxBytes - total) {
            throw IOException("HLS playlist exceeds maximum allowed size")
        }
        output.write(chunk, 0, read)
        total += read
    }
    return output.toString(Charsets.UTF_8.name())
}
