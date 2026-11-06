package ir.amirab.downloader.db

import ir.amirab.downloader.part.Parts

/**
 * In-memory part state is accessed by independent download workers and lifecycle
 * commands. Guard the map as a single critical section so clear/remove/set do
 * not race with reads during concurrent transfers.
 */
class MemoryDownloadPartStatesDB : IDownloadPartListDb {
    private val lock = Any()
    private val list = mutableMapOf<Long, Parts>()

    override suspend fun getParts(id: Long): Parts? = synchronized(lock) {
        // Never expose the stored Parts instance to callers that may mutate it.
        list[id]?.clone()
    }

    override suspend fun setParts(id: Long, parts: Parts) {
        synchronized(lock) {
            list[id] = parts.clone()
        }
    }

    override suspend fun removeParts(id: Long) {
        synchronized(lock) {
            list.remove(id)
        }
    }

    override suspend fun clear() {
        synchronized(lock) {
            list.clear()
        }
    }
}
