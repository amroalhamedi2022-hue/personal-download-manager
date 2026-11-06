package ir.amirab.downloader

import ir.amirab.downloader.downloaditem.DownloadJobStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveJobCountTest {
    @Test fun countsEveryActiveLifecyclePhaseWithoutCountingIdleOrFinished() {
        val statuses = listOf(
            DownloadJobStatus.Downloading,
            DownloadJobStatus.Resuming,
            DownloadJobStatus.Retrying(1000L),
            DownloadJobStatus.PreparingFile(50),
            DownloadJobStatus.IDLE,
            DownloadJobStatus.Finished,
        )
        assertEquals(4, countActiveJobs(statuses) { it })
    }

    @Test fun countsSixteenActiveTransfersWithoutDuplicatingRegistry() {
        val statuses = List(16) { DownloadJobStatus.Downloading }
        assertEquals(16, countActiveJobs(statuses) { it })
        assertEquals(16, statuses.size)
        assertEquals(0, countActiveJobs(emptyList<DownloadJobStatus>()) { it })
    }
}
