package ir.amirab.downloader

import ir.amirab.downloader.downloaditem.DownloadJobStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopAllEligibilityTest {
    @Test fun stopsAllActivePhasesNotOnlyDownloading() {
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.Downloading, null))
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.Resuming, null))
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.Retrying(20_000L), null))
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.PreparingFile(50), null))
    }

    @Test fun stopsPersistedRetriesEvenBeforeInMemoryRetryStatusIsRehydrated() {
        val waitingRetryAt = System.currentTimeMillis() + 60_000L
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.IDLE, waitingRetryAt))
        assertTrue(shouldStopOnStopAll(DownloadJobStatus.Canceled(kotlinx.coroutines.CancellationException()), waitingRetryAt))
    }

    @Test fun leavesTrulyInactiveAndCompletedJobsAlone() {
        assertFalse(shouldStopOnStopAll(DownloadJobStatus.IDLE, null))
        assertFalse(shouldStopOnStopAll(DownloadJobStatus.Finished, null))
        assertFalse(shouldStopOnStopAll(DownloadJobStatus.Canceled(kotlinx.coroutines.CancellationException()), null))
    }
}
