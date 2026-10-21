package ir.amirab.downloader.ftp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persistent FTP item. Intentionally contains no password or token.
 * The authentication reference must be resolved by Windows secure storage
 * before any non-anonymous FTP job may be started.
 */
@Serializable
@SerialName("ftp")
data class FtpDownloadItem(
    override var id: Long,
    override var folder: String,
    override var name: String,
    override var link: String,
    override var downloadPage: String? = null,
    val username: String = "anonymous",
    override var contentLength: Long = IDownloadItem.LENGTH_UNKNOWN,
    override var dateAdded: Long = 0L,
    override var startTime: Long? = null,
    override var completeTime: Long? = null,
    override var status: DownloadStatus = DownloadStatus.Added,
    override var retryAttempt: Int = 0,
    override var retryAt: Long? = null,
    override var preferredConnectionCount: Int? = 1,
    override var speedLimit: Long = 0L,
    override var fileChecksum: String? = null,
) : IDownloadItem {
    override fun validateCredentials() =
        FtpDownloadCredentials(link, downloadPage, username).validateCredentials()

    override fun validateItem() {
        validateCredentials()
        require(speedLimit >= 0L) { "Negative FTP speed limit" }
        require(preferredConnectionCount == null || preferredConnectionCount == 1) {
            "FTP transfer currently supports a single connection only"
        }
    }

    override fun withCredentials(credentials: IDownloadCredentials): FtpDownloadItem =
        if (credentials is FtpDownloadCredentials) {
            copy(
                link = credentials.link,
                downloadPage = credentials.downloadPage,
                username = credentials.username,
            )
        } else this

    override fun copy(link: Option<String>, downloadPage: Option<String?>): FtpDownloadItem =
        copy(link = link.getOrElse { this.link }, downloadPage = downloadPage.getOrElse { this.downloadPage })

    override fun copy(
        id: Option<Long>,
        folder: Option<String>,
        name: Option<String>,
        link: Option<String>,
        contentLength: Option<Long>,
        downloadPage: Option<String?>,
        dateAdded: Option<Long>,
        startTime: Option<Long?>,
        completeTime: Option<Long?>,
        status: Option<DownloadStatus>,
        preferredConnectionCount: Option<Int?>,
        speedLimit: Option<Long>,
        fileChecksum: Option<String?>,
    ): FtpDownloadItem = copy(
        id = id.getOrElse { this.id },
        folder = folder.getOrElse { this.folder },
        name = name.getOrElse { this.name },
        link = link.getOrElse { this.link },
        contentLength = contentLength.getOrElse { this.contentLength },
        downloadPage = downloadPage.getOrElse { this.downloadPage },
        dateAdded = dateAdded.getOrElse { this.dateAdded },
        startTime = startTime.getOrElse { this.startTime },
        completeTime = completeTime.getOrElse { this.completeTime },
        status = status.getOrElse { this.status },
        preferredConnectionCount = preferredConnectionCount.getOrElse { this.preferredConnectionCount },
        speedLimit = speedLimit.getOrElse { this.speedLimit },
        fileChecksum = fileChecksum.getOrElse { this.fileChecksum },
    )
}
