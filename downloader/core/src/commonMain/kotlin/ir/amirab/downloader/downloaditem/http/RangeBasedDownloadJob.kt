package ir.amirab.downloader.downloaditem.http

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.part.RangedPart

/**
 * UI-facing range progress contract shared by the built-in HTTP engine and an
 * optional desktop engine. Engine-specific identities never cross this boundary.
 */
interface RangeBasedDownloadJob {
    val supportsConcurrent: Boolean?
    fun getParts(): List<RangedPart>
}

/**
 * Desktop may provide an opt-in HTTP job. Returning null is the explicit signal
 * to keep using HyperGet's built-in engine.
 */
fun interface OptionalHttpDownloadJobFactory {
    fun create(item: HttpDownloadItem, downloadManager: DownloadManager): DownloadJob?

    companion object {
        val Disabled = OptionalHttpDownloadJobFactory { _, _ -> null }
    }
}
