package ir.amirab.downloader.queue

import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.db.IDownloadQueueDatabase
import ir.amirab.downloader.db.DownloadQueuePersistedDataAccess
import ir.amirab.downloader.db.QueueModel
import ir.amirab.util.suspendGuardedEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DefaultQueueInfo {
    const val ID = 0L
    const val NAME = "Main"
}

class QueueManager(
    private val queueDb: IDownloadQueueDatabase,
    private val listOfJobs: DownloadManagerMinimalControl,
) {
    companion object {

        // we save this ids maybe later we want to add some queues
        const val RESERVED_UNTIL_QUEUE_ID = 10L
    }

    // Serialize queue creation and cross-queue moves: both are read-modify-write
    // operations that otherwise race under concurrent CLI/UI commands.
    private val queueMutationMutex = Mutex()

    val queues = MutableStateFlow(
        emptyList<DownloadQueue>()
    )

    private val _queueEvent = MutableSharedFlow<QueueEvent>(
        // send events without suspending
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val queueEvents: SharedFlow<QueueEvent> = _queueEvent.asSharedFlow()
    private fun onQueueEvent(queueEvent: QueueEvent) {
        _queueEvent.tryEmit(queueEvent)
    }


    private suspend fun addDefaultQueue() {
        val queueModel = QueueModel(
            id = DefaultQueueInfo.ID,
            name = DefaultQueueInfo.NAME,
        )
//        println("creating default queue")
        queueDb.addQueue(queueModel)
        val queue = createQueue(queueModel)
        queues.update { currentList ->
            buildList {
                add(queue)
                addAll(currentList)
            }
        }
        queue.boot()
    }

    suspend fun addQueue(
        name: String,
    ) {
        queueMutationMutex.lock()
        try {
            val maxId = queueDb
                .getAllQueueIds()
                .maxOrNull()
                ?.coerceAtLeast(RESERVED_UNTIL_QUEUE_ID) ?: RESERVED_UNTIL_QUEUE_ID
            // Reserve and persist the new ID before another caller can allocate it.
            val queueModel = QueueModel(
                id = maxId + 1,
                name = name,
            )
            queueDb.addQueue(queueModel)
            val queue = createQueue(queueModel)
            queues.update { it.plus(queue) }
            queue.boot()
        } finally {
            queueMutationMutex.unlock()
        }
    }

    private suspend fun deleteQueueUnderLock(queue: DownloadQueue) {
        if (queue.isMainQueue() || queues.value.none { it === queue }) {
            // A stale reference must never delete a newer queue that reused this ID.
            return
        }
        queue.dispose()
        queueDb.deleteQueue(queue.id)
        queues.update { existing -> existing.filterNot { it === queue } }
    }

    suspend fun deleteQueue(queue: DownloadQueue) {
        queueMutationMutex.withLock {
            deleteQueueUnderLock(queue)
        }
    }

    suspend fun deleteQueue(id: Long) {
        queueMutationMutex.withLock {
            queues.value.firstOrNull { it.id == id }?.let {
                deleteQueueUnderLock(it)
            }
        }
    }

    private var booted = suspendGuardedEntry()
    private fun ensureBooted() {
        require(booted.isDone()) {
            "please first boot QueueManager"
        }
    }

    suspend fun boot() {
        booted.action {
            val queueModels = normalizePersistedQueueModels(
                queueDb.getAllQueues()
            )
            val dbQueues = queueModels.map {
                createQueue(it)
            }
            for (queue in dbQueues) {
                queue.boot()
            }
            queues.update { dbQueues }
            val ids = queueModels.map { it.id }
            if (DefaultQueueInfo.ID !in ids) {
                addDefaultQueue()
            }
        }
    }

    fun getMainQueue(): DownloadQueue {
        ensureBooted()
        return requireNotNull(
            queues.value.find {
                it.id == DefaultQueueInfo.ID
            }
        ) { "we can't find main queue" }
    }

    private suspend fun normalizePersistedQueueModels(
        queueModels: List<QueueModel>,
    ): List<QueueModel> {
        if (queueModels.isEmpty()) {
            return emptyList()
        }

        val claimedDownloadIds = mutableSetOf<Long>()
        val normalizedByQueueId = queueModels
            .sortedWith(
                compareBy<QueueModel> { it.id == DefaultQueueInfo.ID }
                    .thenBy { it.id }
            )
            .associate { queueModel ->
                queueModel.id to queueModel.copy(
                    queueItems = queueModel.queueItems.filter(claimedDownloadIds::add)
                )
            }

        val normalized = queueModels.map { queueModel ->
            requireNotNull(normalizedByQueueId[queueModel.id])
        }
        normalized.zip(queueModels).forEach { (newModel, oldModel) ->
            if (newModel != oldModel) {
                queueDb.updateQueue(newModel)
            }
        }
        return normalized
    }

    private fun createQueue(queueModel: QueueModel): DownloadQueue {
        return DownloadQueue(
            persistedData = QueueInfoPersistedData(
                queueDb,
                queueModel.id
            ),
            downloadEvents = listOfJobs,
            persistedModel = queueModel,
            onQueueEvent = ::onQueueEvent,
        )
    }

    fun getAll(): List<DownloadQueue> {
        return queues.value
    }

    suspend fun flush() {
        getAll().forEach { it.flush() }
    }

    fun getQueue(queue: Long): DownloadQueue {
        return requireNotNull(
            queues.value.find {
                it.id == queue
            }
        )
    }

    fun canDelete(queue: Long): Boolean {
        return queue != DefaultQueueInfo.ID
        //        return if (queue in 0..RESERVED_UNTIL_QUEUE_ID) {
//            false
//        } else true
    }

    fun isItemInQueue(downloadId: Long): Boolean {
        return findItemInQueue(downloadId) != null
    }

    fun findItemInQueue(downloadId: Long): Long? {
        for (queue in queues.value) {
            for (queueItem in queue.getQueueModel().queueItems) {
                if (downloadId == queueItem) {
                    return queue.id
                }
            }
        }
        return null
    }

    suspend fun addToQueue(
        queueId: Long,
        downloadId: Long,
    ) {
        queueMutationMutex.lock()
        try {
            // Moving an item must be atomic across the two queue models.
            // Otherwise simultaneous moves can leave it in multiple queues.
            val target = getQueue(queueId)
            val foundInQueue = findItemInQueue(downloadId = downloadId)
            if (foundInQueue == queueId) return
            if (foundInQueue != null) {
                getQueue(foundInQueue).removeFromQueue(downloadId)
            }
            target.addToQueue(downloadId)
        } finally {
            queueMutationMutex.unlock()
        }
    }

    suspend fun addToQueue(queueId: Long, downloadIds: List<Long>) {
        downloadIds.forEach {
            addToQueue(queueId, it)
        }
    }

    fun clearQueue(queueId: Long) {
        val queue = getQueue(queueId)
        queue.clearQueue()
    }
}

private class QueueInfoPersistedData(
    val db: IDownloadQueueDatabase,
    val id: Long,
) : DownloadQueuePersistedDataAccess {
    var cached: QueueModel? = null
    val lock = Mutex()
    override suspend fun getModel(): QueueModel {
        if (cached == null) {
            cached = db.getQueue(id)
        }
//        println("getModel() == $cached")
        return cached!!
    }

    override suspend fun setModel(model: QueueModel) {
        if (model == cached) {
            //nothing to update
//            println("Noting to update")
            return
        }
        lock.withLock {
            db.updateQueue(model)
//            println("setModel() == $model")
            cached = model
        }
    }
}

private fun QueueManager.getActiveOrInactiveQueues(
    active: Boolean,
): Flow<List<DownloadQueue>> {
    return queues.flatMapLatest { latestQueues ->
        if (latestQueues.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                latestQueues.map { it.activeFlow }
            ) {
                it.mapIndexedNotNull { index, value ->
                    val shouldAdd = if (active) value else !value
                    if (shouldAdd) {
                        latestQueues[index]
                    } else {
                        null
                    }
                }
            }
        }
    }
}

fun QueueManager.activeQueuesFlow(): Flow<List<DownloadQueue>> {
    return getActiveOrInactiveQueues(true)
}

fun QueueManager.inactiveQueuesFlow(): Flow<List<DownloadQueue>> {
    return getActiveOrInactiveQueues(false)
}

fun QueueManager.queueModelsFlow(): Flow<List<QueueModel>> {
    return queues.flatMapLatest { queues ->
        if (queues.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                queues.map { it.queueModel }
            ) {
                it.toList()
            }
        }
    }
}

fun DownloadQueue.isMainQueue(): Boolean {
    return DefaultQueueInfo.ID == id
}
