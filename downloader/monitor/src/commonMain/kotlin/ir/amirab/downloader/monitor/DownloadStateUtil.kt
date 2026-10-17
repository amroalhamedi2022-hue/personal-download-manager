package ir.amirab.downloader.monitor

import ir.amirab.downloader.downloaditem.DownloadJobStatus

fun IDownloadItemState.statusOrFinished(): DownloadJobStatus {
    return (this as? ProcessingDownloadItemState)?.status ?: DownloadJobStatus.Finished
}

fun IDownloadItemState.isFinished(): Boolean {
    return this is CompletedDownloadItemState
}

fun IDownloadItemState.isNotFinished(): Boolean {
    return this is ProcessingDownloadItemState
}

fun IDownloadItemState.speedOrNull(): Long? {
    return (this as? ProcessingDownloadItemState)?.speed
}

fun IDownloadItemState.remainingOrNull(): Long? {
    return (this as? ProcessingDownloadItemState)?.remainingTime
}

/**
 * Returns an exact total only when every item has a known non-negative length.
 * A null result prevents unknown lengths and overflow from being presented as 0 bytes.
 */
fun Iterable<IDownloadItemState>.totalContentLengthOrNull(): Long? {
    var total = 0L
    for (item in this) {
        val length = item.contentLength
        if (length < 0 || length > Long.MAX_VALUE - total) {
            return null
        }
        total += length
    }
    return total
}
