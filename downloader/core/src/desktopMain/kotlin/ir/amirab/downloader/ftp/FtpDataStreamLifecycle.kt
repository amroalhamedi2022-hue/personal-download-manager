package ir.amirab.downloader.ftp

import java.io.InputStream

/**
 * RETR can race with Pause immediately after the data stream is registered.
 * Always unregister and close it even when the first cancellation check fails,
 * before any bytes are read or a resume sidecar is written.
 */
internal inline fun <T> withFtpDataStream(
    stream: InputStream,
    unregister: () -> Unit,
    checkCancellation: () -> Unit,
    block: () -> T,
): T {
    try {
        checkCancellation()
        return block()
    } finally {
        try {
            unregister()
        } finally {
            // Do not replace the original cancellation/transfer exception.
            runCatching { stream.close() }
        }
    }
}
