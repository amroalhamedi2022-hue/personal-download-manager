package ir.amirab.downloader.downloaditem.hls

/**
 * Compare only the MIME type token, not optional Content-Type parameters.
 * Do not accept prefixes (e.g. application/vnd.apple.mpegurl-evil).
 */
internal fun isHlsPlaylistContentType(header: String?): Boolean {
    val mediaType = header?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return mediaType == "application/x-mpegurl" ||
        mediaType == "application/vnd.apple.mpegurl"
}
