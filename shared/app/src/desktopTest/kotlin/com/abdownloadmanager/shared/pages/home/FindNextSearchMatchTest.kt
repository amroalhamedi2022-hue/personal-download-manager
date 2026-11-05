package com.abdownloadmanager.shared.pages.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindNextSearchMatchTest {
    private val visible = listOf(8L, 4L, 12L)

    @Test fun forwardSearchUsesVisibleTableOrderAndWraps() {
        assertEquals(8L, nextVisibleSearchResultId(visible, null))
        assertEquals(4L, nextVisibleSearchResultId(visible, 8L))
        assertEquals(12L, nextVisibleSearchResultId(visible, 4L))
        assertEquals(8L, nextVisibleSearchResultId(visible, 12L))
    }

    @Test fun reverseSearchUsesVisibleTableOrderAndWraps() {
        assertEquals(12L, nextVisibleSearchResultId(visible, null, backwards = true))
        assertEquals(12L, nextVisibleSearchResultId(visible, 8L, backwards = true))
        assertEquals(4L, nextVisibleSearchResultId(visible, 12L, backwards = true))
    }

    @Test fun invisibleSelectionsAndNoMatchesAreHandledSafely() {
        assertEquals(8L, nextVisibleSearchResultId(visible, 99L))
        assertEquals(12L, nextVisibleSearchResultId(visible, 99L, backwards = true))
        assertNull(nextVisibleSearchResultId(emptyList(), 99L))
        assertEquals(6L, nextVisibleSearchResultId(listOf(6L), 6L))
    }

    @Test fun tenThousandRowsWrapWithoutChangingDownloadState() {
        val rows = (0L until 10_000L).toList()
        assertEquals(0L, nextVisibleSearchResultId(rows, 9_999L))
        assertEquals(9_999L, nextVisibleSearchResultId(rows, 0L, backwards = true))
        assertEquals(5001L, nextVisibleSearchResultId(rows, 5_000L))
    }
}
