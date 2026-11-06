package ir.amirab.downloader.utils

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object TimeUtils {
    // Reuse an immutable parser across parallel HTTP response handlers. A lenient
    // SimpleDateFormat accepts impossible calendar dates and trailing text,
    // which can undermine Last-Modified based resume identity checks.
    private val imfFixdate = Regex(
        """(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \\d{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{4} \\d{2}:\\d{2}:\\d{2} GMT"""
    )
    private val strictHttpDate = DateTimeFormatter
        .ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)
        .withZone(ZoneOffset.UTC)

    fun convertLastModifiedHeaderToTimestamp(lastModified: String): Long {
        require(imfFixdate.matches(lastModified)) { "Invalid Last-Modified header" }
        return try {
            Instant.from(strictHttpDate.parse(lastModified)).toEpochMilli()
        } catch (failure: RuntimeException) {
            throw IllegalArgumentException("Invalid Last-Modified header", failure)
        }
    }
}
