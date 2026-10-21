package ir.amirab.downloader.ftp

import java.io.IOException

/**
 * Transient, non-serializable FTP login resolved at download time.
 * Production's default explicitly permits anonymous FTP only. A future
 * Windows Credential Manager implementation must inject an authenticated
 * provider after safe OS-backed storage has been tested.
 */
class FtpSessionCredentials(val username: String, val password: String) {
    override fun toString(): String = "FtpSessionCredentials(<redacted>)"
}

fun interface FtpCredentialsProvider {
    fun resolve(item: FtpDownloadItem): FtpSessionCredentials
}

object AnonymousFtpCredentialsProvider : FtpCredentialsProvider {
    override fun resolve(item: FtpDownloadItem): FtpSessionCredentials {
        if (item.username != "anonymous") {
            throw IOException("Authenticated FTP requires protected OS credential storage")
        }
        return FtpSessionCredentials("anonymous", "anonymous@")
    }
}
