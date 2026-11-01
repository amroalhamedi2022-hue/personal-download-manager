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
        val selected = listOf(9L, 7L, 4L)
        val result = retainVisibleSelections(selected, visible)
        assertEquals(listOf(9L, 4L), result)
        assertEquals(9L, result[0])
        assertEquals(4L, result[1])
        assertTrue(retainVisibleSelections(selected, emptyList()).isEmpty())
        assertTrue(retainVisibleSelections(emptyList(), visible).isEmpty())
    }

    @Test fun selectionContentLookupPreservesSelectionOrderAndVisibleItems() {
        val visible = listOf(item(3L), item(10L), item(1L))
        val selected = listOf(10L, 99L, 1L)
        val result = findVisibleSelectedItems(selected, visible)
        assertEquals(listOf(10L, 1L), result.map { it.id })
        assertTrue(result[0] === visible[1])
        assertTrue(result[1] === visible[2])
        assertTrue(findVisibleSelectedItems(selected, emptyList()).isEmpty())
        assertTrue(findVisibleSelectedItems(emptyList(), visible).isEmpty())
    }

    @Test fun tenThousandSelectedItemLookupsKeepItemIdentityAndOrder() {
        val visible = (0L until 10_000L).map { item(it) }
        val selected = (9_999L downTo 0L).toList() + listOf(11_000L)
        val result = findVisibleSelectedItems(selected, visible)
        assertEquals(10_000, result.size)
        assertEquals(9_999L, result.first().id)
        assertEquals(0L, result.last().id)
        assertTrue(result[0] === visible[9999])
        assertTrue(result.last() === visible[0])
    }

    @Test fun tenThousandRowsAndSelectionsDoNotNeedNestedMembershipScans() {
        // List/selection membership only: NOT a 10k live-download GUI test.
        val visible = (0 until 10_000).map { item(it.toLong()) }
        val selected = (9_999L downTo 0L).toList() + (10_000L until 11_000L).toList()
        val started = System.nanoTime()
        val retained = retainVisibleSelections(selected, visible)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(10_000, retained.size)
        assertEquals(9_999L, retained.first())
        assertEquals(0L, retained.last())
        assertTrue(retained.none { it >= 10_000L })
        println("HyperGet 10k selection filtering (no GUI): ${elapsedMs} ms")
    }
}
