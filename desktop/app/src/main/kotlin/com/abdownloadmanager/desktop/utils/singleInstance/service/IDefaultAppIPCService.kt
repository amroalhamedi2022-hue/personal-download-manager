package com.abdownloadmanager.desktop.utils.singleInstance.service

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable
import ir.amirab.downloader.downloaditem.DownloadStatus as DownloadItemStatus

@Rpc
interface IDefaultAppIPCService {
    // download
    suspend fun addDownloadByGui(request: AddDownloadsFromIntegration)
    suspend fun addDownload(request: NewDownloadTask): Long
    suspend fun addFtpDownload(request: AddFtpDownloadIPC): Long

    suspend fun pauseDownload(ids: List<Long>)
    suspend fun resumeDownload(ids: List<Long>)
    suspend fun openDownload(ids: List<Long>)

    /** Persistent global active-download limit: 0 means unlimited. */
    suspend fun getMaxConcurrentDownloads(): Int
    suspend fun setMaxConcurrentDownloads(value: Int): Int

    suspend fun removeDownload(ids: List<Long>, alsoRemoveFile: Boolean)

    suspend fun showDownload(ids: List<Long>): List<ShowDownloadIPC>
    fun watchDownload(ids: List<Long>): Flow<List<ShowDownloadIPC>>

    suspend fun listQueues(): List<ApiQueueModel>
}

/** Password-bearing local RPC value: handle transiently and never persist/log it. */
@Serializable
data class AddFtpDownloadIPC(
    val link: String,
    val username: String,
    val password: String,
    val folder: String,
    val name: String,
    val startImmediately: Boolean,
)

@Serializable
data class ShowDownloadIPC(
    val name: String,
    val folder: String,
    val id: Long,
    val percent: Int?,
    val status: DownloadStatus,
    // Optional additive IPC field: never include URLs, exception messages or secrets.
    val failureType: String? = null
) {
    @Serializable
    enum class DownloadStatus {
        Paused,
        Error,
        Downloading,
        Finished,
        PreparingFile,
        Resuming,
        Retrying;

        companion object {
            fun fromDownloadStatus(status: DownloadJobStatus): DownloadStatus {
                return when (status) {
                    is DownloadJobStatus.Canceled -> {
                        if (ExceptionUtils.isNormalCancellation(status.e)) {
                            Paused
                        } else {
                            Error
                        }
                    }

                    DownloadJobStatus.Downloading -> Downloading
                    DownloadJobStatus.Finished -> Finished
                    DownloadJobStatus.IDLE -> Paused
                    is DownloadJobStatus.PreparingFile -> PreparingFile
                    DownloadJobStatus.Resuming -> Resuming
                    is DownloadJobStatus.Retrying -> Retrying
                }
            }

            fun fromDownloadStatus(status: DownloadItemStatus): ShowDownloadIPC.DownloadStatus {
                return when (status) {
                    DownloadItemStatus.Paused -> Paused
                    DownloadItemStatus.Error -> Error
                    DownloadItemStatus.Downloading -> Downloading
                    DownloadItemStatus.Completed -> Finished
                    DownloadItemStatus.Added -> Paused
                }
            }
        }
    }
}
