package ir.amirab.downloader.downloaditem.hls

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Compile only this file and HlsPlaylistBodyReader.kt, never the app. */
fun main() {
    var checks = 0
    fun verify(ok: Boolean, label: String) { check(ok) { label }; checks++ }
    fun failsOversize(block: () -> Unit): Boolean = try {
        block()
        false
    } catch (e: IOException) {
        e.message == "HLS playlist exceeds maximum allowed size"
    }
    verify(readBoundedHlsPlaylist(ByteArrayInputStream(byteArrayOf())) == "", "empty playlist")
    verify(readBoundedHlsPlaylist(ByteArrayInputStream("#EXTM3U".toByteArray())) == "#EXTM3U", "normal playlist")
    verify(readBoundedHlsPlaylist(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3).length == 3, "exact size accepted")
    verify(failsOversize { readBoundedHlsPlaylist(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3) }, "oversized rejected")
    verify(failsOversize { readBoundedHlsPlaylist(ByteArrayInputStream(ByteArray(9000)), 8192) }, "oversized across buffer rejected")
    verify(readBoundedHlsPlaylist(ByteArrayInputStream("مرحبا".toByteArray(Charsets.UTF_8)), 64) == "مرحبا", "utf8 preserved")
    verify(runCatching { readBoundedHlsPlaylist(ByteArrayInputStream(byteArrayOf(1)), 0) }.exceptionOrNull() is IllegalArgumentException, "invalid limit rejected")
    val zeroRead = object : InputStream() {
        var count = 0
        override fun read(): Int = if (count++ == 0) 65 else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = 0
    }
    verify(readBoundedHlsPlaylist(zeroRead, 2) == "A", "zero-length read handled")
    println("PASS $checks/$checks HyperGet HLS bounded playlist source-only tests")
}
