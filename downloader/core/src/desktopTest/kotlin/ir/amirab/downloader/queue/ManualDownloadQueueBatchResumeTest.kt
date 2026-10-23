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

    @Test
    fun failedAsynchronousStartReleasesItsSlotAndAdmitsTheNextJob() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloads = RecordingDownloads(failStartIds = setOf(61L))
        val queue = ManualDownloadQueue(downloads, scope)
        try {
            queue.boot()
            queue.setMaxConcurrent(1)
            queue.resumeAll(listOf(61L, 62L, 63L))

            // startJob(61) throws *before* any OnJobCanceled/Completed
            // event. The reserved slot must be released, allowing 62 to
            // start without changing the configured one-task limit.
            withTimeout(5.seconds) {
                while (downloads.started.size < 2) delay(10)
            }
            assertEquals(listOf(61L, 62L), downloads.started.toList())
            delay(700)
            assertEquals(
                listOf(61L, 62L),
                downloads.started.toList(),
                "The failed job must not spin/retry or admit the third job over the limit",
            )

            queue.removeFromQueue(62L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 3) delay(10)
            }
            assertEquals(listOf(61L, 62L, 63L), downloads.started.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pausingPendingJobPreventsUnexpectedStartAfterSlotIsFreed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloads = RecordingDownloads()
        val queue = ManualDownloadQueue(downloads, scope)
        try {
            queue.boot()
            queue.setMaxConcurrent(1)
            queue.resumeAll(listOf(71L, 72L, 73L))
            withTimeout(5.seconds) {
                while (downloads.started.size < 1) delay(10)
            }
            assertEquals(listOf(71L), downloads.started.toList())

            // stopJob(72) emits no OnJobCanceled: it never started.
            // Before the fix, 72 remained queued and started when 71 left.
            queue.pause(72L)
            queue.removeFromQueue(71L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 2) delay(10)
            }
            assertEquals(
                listOf(71L, 73L), downloads.started.toList(),
                "An explicitly paused pending job must not be auto-admitted",
            )

            // Explicit Resume is still allowed, but must respect the cap.
            queue.resumeAll(listOf(72L))
            delay(700)
            assertEquals(listOf(71L, 73L), downloads.started.toList())
            queue.removeFromQueue(73L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 3) delay(10)
            }
            assertEquals(listOf(71L, 73L, 72L), downloads.started.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun reducingConcurrencyDoesNotReuseSlotsBeforeAsynchronousStopsFinish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloads = RecordingDownloads()
        val queue = ManualDownloadQueue(downloads, scope)
        try {
            queue.boot()
            queue.setMaxConcurrent(3)
            queue.resumeAll(listOf(81L, 82L, 83L, 84L))
            withTimeout(5.seconds) {
                while (downloads.started.size < 3) delay(10)
            }
            assertEquals(setOf(81L, 82L, 83L), downloads.started.toSet())

            // stopJob deliberately emits NO cancellation event. A real network
            // transfer may keep its socket open until asynchronous shutdown.
            queue.setMaxConcurrent(1)
            withTimeout(5.seconds) {
                while (downloads.stopped.size < 2) delay(10)
            }
            assertEquals(setOf(82L, 83L), downloads.stopped.toSet())
            // A second shake must not schedule duplicate stop calls.
            queue.setMaxConcurrent(1)
            delay(700)
            assertEquals(2, downloads.stopped.size)

            // The only non-trimmed job finishes, but both stop requests are
            // still in flight. Neither reserved slot may admit job 84 yet.
            queue.removeFromQueue(81L)
            delay(700)
            assertEquals(
                setOf(81L, 82L, 83L), downloads.started.toSet(),
                "A stop request must not free a live download slot",
            )
            queue.removeFromQueue(82L)
            delay(700)
            assertEquals(setOf(81L, 82L, 83L), downloads.started.toSet())

            // Once the final stopping job is explicitly removed, the next
            // pending download can start within the one-job limit.
            queue.removeFromQueue(83L)
            withTimeout(5.seconds) {
                while (downloads.started.size < 4) delay(10)
            }
            assertEquals(listOf(81L, 82L, 83L, 84L), downloads.started.toList())
        } finally {
            scope.cancel()
        }
    }

    private class RecordingDownloads(
        private val failStartIds: Set<Long> = emptySet(),
    ) : DownloadManagerMinimalControl {
        val started = ConcurrentLinkedQueue<Long>()
        val stopped = ConcurrentLinkedQueue<Long>()
        override val listOfJobsEvents: SharedFlow<DownloadManagerEvents> =
            MutableSharedFlow(extraBufferCapacity = 32)

        override suspend fun startJob(id: Long, context: DownloadItemContext) {
            started.add(id)
            if (id in failStartIds) throw java.io.IOException("Simulated start failure before lifecycle event")
        }

        override suspend fun stopJob(id: Long, context: DownloadItemContext) {
            stopped.add(id)
        }

        override fun canActivateJob(id: Long): Boolean = true
    }
}
