package ir.amirab.downloader.ftp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * FTP credentials are a URL and a username only. Never persist the password in
 * this serializable model; authenticated accounts need an OS-protected credential
 * reference in a separately reviewed implementation.
 */
@Serializable
@SerialName("ftp")
data class FtpDownloadCredentials(
    override val link: String,
    override val downloadPage: String? = null,
    val username: String = "anonymous",
) : IDownloadCredentials {
    override fun validateCredentials() {
        validateFtpLink(link)
        require(username.isNotBlank() && username.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid FTP username"
        }
    }

    override fun copy(link: Option<String>, downloadPage: Option<String?>): FtpDownloadCredentials =
        copy(link = link.getOrElse { this.link }, downloadPage = downloadPage.getOrElse { this.downloadPage })
}

internal fun validateFtpLink(url: String) {
    val uri = runCatching { URI(url.trim()) }.getOrNull()
        ?: throw IllegalArgumentException("Invalid FTP URL")
    require(uri.scheme.equals("ftp", ignoreCase = true)) { "Only ftp:// links are accepted" }
    require(!uri.host.isNullOrBlank()) { "FTP host is missing" }
    require(uri.userInfo == null) { "Do not embed FTP passwords in URLs" }
    require(uri.query == null && uri.fragment == null) { "Unsupported FTP URL query/fragment" }
    require(uri.path?.startsWith("/") == true && uri.path.length > 1) { "FTP path is missing" }
    require(uri.path.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Unsafe FTP path" }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid FTP port" }
}
