package ir.amirab.downloader.utils

/**
 * Returns a bounded progress percentage. Unknown or invalid total lengths
 * must not turn into a misleading completed/overflowed progress value.
 */
fun calcPercent(proceed: Long, contentLength: Long): Int =
    if (contentLength <= 0L) 0 else ((proceed.toDouble() / contentLength.toDouble()) * 100.0)
        .coerceIn(0.0, 100.0).toInt()

fun calcPercent(proceed: Int, contentLength: Int): Int =
    calcPercent(proceed.toLong(), contentLength.toLong())

fun calcPercent(proceed: Double, contentLength: Double): Int =
    if (!proceed.isFinite() || !contentLength.isFinite() || contentLength <= 0.0) 0
    else ((proceed / contentLength) * 100.0).coerceIn(0.0, 100.0).toInt()
