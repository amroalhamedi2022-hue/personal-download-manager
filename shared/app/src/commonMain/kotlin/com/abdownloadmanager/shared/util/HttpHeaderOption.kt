package com.abdownloadmanager.shared.util

/**
 * Parse a desktop CLI --header argument without truncating header values that
 * contain ':' or '=' (Authorization, Cookie, Referer, signed CDN URLs).
 *
 * Supports both "Name: value" and "Name=value" forms for backwards
 * compatibility. Rejects unsafe control characters to prevent request-header
 * injection. The returned value is not logged, so authorization secrets stay
 * out of diagnostics.
 */
fun parseDownloadHeaderOption(raw: String): Pair<String, String> {
    val colon = raw.indexOf(':')
    val equals = raw.indexOf('=')
    val delimiter = when {
        colon < 0 -> equals
        equals < 0 -> colon
        else -> minOf(colon, equals)
    }
    require(delimiter > 0) { "Header must be Name: value or Name=value" }
    val name = raw.substring(0, delimiter).trim()
    require(name.isNotEmpty() && name.all { it.isHttpHeaderTokenCharacter() }) {
        "Invalid HTTP header name"
    }
    val value = raw.substring(delimiter + 1).trimStart(' ', '\t')
    require(value.none { it == '\r' || it == '\n' || it == '\u0000' || it == '\u007f' ||
        (it.code < 0x20 && it != '\t') }) {
        "Unsafe HTTP header value"
    }
    return name to value
}

private fun Char.isHttpHeaderTokenCharacter(): Boolean =
    (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9') ||
        this in "!#$%&'*+-.^_`|~"
