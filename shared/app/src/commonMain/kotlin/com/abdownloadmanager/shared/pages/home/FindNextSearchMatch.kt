package com.abdownloadmanager.shared.pages.home

/**
 * Find the adjacent filtered download in the order displayed by the table.
 *
 * No query/selection means no implicit selection of an unrelated item; callers
 * should invoke this only when the search field contains a non-blank query.
 */
fun nextVisibleSearchResultId(
    visibleIds: List<Long>,
    selectedId: Long?,
    backwards: Boolean = false,
): Long? {
    if (visibleIds.isEmpty()) return null
    val current = visibleIds.indexOf(selectedId)
    val next = when {
        current < 0 -> if (backwards) visibleIds.lastIndex else 0
        backwards -> if (current == 0) visibleIds.lastIndex else current - 1
        current == visibleIds.lastIndex -> 0
        else -> current + 1
    }
    return visibleIds[next]
}
