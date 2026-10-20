package ir.amirab.downloader.downloaditem

import ir.amirab.downloader.exception.UnSuccessfulResponseException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

internal const val BASE_RETRY_DELAY_MILLIS = 3_000L
internal const val MAX_BACKOFF_DELAY_MILLIS = 60_000L

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
    return max(backoff, serverDelay)
}

internal fun calculateRetryAtMillis(
    delayMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Long = saturatedAdd(nowEpochMillis, delayMillis.coerceAtLeast(0L))

internal fun Throwable.retryAfterHeaderOrNull(): String? {
    causes().forEach { current ->
        if (current is UnSuccessfulResponseException) {
            return current.retryAfterHeader
        }
    }
    return null
}

internal fun Throwable.isRetryableDownloadFailure(): Boolean {
    val chain = causes().toList()
    if (chain.any { it is CancellationException }) return false
    chain.filterIsInstance<UnSuccessfulResponseException>().firstOrNull()?.let { response ->
        return response.code == 408 || response.code == 425 || response.code == 429 ||
            response.code in 500..599
    }
    return chain.any { it is IOException }
}

private fun parseRetryAfterMillis(value: String?, nowEpochMillis: Long): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (normalized.all(Char::isDigit)) {
        val seconds = normalized.toLongOrNull() ?: Long.MAX_VALUE
        return saturatedMultiplyByOneThousand(seconds)
    }
    return runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .apply { isLenient = false }
            .parse(normalized)
            ?.time
            ?.let { retryAt -> saturatedSubtract(retryAt, nowEpochMillis) }
            ?.coerceAtLeast(0)
    }.getOrNull()
}

private fun Throwable.causes(): Sequence<Throwable> = sequence {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this@causes
    while (current != null && seen.add(current)) {
        yield(current)
        current = current.cause
    }
}

private fun saturatedMultiplyByOneThousand(value: Long): Long =
    if (value > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else value * 1_000L

private fun saturatedAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private fun saturatedSubtract(left: Long, right: Long): Long = when {
    right < 0L && left > Long.MAX_VALUE + right -> Long.MAX_VALUE
    right > 0L && left < Long.MIN_VALUE + right -> Long.MIN_VALUE
    else -> left - right
}
