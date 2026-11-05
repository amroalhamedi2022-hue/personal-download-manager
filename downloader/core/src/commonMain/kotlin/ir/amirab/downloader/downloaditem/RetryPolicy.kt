package ir.amirab.downloader.downloaditem

import ir.amirab.downloader.exception.UnSuccessfulResponseException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.max

internal const val BASE_RETRY_DELAY_MILLIS = 3_000L
internal const val MAX_BACKOFF_DELAY_MILLIS = 60_000L
internal const val MAX_SERVER_RETRY_DELAY_MILLIS = 15 * 60_000L

// RFC 9110 Retry-After allows decimal seconds or an HTTP-date. For IMF-fixdate,
// require the complete GMT date and matching weekday; Java's lenient DateFormat
// can silently accept trailing junk and contradictory dates.
private val imfFixdate = Regex(
    """(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{4} \d{2}:\d{2}:\d{2} GMT"""
)
private val httpDateParser = DateTimeFormatter
    .ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", Locale.US)
    .withResolverStyle(ResolverStyle.STRICT)
    .withZone(ZoneOffset.UTC)

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
    // A Throwable cause graph may be cyclic (two exceptions can be linked by
    // initCause). Never hang the download job's retry loop traversing it.
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is UnSuccessfulResponseException) {
            return current.retryAfterHeader
        }
        current = current.cause
    }
    return null
}

private fun parseRetryAfterMillis(value: String?, nowEpochMillis: Long): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null

    // A huge but syntactically valid delta-seconds must be clamped to our
    // existing safety cap, not mistaken for an invalid header and retried early.
    if (normalized.all { it in '0'..'9' }) {
        val seconds = normalized.toLongOrNull() ?: return MAX_SERVER_RETRY_DELAY_MILLIS
        return seconds.coerceAtMost(MAX_SERVER_RETRY_DELAY_MILLIS / 1_000) * 1_000
    }

    if (!imfFixdate.matches(normalized)) return null
    val serverDeadline = runCatching {
        Instant.from(httpDateParser.parse(normalized)).toEpochMilli()
    }.getOrNull() ?: return null
    if (serverDeadline <= nowEpochMillis) return 0L

    // Math.subtractExact prevents overflow for unusual persisted/system clocks.
    val remaining = runCatching {
        Math.subtractExact(serverDeadline, nowEpochMillis)
    }.getOrDefault(MAX_SERVER_RETRY_DELAY_MILLIS)
    return remaining.coerceAtMost(MAX_SERVER_RETRY_DELAY_MILLIS)
}
