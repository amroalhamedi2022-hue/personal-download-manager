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

/**
 * The initial manifest and UI preview share one conservative redirect policy.
 * OkHttp otherwise follows cross-origin 302/307 with caller-supplied headers.
 * Keep ordinary anonymous playlist redirects working; authenticated redirects
 * require an explicit, origin-checked flow (not implemented yet).
 *
 * This request policy is transient and does not change stored HLS credentials.
 */
fun credentialsForHlsPlaylist(source: IHLSCredentials): HttpDownloadCredentials {
    val headers = source.headers
        ?.filterKeys { it.lowercase() !in forbiddenSegmentHeaders }
        ?.takeIf { it.isNotEmpty() }
    val hasBasicAuth = !source.username.isNullOrBlank() && !source.password.isNullOrBlank()
    return HttpDownloadCredentials(
        link = source.link,
        headers = headers,
        username = source.username,
        password = source.password,
        userAgent = source.userAgent,
        followRedirects = headers.isNullOrEmpty() && !hasBasicAuth,
    )
}

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
