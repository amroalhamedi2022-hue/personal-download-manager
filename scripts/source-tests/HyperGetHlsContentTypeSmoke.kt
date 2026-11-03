package ir.amirab.downloader.downloaditem.hls

/** Standalone source-only regression. Never compiles the application. */
fun main() {
    val cases = listOf(
        "standard x-mpegurl" to ("application/x-mpegurl" to true),
        "standard Apple MIME" to ("application/vnd.apple.mpegurl" to true),
        "charset parameter" to ("application/vnd.apple.mpegurl; charset=utf-8" to true),
        "mixed-case MIME" to ("Application/Vnd.Apple.MpegURL" to true),
        "surrounding whitespace" to (" application/x-mpegurl ; charset=UTF-8" to true),
        "untrusted MIME suffix" to ("application/vnd.apple.mpegurl-evil" to false),
        "untrusted MIME prefix" to ("xapplication/vnd.apple.mpegurl" to false),
        "unrelated MIME" to ("text/html" to false),
        "missing header" to (null to false),
        "empty header" to ("" to false),
        "parameter-only header" to (";charset=utf-8" to false),
        "multiple MIME values" to ("application/x-mpegurl, text/html" to false),
    )
    cases.forEach { (label, pair) ->
        check(isHlsPlaylistContentType(pair.first) == pair.second) { "FAIL: $label" }
        println("PASS: $label")
    }
    println("PASS ${cases.size}/${cases.size} HLS MIME source cases")
}
