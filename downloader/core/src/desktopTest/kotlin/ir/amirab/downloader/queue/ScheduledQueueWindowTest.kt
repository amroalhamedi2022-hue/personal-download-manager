@file:OptIn(kotlin.time.ExperimentalTime::class)

package ir.amirab.downloader.queue

import ir.amirab.downloader.DownloadManagerEvents
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.db.DownloadQueueFileStorageDatabase
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.DownloadItemContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Real wall-clock acceptance for the queue's scheduled start and stop timers. */
class ScheduledQueueWindowTest {
    @Test
    fun scheduledStartAndStopActivateThenStopQueueExactlyOnce() = runBlocking {
        val folder = Files.createTempDirectory("hyperget-scheduled-queue-window-").toFile()
        val database = DownloadQueueFileStorageDatabase(
            fileSaver = TransactionalFileSaver(Json),
            queueFolder = folder,
        )
        val downloads = RecordingDownloads()
        val manager = QueueManager(database, downloads)
        val observedEvents = ConcurrentLinkedQueue<QueueEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            manager.queueEvents.collect { observedEvents.add(it) }
        }

        try {
            manager.boot()
            val queue = manager.getMainQueue()
            manager.addToQueue(DefaultQueueInfo.ID, 73L)

            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(zone)
            val start = now.plus(5, DateTimeUnit.SECOND).time
            val end = now.plus(11, DateTimeUnit.SECOND).time
            queue.setScheduledTimes {
                copy(
                    daysOfWeek = DayOfWeek.entries.toSet(),
                    startTime = start,
                    endTime = end,
                    enabledStartTime = true,
                    enabledEndTime = true,
                )
            }

            withTimeout(18.seconds) {
                while (
                    observedEvents.none { it is QueueEvent.OnQueueStartTimeReached } ||
                    observedEvents.none { it is QueueEvent.QueueEndTimeReached }
                ) {
                    delay(25)
                }
            }

            assertEquals(listOf(73L), downloads.started.toList(), "scheduled start must admit the queued task once")
            assertEquals(listOf(73L), downloads.stopped.toList(), "scheduled stop must stop the active task once")
            val startEvent = observedEvents.filterIsInstance<QueueEvent.OnQueueStartTimeReached>().single()
            val stopEvent = observedEvents.filterIsInstance<QueueEvent.QueueEndTimeReached>().single()
            assertFalse(startEvent.wasActive, "the queue must be inactive immediately before scheduled start")
            assertTrue(stopEvent.wasActive, "the queue must be active immediately before scheduled stop")
            assertFalse(queue.isQueueActive, "the scheduled end time must leave the queue inactive")
        } finally {
            collector.cancel()
            manager.getAll().forEach(DownloadQueue::dispose)
            folder.deleteRecursively()
        }
    }

    private class RecordingDownloads : DownloadManagerMinimalControl {
        override val listOfJobsEvents: SharedFlow<DownloadManagerEvents> =
            MutableSharedFlow(extraBufferCapacity = 32)
        val started = ConcurrentLinkedQueue<Long>()
        val stopped = ConcurrentLinkedQueue<Long>()

        override suspend fun startJob(id: Long, context: DownloadItemContext) {
            started.add(id)
        }

        override suspend fun stopJob(id: Long, context: DownloadItemContext) {
            stopped.add(id)
        }

        override fun canActivateJob(id: Long): Boolean = true
    }
}
