package com.abdownloadmanager.shared.ui.widget.table.customtable

import androidx.compose.ui.unit.dp
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TableStatePersistenceTest {
    private object Selection : TableCell<Int> {
        override val id = "#"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(26.dp)
    }

    private object Name : SortableCell<Int> {
        override val id = "Name"
        override val name: StringSource = "Name".asStringSource()
        override val size: CellSize = CellSize.Resizeable(80.dp..240.dp, 120.dp)
        override fun comparator(): Comparator<Int> = compareBy { it }
    }

    private fun newState() = TableState(
        cells = listOf(Selection, Name),
        forceVisibleCells = listOf(Name),
        initialVisibleItems = listOf(Selection, Name),
    )

    @Test
    fun ignoresCorruptNonSortableSavedSortAndFixedColumnWidth() {
        val state = newState()
        state.load(
            TableState.SerializableTableState(
                sizes = mapOf(Selection.id to 900f, Name.id to 9999f, "Gone" to 77f),
                sortBy = TableState.SortBy(Selection.id, descending = true),
                order = listOf(Name.id, Selection.id),
                visibleCells = listOf(Name.id),
            )
        )
        assertNull(state.sortBy.value)
        assertFalse(state.customSizes.value.containsKey(Selection))
        assertEquals(240.dp, state.customSizes.value[Name])
        assertEquals(listOf(Name, Selection), state.order.value)
        assertEquals(listOf(Name), state.visibleCells.value)
    }

    @Test
    fun ignoresNonFiniteWidthsAndUnknownSortWithoutLosingVisibleColumns() {
        val state = newState()
        state.load(
            TableState.SerializableTableState(
                sizes = mapOf(Name.id to Float.NaN),
                sortBy = TableState.SortBy("Removed Column", descending = false),
                visibleCells = listOf(Name.id),
            )
        )
        assertNull(state.sortBy.value)
        assertFalse(state.customSizes.value.containsKey(Name))
        assertEquals(listOf(Name), state.visibleCells.value)
    }

    @Test
    fun validWidthSortOrderAndVisibilityRoundTripAcrossNewStateInstance() {
        val first = newState()
        first.load(
            TableState.SerializableTableState(
                sizes = mapOf(Name.id to 156f),
                sortBy = TableState.SortBy(Name.id, descending = true),
                order = listOf(Name.id, Selection.id),
                visibleCells = listOf(Name.id),
            )
        )
        val reloaded = newState()
        reloaded.load(first.save())
        assertEquals(156.dp, reloaded.customSizes.value[Name])
        assertEquals(Name.id, reloaded.save().sortBy?.name)
        assertEquals(true, reloaded.save().sortBy?.descending)
        assertEquals(listOf(Name.id, Selection.id), reloaded.save().order)
        assertEquals(listOf(Name.id), reloaded.save().visibleCells)
    }
}
