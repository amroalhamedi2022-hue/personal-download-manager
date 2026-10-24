package ir.amirab.downloader.downloaditem.hls

import java.io.IOException

/**
 * HLS media segments are fetched as complete resources, never HTTP byte ranges.
 * A 206 response can be a valid HTTP response but would corrupt an HLS segment
 * when written at offset zero. An early EOF must not become Completed either.
 */
internal fun requireCompleteHlsSegmentResponse(statusCode: Int) {
    if (statusCode != 200) {
        throw IOException("HLS segment requires HTTP 200, got $statusCode")
    }
}

internal fun requireCompleteHlsSegmentBytes(expectedBytes: Long?, receivedBytes: Long) {
    if (receivedBytes <= 0L || (expectedBytes != null && receivedBytes != expectedBytes)) {
        throw IOException("Incomplete HLS segment: received $receivedBytes bytes; expected ${expectedBytes ?: "nonempty content"}")
    }
}
