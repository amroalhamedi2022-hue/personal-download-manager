package ir.amirab.downloader.connection.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpPartialTotalLengthTest {
    private fun response(code: Int, headers: Map<String, String>) = HttpResponseInfo(
        statusCode = code,
        message = "test",
        requestUrl = "https://example.test/file.bin",
        responseHeaders = headers,
    )

    @Test
    fun unknownTotalInPartialResponseNeverUsesFragmentLengthAsFileSize() {
        val info = response(206, mapOf(
            "content-length" to "100",
            "content-range" to "bytes 0-99/*",
        ))
        assertEquals(100L, info.contentLength)
        assertNull(info.totalLength)
        assertFalse(info.resumeSupport)
    }

    @Test
    fun missingOrMalformedContentRangeNeverMakesPartialLookComplete() {
        val fragments = listOf(
            mapOf("content-length" to "100"),
            mapOf("content-length" to "100", "content-range" to "bytes 300-200/1000"),
            mapOf("content-length" to "100", "content-range" to "bytes */*"),
        )
        fragments.forEach { headers ->
            val info = response(206, headers)
            assertNull(info.totalLength)
            assertFalse(info.resumeSupport)
        }
    }

    @Test
    fun validPartialTotalStillWinsOverFragmentLength() {
        val info = response(206, mapOf(
            "content-length" to "100",
            "content-range" to "bytes 100-199/1000",
        ))
        assertEquals(1000L, info.totalLength)
        assertTrue(info.resumeSupport)
    }

    @Test
    fun fullResponseKeepsOrdinaryContentLength() {
        val info = response(200, mapOf("content-length" to "100"))
        assertEquals(100L, info.totalLength)
    }
}
