package ir.amirab.downloader.downloaditem

import ir.amirab.downloader.exception.UnSuccessfulResponseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

internal const val BASE_RETRY_DELAY_MILLIS = 3_000L
internal const val MAX_BACKOFF_DELAY_MILLIS = 60_000L
internal const val MAX_SERVER_RETRY_DELAY_MILLIS = 15 * 60_000L

internal fun calculateRetryDelayMillis(
    retryIndex: Int,
    retryAfterHeader: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Long {
    var backoff = BASE_RETRY_DELAY_MILLIS
    repeat(retryIndex.coerceIn(0, 5)) {
        backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_DELAY_MILLIS)
    }
    val serverDelay = parseRetryAfterMillis(retryAfterHeader, nowEpochMillis) ?: 0L
    return max(backoff, serverDelay).coerceAtMost(MAX_SERVER_RETRY_DELAY_MILLIS)
}

internal fun Throwable.retryAfterHeaderOrNull(): String? {
    var current: Throwable? = this
    while (current != null) {
        if (current is UnSuccessfulResponseException) {
            return current.retryAfterHeader
        }
        current = current.cause
    }
    return null
}

private fun parseRetryAfterMillis(value: String?, nowEpochMillis: Long): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    normalized.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
        return seconds
            .coerceAtMost(MAX_SERVER_RETRY_DELAY_MILLIS / 1_000)
            .times(1_000)
    }
    return runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .apply { isLenient = false }
            .parse(normalized)
            ?.time
            ?.minus(nowEpochMillis)
            ?.coerceAtLeast(0)
    }.getOrNull()
}
