package ir.amirab.downloader.ftp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val credentialRef: String? = null,
) : IDownloadCredentials {
    override fun validateCredentials() {
        validateFtpLink(link)
        credentialRef?.let(::validateFtpCredentialReference)
        require(username.isNotBlank() && username.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid FTP username"
        }
    }

    override fun copy(link: Option<String>, downloadPage: Option<String?>): FtpDownloadCredentials =
        copy(link = link.getOrElse { this.link }, downloadPage = downloadPage.getOrElse { this.downloadPage })
}

internal fun validateFtpCredentialReference(reference: String) {
    require(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(reference)) {
        "Invalid FTP credential reference"
    }
}

internal fun validateFtpLink(url: String) {
    parseFtpFileUri(url)
}
