package ir.amirab.downloader.ftp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.Downloader
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * Not registered in production until credential storage, UI, speed limiter
 * and lifecycle acceptance gates are complete.
 */
class FtpDownloader : Downloader<FtpDownloadItem, FtpDownloadJob, FtpDownloadCredentials> {
    override fun createJob(item: FtpDownloadItem, downloadManager: DownloadManager) =
        FtpDownloadJob(item, downloadManager)

    override fun accept(item: IDownloadItem) = item is FtpDownloadItem
    override val downloadItemClass: KClass<FtpDownloadItem> = FtpDownloadItem::class
    override val downloadCredentialsClass: KClass<FtpDownloadCredentials> = FtpDownloadCredentials::class
    override val downloadJobClass: KClass<FtpDownloadJob> = FtpDownloadJob::class
    override val downloadItemSerializer: KSerializer<FtpDownloadItem> = FtpDownloadItem.serializer()
    override val downloadCredentialsSerializer: KSerializer<FtpDownloadCredentials> = FtpDownloadCredentials.serializer()
}
