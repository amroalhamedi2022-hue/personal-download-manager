package ir.amirab.downloader.connection

import okio.Source
import java.io.Closeable

data class Connection<out TResponseInfo : IResponseInfo>(
    val source: Source,
    val contentLength: Long,
    val responseInfo: TResponseInfo,
    private val cancelTransport: (() -> Unit)? = null,
) : Closeable {
    override fun close() {
        try {
            // Cancel the owning HTTP call before closing its response source:
            // a blocking network read may ignore Thread.interrupt().
            cancelTransport?.invoke()
        } finally {
            source.close()
        }
    }
}
