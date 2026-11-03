package ir.amirab.downloader.downloaditem.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Resume safety when the same relative HLS filename moves to another origin/path. */
class HlsSegmentIdentityTest {
    private val first = "https://media.example.test/a/index.m3u8"
    private val moved = "https://media.example.test/b/index.m3u8"

    @Test fun identicalFinalPlaylistAndSegmentPreservesCompletedBytes() {
        val saved = resolveHlsSegmentIdentity(first, "clip.ts?sig=one")
        val fresh = resolveHlsSegmentIdentity(first, "clip.ts?sig=one")
        val previous = listOf(HlsSegmentSnapshot(1, saved, 4.0, completed = true, length = 100, current = 100))
        assertTrue(reconcileHlsSegments(previous, listOf(HlsSegmentSnapshot(1, fresh, 4.0))).single().completed)
    }

    @Test fun redirectedPlaylistDirectoryInvalidatesIdenticalRelativeFilename() {
        val old = resolveHlsSegmentIdentity(first, "clip.ts")
        val fresh = resolveHlsSegmentIdentity(moved, "clip.ts")
        val result = reconcileHlsSegments(
            listOf(HlsSegmentSnapshot(1, old, 4.0, completed = true, length = 100, current = 100)),
            listOf(HlsSegmentSnapshot(1, fresh, 4.0)),
        ).single()
        assertFalse(result.completed)
        assertEquals(null, result.length)
        assertEquals(0L, result.current)
    }

    @Test fun redirectedPlaylistHostInvalidatesSameRelativeFilename() {
        val saved = resolveHlsSegmentIdentity(first, "clip.ts")
        val fresh = resolveHlsSegmentIdentity("https://other.example.test/a/index.m3u8", "clip.ts")
        assertFalse(reconcileHlsSegments(
            listOf(HlsSegmentSnapshot(1, saved, 4.0, completed = true)),
            listOf(HlsSegmentSnapshot(1, fresh, 4.0)),
        ).single().completed)
    }

    @Test fun absoluteSegmentRetainsIdentityWhenPlaylistMoves() {
        val uri = "https://cdn.example.test/clip.ts?sig=one"
        assertEquals(resolveHlsSegmentIdentity(first, uri), resolveHlsSegmentIdentity(moved, uri))
    }

    @Test fun legacyRelativeIdentityIsConservativelyInvalidated() {
        val resolved = resolveHlsSegmentIdentity(first, "clip.ts")
        assertFalse(reconcileHlsSegments(
            listOf(HlsSegmentSnapshot(1, "clip.ts", 4.0, completed = true)),
            listOf(HlsSegmentSnapshot(1, resolved, 4.0)),
        ).single().completed)
    }

    @Test fun signedQueryAndFragmentFollowHttpResourceIdentity() {
        val saved = resolveHlsSegmentIdentity(first, "clip.ts?sig=one#chapter")
        val same = resolveHlsSegmentIdentity(first, "clip.ts?sig=one")
        val changed = resolveHlsSegmentIdentity(first, "clip.ts?sig=two")
        assertEquals(saved, same)
        assertFalse(saved == changed)
    }

    @Test fun embeddedSegmentUrlCredentialsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            resolveHlsSegmentIdentity(first, "https://user:password@cdn.example.test/clip.ts")
        }
    }
}
