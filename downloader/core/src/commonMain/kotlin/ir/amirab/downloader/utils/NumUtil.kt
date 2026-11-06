package ir.amirab.downloader.utils

/**
 * Returns a bounded progress percentage. Unknown or invalid total lengths
 * must not turn into a misleading completed/overflowed progress value.
 */
fun calcPercent(proceed: Long, contentLength: Long): Int {
    if (contentLength <= 0L || proceed <= 0L) return 0
    if (proceed >= contentLength) return 100
    // Converting very large Long values to Double can round an incomplete
    // transfer up to 100%. Only a completed transfer may report 100%.
    return ((proceed.toDouble() / contentLength.toDouble()) * 100.0)
        .toInt().coerceIn(0, 99)
}

fun calcPercent(proceed: Int, contentLength: Int): Int =
    calcPercent(proceed.toLong(), contentLength.toLong())

fun calcPercent(proceed: Double, contentLength: Double): Int =
    if (!proceed.isFinite() || !contentLength.isFinite() || contentLength <= 0.0) 0
    else ((proceed / contentLength) * 100.0).coerceIn(0.0, 100.0).toInt()
