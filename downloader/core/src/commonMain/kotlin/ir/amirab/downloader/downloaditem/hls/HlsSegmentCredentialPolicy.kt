package ir.amirab.downloader.downloaditem.hls

import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import okhttp3.HttpUrl

/**
 * Credentials from a user-provided HLS playlist are origin-scoped, not
 * automatically trusted for every URI advertised inside that playlist.
 * Compare scheme, canonical host AND effective port, not a string prefix.
 */
internal fun sameHlsOrigin(first: HttpUrl, second: HttpUrl): Boolean =
    first.scheme == second.scheme && first.host == second.host && first.port == second.port

private val forbiddenSegmentHeaders = setOf(
    "host", "proxy-authorization", "proxy-authenticate", "connection",
    "transfer-encoding", "content-length", "range", "accept-encoding",
    "keep-alive", "te", "trailer", "upgrade",
)

internal fun credentialsForHlsSegment(
    source: IHLSCredentials,
    finalPlaylistUrl: String,
    segmentUrl: String,
): HttpDownloadCredentials {
    val originalOrigin = HttpUrlUtils.createURL(source.link)
    val finalPlaylistOrigin = HttpUrlUtils.createURL(finalPlaylistUrl)
    val segmentOrigin = HttpUrlUtils.createURL(segmentUrl)
    // A playlist redirect to another origin does NOT grant that host access
    // to credentials originally provided for the first playlist host.
    val trusted = sameHlsOrigin(originalOrigin, finalPlaylistOrigin) &&
        sameHlsOrigin(originalOrigin, segmentOrigin)
    val headers = if (trusted) {
        source.headers?.filterKeys { it.lowercase() !in forbiddenSegmentHeaders }
            ?.takeIf { it.isNotEmpty() }
    } else null
    val username = source.username?.takeIf { trusted }
    val password = source.password?.takeIf { trusted }
    val hasBasicAuth = !username.isNullOrBlank() && !password.isNullOrBlank()
    // Conservative: any user-provided header may be sensitive, not just Cookie.
    // Do not follow even same-origin redirects with forwarded headers or auth;
    // a future explicit redirect handler may safely permit verified hops.
    val hasForwardedSecrets = !headers.isNullOrEmpty() || hasBasicAuth
    return HttpDownloadCredentials(
        link = segmentUrl,
        headers = headers,
        username = username,
        password = password,
        userAgent = source.userAgent,
        followRedirects = !hasForwardedSecrets,
    )
}
