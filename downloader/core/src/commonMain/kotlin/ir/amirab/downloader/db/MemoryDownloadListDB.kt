package ir.amirab.downloader.db

import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MemoryDownloadListDB : IDownloadListDb {

    // The list is shared by UI/lifecycle commands and independent download workers.
    // Guard duplicate detection and every structural mutation as one operation.
    private val lock = Any()
    private val list: MutableList<IDownloadItem> = mutableListOf()

    override suspend fun getAll(): List<IDownloadItem> = synchronized(lock) {
        list.toList()
    }

    override suspend fun getById(id: Long): IDownloadItem? = synchronized(lock) {
        list.find { it.id == id }
    }

    override suspend fun add(item: IDownloadItem) {
        synchronized(lock) {
            require(list.all { it.id != item.id }) {
                "duplicate download id"
            }
            list.add(item)
        }
    }

    override suspend fun update(item: IDownloadItem) {
        synchronized(lock) {
            list.indexOfFirst { it.id == item.id }
                .takeIf { it != -1 }
                ?.let { index -> list[index] = item }
        }
    }

    override suspend fun remove(item: IDownloadItem) {
        removeById(item.id)
    }

    override suspend fun removeById(itemId: Long) {
        synchronized(lock) {
            val index = list.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                list.removeAt(index)
            }
        }
    }

    override suspend fun getLastId(): Long = synchronized(lock) {
        list.maxByOrNull { it.id }?.id ?: -1
    }

    private val flow = MutableSharedFlow<List<IDownloadItem>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).also {
        // A new subscriber should see the empty database immediately, without
        // depending on an unrelated mutation to trigger the first notification.
        it.tryEmit(emptyList())
    }

    suspend fun onDbUpdate() {
        // Snapshot and publish under the same lock: a delayed publisher must not
        // overwrite a newer notification with an older database snapshot.
        synchronized(lock) {
            flow.tryEmit(list.toList())
        }
    }

    suspend fun allAsFlow() = flow
}
