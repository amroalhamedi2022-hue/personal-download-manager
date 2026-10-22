package ir.amirab.downloader.monitor

import ir.amirab.downloader.part.PartDownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VisibleDownloadPartsTest {
    private fun part(id: Long, state: PartDownloadStatus): UiPart = UiRangedPart(
        id = id, status = state, howMuchProceed = 0L, percent = 0,
        length = 100L, partSpace = 0.2f,
    )

    @Test
    fun defaultShowsFinishedWaitingAndCancelledAlongsideActive() {
        val all = listOf(
            part(1, PartDownloadStatus.ReceivingData),
            part(2, PartDownloadStatus.Completed),
            part(3, PartDownloadStatus.IDLE),
            part(4, PartDownloadStatus.Canceled(IllegalStateException("fixture"))),
            part(5, PartDownloadStatus.Connecting),
        )
        val selected = selectVisibleDownloadParts(all, onlyActive = false)
        assertEquals(listOf(0, 1, 2, 3, 4), selected.map { it.index })
        assertEquals(all.map { it.id }, selected.map { it.value.id })
        assertSame(all[3], selected[3].value)
    }

    @Test
    fun onlyActivePreservesOriginalNumbersAndConnectionIdentity() {
        val parts = listOf(
            part(11, PartDownloadStatus.Completed),
            part(12, PartDownloadStatus.Connecting),
            part(13, PartDownloadStatus.IDLE),
            part(14, PartDownloadStatus.ReceivingData),
        )
        val selected = selectVisibleDownloadParts(parts, onlyActive = true)
        assertEquals(listOf(1, 3), selected.map { it.index })
        assertEquals(listOf(12L, 14L), selected.map { it.value.id })
    }

    @Test
    fun filteringDoesNotMutateSourceAndEmptyInputsStayEmpty() {
        val source = listOf(part(1, PartDownloadStatus.Completed))
        assertTrue(selectVisibleDownloadParts(source, onlyActive = true).isEmpty())
        assertEquals(PartDownloadStatus.Completed, source.single().status)
        assertTrue(selectVisibleDownloadParts(emptyList(), onlyActive = false).isEmpty())
    }
}
