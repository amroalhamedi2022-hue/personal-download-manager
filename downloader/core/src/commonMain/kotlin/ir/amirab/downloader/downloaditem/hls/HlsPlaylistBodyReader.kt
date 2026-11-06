package ir.amirab.downloader.downloaditem.hls

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

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
    // HLS playlists are UTF-8. The default String decoder silently inserts
    // replacement characters for malformed bytes, potentially changing a
    // signed segment URI and corrupting persisted segment identity.
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    } catch (failure: CharacterCodingException) {
        throw IOException("Invalid UTF-8 in HLS playlist", failure)
    }
}
