package ir.amirab.downloader.queue

import ir.amirab.downloader.DownloadManagerEvents
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.downloaditem.DownloadItemContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ManualDownloadQueueBatchResumeTest {
    @Test
    fun sixteenIdsAreAdmittedOnceAndExistingUserLimitIsHonored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloads = RecordingDownloads()
        val queue = ManualDownloadQueue(downloads, scope)
        try {
            queue.boot()
            queue.setMaxConcurrent(4)
            val ids = (1L..16L).toList()
            // Duplicates must never occupy multiple queue slots or start twice.
            queue.resumeAll(ids + listOf(1L, 4L, 16L))
            withTimeout(5.seconds) {
                while (downloads.started.size != 4) delay(10)
            }
            assertEquals(setOf(1L, 2L, 3L, 4L), downloads.started.toSet())

            // Raising the existing limit admits the remainder; no new
            // downloader state or bypass of maxConcurrent is introduced.
            queue.setMaxConcurrent(16)
            withTimeout(5.seconds) {
                while (downloads.started.size != 16) delay(10)
            }
            assertEquals(ids.toSet(), downloads.started.toSet())
            assertEquals(16, downloads.started.size)

            queue.resumeAll(ids)
            queue.resumeAll(emptyList())
            delay(700) // let the queue's existing 500ms debounce settle
            assertEquals(16, downloads.started.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun removingAnActiveJobAdmitsNextPendingJobWithoutCompletionEvent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloads = RecordingDownloads()
        val queue = ManualDownloadQueue(downloads, scope)
        try {
            queue.boot()
            queue.setMaxConcurrent(1)
            queue.resumeAll(listOf(41L, 42L, 43L))
            withTimeout(5.seconds) {
                while (downloads.started.size < 1) delay(10)
            }
            assertEquals(listOf(41L), downloads.started.toList())

            // A deleted job may never send OnJobCompleted/OnJobCanceled.
            // Removing it must refill its active slot instead of stranding
            // the remaining downloads until another unrelated event arrives.
            queue.removeFromQueue(41L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 2) delay(10)
            }
            assertEquals(listOf(41L, 42L), downloads.started.toList())

            // Repeated removals must not double-start the same pending job.
            queue.removeFromQueue(41L)
            delay(700)
            assertEquals(listOf(41L, 42L), downloads.started.toList())

            queue.removeFromQueue(42L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 3) delay(10)
            }
            assertEquals(listOf(41L, 42L, 43L), downloads.started.toList())
        } finally {
            scope.cancel()
        }
    }

    private class RecordingDownloads : DownloadManagerMinimalControl {
        val started = ConcurrentLinkedQueue<Long>()
        override val listOfJobsEvents: SharedFlow<DownloadManagerEvents> =
            MutableSharedFlow(extraBufferCapacity = 32)

        override suspend fun startJob(id: Long, context: DownloadItemContext) {
            started.add(id)
        }

        override suspend fun stopJob(id: Long, context: DownloadItemContext) = Unit

        override fun canActivateJob(id: Long): Boolean = true
    }
}
