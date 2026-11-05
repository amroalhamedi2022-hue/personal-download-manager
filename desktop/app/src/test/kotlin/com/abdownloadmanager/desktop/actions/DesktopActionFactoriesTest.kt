package com.abdownloadmanager.desktop.actions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopActionFactoriesTest {
    @Test
    fun deleteCompletedActionIsDisabledWhenNoCompletedDownloadsExist() = runBlocking {
        val completedIds = MutableStateFlow(emptyList<Long>())
        val deleteRequests = mutableListOf<List<Long>>()
        val action = createDeleteCompletedDownloadsAction(
            scope = this,
            completedDownloadIdsFlow = completedIds,
            requestDelete = { deleteRequests += it },
        )

        assertFalse(action.isEnabled.value)
        action()
        yield()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun deleteCompletedActionDispatchesCurrentCompletedIds() = runBlocking {
        val completedIds = MutableStateFlow(listOf(5L, 9L))
        val deleteRequests = mutableListOf<List<Long>>()
        val action = createDeleteCompletedDownloadsAction(
            scope = this,
            completedDownloadIdsFlow = completedIds,
            requestDelete = { deleteRequests += it },
        )

        assertTrue(action.isEnabled.value)
        action()
        yield()
        assertEquals(listOf(listOf(5L, 9L)), deleteRequests)
    }

    @Test
    fun deleteCompletedActionUsesLatestStateAcrossInvocations() = runBlocking {
        val completedIds = MutableStateFlow(listOf(1L))
        val deleteRequests = mutableListOf<List<Long>>()
        val action = createDeleteCompletedDownloadsAction(
            scope = this,
            completedDownloadIdsFlow = completedIds,
            requestDelete = { deleteRequests += it },
        )

        action()
        yield()
        completedIds.value = listOf(2L, 3L)
        action()
        yield()

        assertEquals(listOf(listOf(1L), listOf(2L, 3L)), deleteRequests)
    }
}
