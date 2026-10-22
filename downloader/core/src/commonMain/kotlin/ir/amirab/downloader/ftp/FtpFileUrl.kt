package ir.amirab.downloader.ftp

import java.net.URI

/**
 * Parse FTP file links consistently in persisted items, desktop UI, and the
 * socket transport. The path may contain literal spaces or square brackets,
 * as seen in common FTP file listings and copy/pasted Windows paths.
 *
 * Preserve existing %-escapes, encode only path characters that URI rejects,
 * and never silently accept embedded credentials, queries, fragments or
 * control characters. Passwords must use the separate WinCred-backed provider.
 */
fun parseFtpFileUri(value: String): URI {
    val trimmed = value.trim()
    val schemeEnd = trimmed.indexOf("://")
    require(schemeEnd > 0 && trimmed.substring(0, schemeEnd).equals("ftp", ignoreCase = true)) {
        "Only ftp:// links are accepted"
    }
    val authorityStart = schemeEnd + 3
    val pathStart = trimmed.indexOf('/', authorityStart)
    require(pathStart > authorityStart) { "FTP host or file path is missing" }
    val authority = trimmed.substring(authorityStart, pathStart)
    require(authority.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) {
        "Invalid FTP authority"
    }
    // Encode just raw path spaces/brackets; %20 and other existing escapes must
    // stay untouched. Brackets in IPv6 host literals are never modified.
    val path = trimmed.substring(pathStart)
        .replace(" ", "%20")
        .replace("[", "%5B")
        .replace("]", "%5D")
    val uri = runCatching { URI(trimmed.substring(0, pathStart) + path) }.getOrNull()
        ?: throw IllegalArgumentException("Invalid FTP URL")
    require(!uri.host.isNullOrBlank()) { "FTP host is missing" }
    require(uri.userInfo == null && uri.rawUserInfo == null) {
        "Do not embed FTP credentials in URLs"
    }
    require(uri.query == null && uri.fragment == null) { "Unsupported FTP URL query/fragment" }
    val decodedPath = uri.path.orEmpty()
    require(decodedPath.startsWith("/") && decodedPath.length > 1) { "FTP path is missing" }
    require(decodedPath.none { it.code < 0x20 || it.code == 0x7f }) {
        "Unsafe FTP path"
    }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid FTP port" }
    return uri
}
