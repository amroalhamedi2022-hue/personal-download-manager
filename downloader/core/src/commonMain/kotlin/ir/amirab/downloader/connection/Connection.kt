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
            // Closing an Okio source alone is not a strong enough cancellation
            // signal for every in-flight HTTP exchange. Cancel the owning call
            // first, then close the source so wrappers still release host permits.
            cancelTransport?.invoke()
        } finally {
            source.close()
        }
    }
}
