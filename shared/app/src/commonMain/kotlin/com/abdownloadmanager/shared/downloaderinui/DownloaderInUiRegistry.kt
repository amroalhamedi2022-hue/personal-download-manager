package com.abdownloadmanager.shared.downloaderinui

import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadInputs
import ir.amirab.downloader.Downloader
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.monitor.UiRangedPart
import ir.amirab.downloader.monitor.RangeBasedProcessingDownloadItemState
import ir.amirab.downloader.monitor.CompletedDownloadItemState
import ir.amirab.downloader.monitor.DownloadItemStateFactory
import ir.amirab.downloader.monitor.ProcessingDownloadItemFactoryInputs
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import kotlin.reflect.KClass

typealias TADownloaderInUI = DownloaderInUi<
        IDownloadCredentials,
        IResponseInfo,
        DownloadSize,
        LinkChecker<IDownloadCredentials, IResponseInfo, DownloadSize>,
        IDownloadItem,
        NewDownloadInputs<IDownloadItem, IDownloadCredentials, IResponseInfo, DownloadSize, LinkChecker<IDownloadCredentials, IResponseInfo, DownloadSize>>,
        EditDownloadInputs<IDownloadItem, IDownloadCredentials, IResponseInfo, DownloadSize, LinkChecker<IDownloadCredentials, IResponseInfo, DownloadSize>, CredentialAndItemMapper<IDownloadCredentials, IDownloadItem>>,
        CredentialAndItemMapper<IDownloadCredentials, IDownloadItem>,
        DownloadJob,
        Downloader<IDownloadItem, DownloadJob, IDownloadCredentials>>

class DownloaderInUiRegistry
    : DownloadItemStateFactory<IDownloadItem, DownloadJob> {
    private val list = mutableListOf<TADownloaderInUI>()
    private val componentHashes = hashMapOf<Any, TADownloaderInUI>()
    fun add(downloaderInUi: DownloaderInUi<*, *, *, *, *, *, *, *, *, *>) {
        // the compiler gave me error when I add these two generics (TDownloadJob, TDownloader) into the DownloaderInUi
        @Suppress("UNCHECKED_CAST")
        val element = downloaderInUi as TADownloaderInUI
        list.add(element)
        getComponentsOf(element).forEach {
            componentHashes[it] = element
        }
    }

    fun remove(downloaderInUi: DownloaderInUi<*, *, *, *, *, *, *, *, *, *>) {
        list.remove(downloaderInUi)
        @Suppress("UNCHECKED_CAST")
        getComponentsOf(
            downloaderInUi as TADownloaderInUI
        ).forEach {
            componentHashes.remove(it)
        }
    }

    fun getDownloaderOf(downloadItem: IDownloadCredentials): TADownloaderInUI? {
        componentHashes.get(downloadItem::class)?.let {
            // fast path without iterating the list
            return it
        }
        // IDownloadCredentials is an intermediate interface so we should iterate the list instead
        return list.firstOrNull {
            it.acceptDownloadCredentials(downloadItem)
        }
    }

    private fun getDownloaderOf(downloadJob: DownloadJob): TADownloaderInUI? {
        return getDownloaderOf(downloadJob.downloadItem)
    }

    fun bestMatchForThisLink(link: String): TADownloaderInUI? {
        return list.firstOrNull {
            it.supportsThisLink(link)
        }
    }

    fun getAll(): List<TADownloaderInUI> {
        return list.toList()
    }


    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<DownloadJob>,
    ): ProcessingDownloadItemState {
        val downloadJob = props.downloadJob
        val ui = getDownloaderOf(downloadJob)
        if (ui != null) return ui.createProcessingDownloadItemState(props)
        // FTP uses a separate desktop Add URL flow. Show the real job in the
        // existing download list while its specialized detail UI is developed.
        val item = downloadJob.downloadItem
        if (item.link.startsWith("ftp://", ignoreCase = true)) {
            val transferred = downloadJob.getDownloadedSize().coerceAtLeast(0L)
            return RangeBasedProcessingDownloadItemState(
                id = item.id,
                folder = item.folder,
                name = item.name,
                downloadLink = item.link,
                contentLength = item.contentLength,
                saveLocation = item.name,
                dateAdded = item.dateAdded,
                startTime = item.startTime ?: -1L,
                completeTime = item.completeTime ?: -1L,
                status = downloadJob.status.value,
                speed = props.speed,
                parts = listOf(UiRangedPart(
                    id = 0L,
                    status = if (downloadJob.isDownloadActive.value)
                        PartDownloadStatus.ReceivingData else PartDownloadStatus.IDLE,
                    howMuchProceed = transferred,
                    percent = null,
                    length = item.contentLength.takeIf { it > 0L },
                    partSpace = 1f,
                )),
                supportResume = null,
                isWaiting = props.isWaiting,
            )
        }
        error("there is no downloader in UI registered for this download job: ${downloadJob::class.qualifiedName}")
    }

    override fun createCompletedDownloadItemState(downloadItem: IDownloadItem): CompletedDownloadItemState {
        val ui = getDownloaderOf(downloadItem)
        if (ui != null) return ui.createCompletedDownloadItemState(downloadItem)
        if (downloadItem.link.startsWith("ftp://", ignoreCase = true)) {
            return CompletedDownloadItemState.fromDownloadItem(downloadItem)
        }
        error("there is no downloader in UI registered for this download item: ${downloadItem::class.qualifiedName}")
    }

    companion object {
        private fun getComponentsOf(element: TADownloaderInUI): List<KClass<out Any>> {
            return listOf(
                element.downloader.downloadJobClass,
                element.downloader.downloadItemClass,
            )
        }
    }

}
