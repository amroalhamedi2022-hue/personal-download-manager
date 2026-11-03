package ir.amirab.downloader.downloaditem.hls

import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import java.io.IOException

/**
 * Follow protected HLS redirects only after verifying the next hop.
 * OkHttp automatic redirects stay disabled for credential-bearing calls.
 * Anonymous HLS requests retain their existing OkHttp redirect behavior.
 */
private const val MAX_GUARDED_HLS_REDIRECTS = 5
private val GUARDED_HLS_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

suspend fun HttpDownloaderClient.connectHlsWithOriginGuard(
    credentials: HttpDownloadCredentials,
): Connection<HttpResponseInfo> {
    if (credentials.followRedirects) return connect(credentials, null, null)

    val originalUrl = HttpUrlUtils.createURL(credentials.link)
    val visited = mutableSetOf(originalUrl.toString())
    var current = credentials

    for (hop in 0..MAX_GUARDED_HLS_REDIRECTS) {
        val connection = connect(current, null, null)
        val response = connection.responseInfo
        if (response.statusCode !in GUARDED_HLS_REDIRECT_CODES) return connection

        val location = response.responseHeaders["location"]?.trim()
        val target = try {
            location?.takeIf { it.isNotEmpty() }
                ?.let { HttpUrlUtils.createURL(response.requestUrl).resolve(it) }
        } catch (_: IllegalArgumentException) {
            null
        }
        // A redirect body/socket must not remain open across requests or errors.
        connection.close()

        val responseUrl = try {
            HttpUrlUtils.createURL(response.requestUrl)
        } catch (_: IllegalArgumentException) {
            throw IOException("Authenticated HLS redirect rejected: invalid response URL")
        }
        if (target == null ||
            !sameHlsOrigin(originalUrl, responseUrl) ||
            !sameHlsOrigin(originalUrl, target) ||
            target.username.isNotEmpty() || target.password.isNotEmpty()
        ) {
            throw IOException("Authenticated HLS redirect rejected: untrusted target")
        }
        if (hop == MAX_GUARDED_HLS_REDIRECTS || !visited.add(target.toString())) {
            throw IOException("Authenticated HLS redirect rejected: loop or hop limit")
        }
        // Keep the guarded flag and explicit headers on same-origin hops.
        current = current.copy(link = target.toString())
    }
    throw IOException("Authenticated HLS redirect rejected: hop limit")
}
