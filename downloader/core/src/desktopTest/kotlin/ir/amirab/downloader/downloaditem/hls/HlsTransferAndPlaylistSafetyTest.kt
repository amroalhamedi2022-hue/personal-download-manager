package ir.amirab.downloader.downloaditem.hls

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsTransferAndPlaylistSafetyTest {
    @Test fun acceptWholeSegmentsOnly() {
        requireCompleteHlsSegmentResponse(200)
        assertFailsWith<IOException> { requireCompleteHlsSegmentResponse(206) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentResponse(204) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentResponse(302) }
    }

    @Test fun doNotMarkTruncatedOrEmptySegmentsAsCompleted() {
        requireCompleteHlsSegmentBytes(1024L, 1024L)
        requireCompleteHlsSegmentBytes(null, 1L)
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(1024L, 700L) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(1024L, 1050L) }
        assertFailsWith<IOException> { requireCompleteHlsSegmentBytes(null, 0L) }
    }

    @Test fun boundedPlaylistReaderAcceptsExactLimitAndRejectsOneByteMore() {
        assertEquals("hello", readBoundedHlsPlaylist(ByteArrayInputStream("hello".toByteArray()), 5))
        assertFailsWith<IOException> {
            readBoundedHlsPlaylist(ByteArrayInputStream("hello!".toByteArray()), 5)
        }
    }

    @Test fun chunkedPlaylistCannotBypassLimitUsingUnknownLength() {
        val oversized = ByteArray(257) { 65 }
        assertFailsWith<IOException> {
            readBoundedHlsPlaylist(ByteArrayInputStream(oversized), 256)
        }
    }

    @Test fun mediaTypeParametersAreAcceptedWithoutAcceptingPrefixSpoofs() {
        assertTrue(isHlsPlaylistContentType("application/vnd.apple.mpegurl; charset=utf-8"))
        assertTrue(isHlsPlaylistContentType("APPLICATION/X-MPEGURL"))
        assertFalse(isHlsPlaylistContentType("application/vnd.apple.mpegurl-evil"))
        assertFalse(isHlsPlaylistContentType("text/plain"))
        assertFalse(isHlsPlaylistContentType(null))
    }
}
