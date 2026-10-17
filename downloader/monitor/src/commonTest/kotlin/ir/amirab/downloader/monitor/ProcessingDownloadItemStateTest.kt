package ir.amirab.downloader.monitor

import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.part.PartDownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessingDownloadItemStateTest {
    @Test
    fun unknownRangeLengthHasNoPercentOrRemainingTime() {
        val state = rangeState(
            contentLength = IDownloadItem.LENGTH_UNKNOWN,
            progress = 40,
            speed = 10,
        )

        assertNull(state.percent)
        assertNull(state.remainingTime)
    }

    @Test
    fun knownRangeLengthUsesExactRemainingTime() {
        val state = rangeState(contentLength = 100, progress = 40, speed = 10)

        assertEquals(40, state.percent)
        assertEquals(6L, state.remainingTime)
    }

    @Test
    fun progressPastKnownLengthNeverProducesNegativeTime() {
        val state = rangeState(contentLength = 100, progress = 120, speed = 10)

        assertEquals(0L, state.remainingTime)
    }

    @Test
    fun optimisticDurationLengthIsNotPresentedAsExactTime() {
        val state = durationState(
            contentLength = IDownloadItem.LENGTH_UNKNOWN,
            optimisticLength = 1_000,
            progress = 100,
            speed = 10,
        )

        assertNull(state.remainingTime)
    }

    @Test
    fun durationStateUsesKnownExactLengthForRemainingTime() {
        val state = durationState(
            contentLength = 1_000,
            optimisticLength = 2_000,
            progress = 100,
            speed = 100,
        )

        assertEquals(9L, state.remainingTime)
    }

    @Test
    fun totalSizeIsUnknownWhenAnyItemLengthIsUnknown() {
        val items = listOf(
            completedState(contentLength = 25),
            completedState(contentLength = IDownloadItem.LENGTH_UNKNOWN),
        )

        assertNull(items.totalContentLengthOrNull())
    }

    @Test
    fun totalSizeSumsOnlyWhenEveryLengthIsKnown() {
        val items = listOf(completedState(25), completedState(75), completedState(0))

        assertEquals(100L, items.totalContentLengthOrNull())
    }

    @Test
    fun totalSizeOverflowIsReportedAsUnknown() {
        val items = listOf(completedState(Long.MAX_VALUE), completedState(1))

        assertNull(items.totalContentLengthOrNull())
    }

    private fun rangeState(
        contentLength: Long,
        progress: Long,
        speed: Long,
    ) = RangeBasedProcessingDownloadItemState(
        id = 1,
        folder = ".",
        name = "file.bin",
        downloadLink = "https://example.test/file.bin",
        contentLength = contentLength,
        saveLocation = "file.bin",
        dateAdded = 0,
        startTime = 0,
        completeTime = 0,
        status = DownloadJobStatus.Downloading,
        speed = speed,
        parts = listOf(
            UiRangedPart(
                id = 1,
                status = PartDownloadStatus.ReceivingData,
                howMuchProceed = progress,
                percent = null,
                length = contentLength.takeIf { it >= 0 },
                partSpace = 1f,
            )
        ),
        supportResume = true,
        isWaiting = false,
    )

    private fun durationState(
        contentLength: Long,
        optimisticLength: Long,
        progress: Long,
        speed: Long,
    ) = DurationBasedProcessingDownloadItemState(
        id = 1,
        folder = ".",
        name = "stream.ts",
        downloadLink = "https://example.test/stream.m3u8",
        contentLength = contentLength,
        saveLocation = "stream.ts",
        dateAdded = 0,
        startTime = 0,
        completeTime = 0,
        status = DownloadJobStatus.Downloading,
        speed = speed,
        parts = emptyList(),
        supportResume = true,
        optimisticLength = optimisticLength,
        duration = 60.0,
        progress = progress,
        percent = 10,
        isWaiting = false,
    )

    private fun completedState(contentLength: Long) = CompletedDownloadItemState(
        id = contentLength,
        folder = ".",
        name = "file.bin",
        downloadLink = "https://example.test/file.bin",
        contentLength = contentLength,
        saveLocation = "file.bin",
        dateAdded = 0,
        startTime = 0,
        completeTime = 0,
    )
}
