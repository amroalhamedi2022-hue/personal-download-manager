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

/**
 * Credential Manager-backed provider for desktop FTP. No network password is
 * serialized with an item: only a random opaque local credential reference.
 * It is intentionally NOT registered in the user-facing UI until the full
 * Add URL / credential lifecycle and backup/restore safety tests pass.
 */
class WindowsCredentialFtpProvider(
    private val vault: WindowsFtpCredentialVault = WindowsFtpCredentialVault(),
) : FtpCredentialsProvider {
    override fun resolve(item: FtpDownloadItem): FtpSessionCredentials {
        item.validateItem()
        if (item.username == "anonymous" && item.credentialRef == null) {
            return AnonymousFtpCredentialsProvider.resolve(item)
        }
        val reference = item.credentialRef
            ?: throw IOException("Authenticated FTP requires a saved Windows credential reference")
        val saved = vault.read(reference)
            ?: throw IOException("Windows credential for FTP account is missing or unavailable")
        if (saved.username != item.username) {
            throw IOException("Windows FTP credential does not match selected username")
        }
        return saved
    }
}
