package com.abdownloadmanager.shared.pages.home

import ir.amirab.downloader.monitor.CompletedDownloadItemState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisibleSelectionTest {
    private fun item(id: Long) = CompletedDownloadItemState(
        id = id,
        folder = "/fixture",
        name = "fixture-$id.bin",
        downloadLink = "https://fixture.example/file?id=$id",
        contentLength = 1L,
        saveLocation = "fixture-$id.bin",
        dateAdded = 1L,
        startTime = 2L,
        completeTime = 3L,
    )

    @Test fun selectionRetainsOriginalOrderAndObjectsWithInvisibleEntriesDropped() {
        val visible = listOf(item(4L), item(1L), item(9L))
        val selected = listOf(visible[2], item(7L), visible[0])
        val result = retainVisibleSelections(selected, visible)
        assertEquals(listOf(9L, 4L), result.map { it.id })
        assertTrue(result[0] === selected[0])
        assertTrue(result[1] === selected[2])
        assertTrue(retainVisibleSelections(selected, emptyList()).isEmpty())
        assertTrue(retainVisibleSelections(emptyList(), visible).isEmpty())
    }

    @Test fun tenThousandRowsAndSelectionsDoNotNeedNestedMembershipScans() {
        // List/selection membership only: NOT a 10k live-download GUI test.
        val visible = (0 until 10_000).map { item(it.toLong()) }
        val selected = visible.asReversed() + (10_000 until 11_000).map { item(it.toLong()) }
        val started = System.nanoTime()
        val retained = retainVisibleSelections(selected, visible)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(10_000, retained.size)
        assertEquals(9_999L, retained.first().id)
        assertEquals(0L, retained.last().id)
        assertTrue(retained.none { it.id >= 10_000L })
        println("HyperGet 10k selection filtering (no GUI): ${elapsedMs} ms")
    }
}
