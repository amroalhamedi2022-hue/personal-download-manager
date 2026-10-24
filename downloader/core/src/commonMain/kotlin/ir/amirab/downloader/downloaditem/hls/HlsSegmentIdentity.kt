package ir.amirab.downloader.downloaditem.hls

import ir.amirab.util.HttpUrlUtils

/**
 * Resolve each media URI against the final fetched playlist URL before
 * persisting its identity. A relative filename alone is not sufficient to
 * reuse completed bytes after a playlist redirect or relocation.
 *
 * Older saved relative URIs intentionally fail the identity comparison once;
 * the segment is re-downloaded instead of silently publishing stale bytes.
 */
internal fun resolveHlsSegmentIdentity(finalPlaylistUrl: String, mediaUri: String): String {
    val resolved = HttpUrlUtils.createURL(finalPlaylistUrl).resolve(mediaUri)
        ?: throw IllegalArgumentException("Invalid HLS media segment URI")
    require(resolved.username.isEmpty() && resolved.password.isEmpty()) {
        "HLS media segment URI must not contain URL credentials"
    }
    // Fragments never reach the HTTP server and must not change byte identity.
    return resolved.newBuilder().fragment(null).build().toString()
}
