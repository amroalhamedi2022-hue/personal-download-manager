package ir.amirab.downloader.queue

import ir.amirab.downloader.DownloadManagerEvents
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.db.DownloadQueueFileStorageDatabase
import ir.amirab.downloader.db.QueueModel
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.DownloadItemContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RestartPersistenceTest {
    @Test
    fun explicitFlushPersistsQueueChangeWithoutWaitingForCollector() = runBlocking {
        val folder = Files.createTempDirectory("download-queue-flush-").toFile()
        val database = DownloadQueueFileStorageDatabase(
            fileSaver = TransactionalFileSaver(Json),
            queueFolder = folder,
        )
        try {
            database.addQueue(QueueModel(id = DefaultQueueInfo.ID, name = DefaultQueueInfo.NAME))
            val manager = QueueManager(database, InactiveDownloads())
            try {
                manager.boot()
                manager.getMainQueue().setName("Saved immediately")
                manager.flush()

                assertEquals("Saved immediately", database.getQueue(DefaultQueueInfo.ID).name)
            } finally {
                manager.getAll().forEach(DownloadQueue::dispose)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun restartRestoresQueueSettingsAndRepairsDuplicateMemberships() = runBlocking {
        val folder = Files.createTempDirectory("download-queue-restart-").toFile()
        val schedule = ScheduleTimes(
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            startTime = LocalTime(4, 15),
            endTime = LocalTime(7, 45),
            enabledStartTime = false,
            enabledEndTime = false,
        )

        try {
            val firstDatabase = DownloadQueueFileStorageDatabase(
                fileSaver = TransactionalFileSaver(Json),
                queueFolder = folder,
            )
            firstDatabase.addQueue(
                QueueModel(
                    id = DefaultQueueInfo.ID,
                    name = DefaultQueueInfo.NAME,
                    queueItems = listOf(7, 7, 8),
                )
            )
            firstDatabase.addQueue(
                QueueModel(
                    id = 11,
                    name = "Night",
                    queueItems = listOf(8, 9, 9),
                )
            )

            val firstManager = QueueManager(firstDatabase, InactiveDownloads())
            firstManager.boot()

            val customQueue = firstManager.getQueue(11)
            customQueue.setName("Night downloads")
            customQueue.setMaxConcurrent(4)
            customQueue.setStopQueueOnEmpty(true)
            customQueue.setScheduledTimes { schedule }
            firstManager.addToQueue(queueId = 11, downloadId = 10)

            withTimeout(2.seconds) {
                while (
                    firstDatabase.getQueue(11) != QueueModel(
                        id = 11,
                        name = "Night downloads",
                        maxConcurrent = 4,
                        queueItems = listOf(8, 9, 10),
                        scheduledTimes = schedule,
                        stopQueueOnEmpty = true,
                    )
                ) {
                    delay(10)
                }
            }
            firstManager.getAll().forEach(DownloadQueue::dispose)

            val restartedDatabase = DownloadQueueFileStorageDatabase(
                fileSaver = TransactionalFileSaver(Json),
                queueFolder = folder,
            )
            val restartedManager = QueueManager(restartedDatabase, InactiveDownloads())
            try {
                restartedManager.boot()

                assertEquals(listOf(7L), restartedManager.getMainQueue().getQueueModel().queueItems)
                assertEquals(
                    QueueModel(
                        id = 11,
                        name = "Night downloads",
                        maxConcurrent = 4,
                        queueItems = listOf(8, 9, 10),
                        scheduledTimes = schedule,
                        stopQueueOnEmpty = true,
                    ),
                    restartedManager.getQueue(11).getQueueModel(),
                )
                assertEquals(
                    listOf(DefaultQueueInfo.ID, 11L),
                    restartedDatabase.getAllQueueIds(),
                )
            } finally {
                restartedManager.getAll().forEach(DownloadQueue::dispose)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    private class InactiveDownloads : DownloadManagerMinimalControl {
        override val listOfJobsEvents: SharedFlow<DownloadManagerEvents> =
            MutableSharedFlow(extraBufferCapacity = 16)

        override suspend fun startJob(id: Long, context: DownloadItemContext) = Unit

        override suspend fun stopJob(id: Long, context: DownloadItemContext) = Unit

        override fun canActivateJob(id: Long): Boolean = false
    }
}
