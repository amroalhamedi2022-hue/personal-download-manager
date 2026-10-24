package ir.amirab.downloader.downloaditem.hls

import io.lindstrom.m3u8.model.KeyMethod
import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import io.lindstrom.m3u8.parser.ParsingMode
import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.connection.response.expectSuccess
import ir.amirab.downloader.utils.FileNameUtil
import ir.amirab.util.HttpUrlUtils
import okio.buffer
import java.io.IOException

data class HLSResponseInfo(
    val httpResponseInfo: HttpResponseInfo,
    val hlsManifest: MediaPlaylist
) : IResponseInfo {
    val name: String?
        get() = httpResponseInfo.fileName
    val duration = hlsManifest.mediaSegments()
        .sumOf { it.duration() }
        .takeIf { it > 0 }

    override val isSuccessFul: Boolean
        get() = httpResponseInfo.isSuccessFul
    override val requiresAuth: Boolean
        get() = httpResponseInfo.requireBasicAuth
    override val requireBasicAuth: Boolean
        get() = httpResponseInfo.requireBasicAuth
    override val isWebPage: Boolean
        get() = httpResponseInfo.isWebPage
    override val resumeSupport: Boolean
        get() = true
    override val unsuccessFullException: Throwable?
        get() = httpResponseInfo.unsuccessFullException

    companion object {
        fun fromConnection(connection: Connection<HttpResponseInfo>): HLSResponseInfo {
            expectSuccess(connection)
            val data = connection.source.buffer().use { readBoundedHlsPlaylist(it.inputStream()) }
            val playlist = try {
                parseHLSAsMediaPlaylist(data)
            } catch (e: Exception) {
                throw BadHLSResponseException("can't parse HLS playlist", e)
            }
            val mediaSegments = playlist.mediaSegments()
            if (mediaSegments.isEmpty()) {
                throw UnsupportedOperationException(
                    "playlist has no segments"
                )
            }
            val firstSegmentExtension = HttpUrlUtils
                .createURL(connection.responseInfo.requestUrl)
                .resolve(mediaSegments[0].uri())?.toString()
                ?.let(HttpUrlUtils::extractNameFromLink)
                ?.let(FileNameUtil::getExtensionOrNull)
                ?.lowercase()
            if (firstSegmentExtension != "ts") {
                throw UnsupportedOperationException(
                    "Only HLS .ts segments supported at the moment, but '$firstSegmentExtension' provided"
                )
            }
            if (isMediaPlayListEncrypted(playlist)) {
                throw UnsupportedOperationException(
                    "Encrypted HLS playlists are not supported"
                )
            }
            return HLSResponseInfo(
                connection.responseInfo,
                playlist,
            )
        }

        private fun parseHLSAsMediaPlaylist(data: String): MediaPlaylist {
            val playlistParser = MediaPlaylistParser(ParsingMode.LENIENT)
            return playlistParser.readPlaylist(data)
        }

        /**
         * if no hls content-size received we check this
         */
        private fun expectSuccess(connection: Connection<HttpResponseInfo>) {
            connection.responseInfo.expectSuccess()
            // Content-Type is not a safety boundary: even a correctly typed
            // response must respect the same declared and actual byte limit.
            val declaredLength = connection.responseInfo.contentLength
            if (declaredLength != null && declaredLength > MAX_HLS_PLAYLIST_BYTES) {
                throw BadHLSResponseException("HLS playlist content length exceeds maximum allowed size")
            }
            val contentType = connection.responseInfo.responseHeaders["content-type"]
            var error: String? = null
            if (contentType == null) {
                error = "no content type is provided"
            } else if (!isHlsPlaylistContentType(contentType)) {
                error = "content type is not hls compatible: $contentType"
            }
            if (error != null) {
                val contentLength = connection.responseInfo.contentLength
                if (contentLength == null) {
                    error += ", and content length is unknown"
                } else {
                    if (contentLength > MAX_HLS_PLAYLIST_BYTES) {
                        error += ", and returned content length is too big for hls playlist"
                    } else {
                        error = null
                    }
                }
            }
            if (error != null) {
                throw BadHLSResponseException(error)
            }
        }

        private fun isMediaPlayListEncrypted(playlist: MediaPlaylist): Boolean {
            return playlist.mediaSegments().any {
                it.segmentKeys().any { key ->
                    key.method() != KeyMethod.NONE
                }
            }
        }
    }
}

class BadHLSResponseException(
    message: String, cause: Throwable? = null
) : IOException(message, cause)
