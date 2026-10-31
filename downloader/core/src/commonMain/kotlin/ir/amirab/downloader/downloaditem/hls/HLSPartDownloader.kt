package ir.amirab.downloader.downloaditem.hls

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.connection.response.expectSuccess
import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.part.MediaSegment
import ir.amirab.downloader.part.PartDownloader
import ir.amirab.util.HttpUrlUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import ir.amirab.downloader.utils.speedlimiter.SpeedLimiter
import ir.amirab.downloader.utils.hostlimits.HostLimitRegistry
import kotlin.coroutines.cancellation.CancellationException

class HLSPartDownloader(
    part: MediaSegment,
    getDestWriter: () -> DestWriter,
    private val baseURL: String,
    private val playlistCredentials: IHLSCredentials,
    private val client: HttpDownloaderClient,
    private val speedLimiters: List<SpeedLimiter>,
    private val hostLimitRegistry: HostLimitRegistry? = null,
) : PartDownloader<
        MediaSegment
        >(
    part = part,
    getDestWriter = getDestWriter,
) {
    override fun howMuchCanRead(maxAllowed: Long): Long {
        return maxAllowed
    }

    override suspend fun connectAndVerify(): Connection<HttpResponseInfo> {
        val fullLink = part.link
            .takeIf { HttpUrlUtils.isValidUrl(it) }
            ?.let(HttpUrlUtils::createURL)
            ?: HttpUrlUtils.createURL(baseURL).resolve(part.link)
        requireNotNull(fullLink) {
            "link is incorrect! ${part.link}"
        }
        val url = fullLink.toString()
        val hostPermit = hostLimitRegistry?.acquireForUrl(url)
        var openedConnection: Connection<HttpResponseInfo>? = null
        try {
            val connect = client.connectHlsWithOriginGuard(
                credentialsForHlsSegment(playlistCredentials, baseURL, url)
            )
            openedConnection = connect
            if (stop || !currentCoroutineContext().isActive) {
                connect.close()
                throw CancellationException()
            }
            if (!connect.responseInfo.isSuccessFul) {
                connect.close()
                connect.responseInfo.expectSuccess()
            }
            // A 206 body represents only a fragment, not an entire HLS media segment.
            requireCompleteHlsSegmentResponse(connect.responseInfo.statusCode)
            part.length = connect.responseInfo.contentLength
            val limiters = buildList {
                addAll(speedLimiters)
                hostLimitRegistry?.speedLimiterForUrl(url)?.let(::add)
            }
            val source = limiters.fold(connect.source) { acc, limiter ->
                limiter.source(acc)
            }.let { limitedSource ->
                hostPermit?.releaseWhenClosed(limitedSource) ?: limitedSource
            }
            return connect.copy(source = source)
        } catch (failure: Throwable) {
            kotlin.runCatching { openedConnection?.close() }
            hostPermit?.close()
            throw failure
        }

    }

    override fun onCanceled(e: Throwable) {
        if (!part.isCompleted) {
            // we should restart failed parts
            part.resetCurrent()
        }
        super.onCanceled(e)
    }

    override fun onFinish() {
        // EOF can be premature: compare against declared Content-Length before publishing.
        try {
            requireCompleteHlsSegmentBytes(part.length, part.current)
        } catch (failure: java.io.IOException) {
            onCanceled(failure)
            return
        }
        part.isCompleted = true
        super.onFinish()
    }
}
