package ir.amirab.downloader.db

import ir.amirab.downloader.part.Parts
import ir.amirab.downloader.utils.SuspendLockList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PartListFileStorage(
    val folder: File,
    val fileSaver: TransactionalFileSaver,
) : IDownloadPartListDb {
    // A single download ID must not race its transactional .tmp/.bak files.
    // Different IDs continue writing concurrently.
    private val fileLocks = SuspendLockList<Long>()

    // Clear is an exclusive lifecycle transition: it waits for earlier I/O,
    // and new I/O waits for clear. Registration is a short JVM monitor only;
    // disk operations never hold a global lock, including under 16 workers.
    private val lifecycleGuard = Any()
    private var activeOperations = 0
    private var clearing = false
    private var clearFinished = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var idleSignal = CompletableDeferred<Unit>().apply { complete(Unit) }

    private suspend fun <T> withRegisteredOperation(block: suspend () -> T): T {
        while (true) {
            val wait: CompletableDeferred<Unit>? = synchronized(lifecycleGuard) {
                if (clearing) {
                    clearFinished
                } else {
                    if (activeOperations == 0) idleSignal = CompletableDeferred()
                    activeOperations++
                    null
                }
            }
            if (wait == null) break
            wait.await()
        }
        try {
            return block()
        } finally {
            val idle = synchronized(lifecycleGuard) {
                activeOperations--
                if (activeOperations == 0) idleSignal else null
            }
            idle?.complete(Unit)
        }
    }

    private suspend fun awaitExclusiveClear(): CompletableDeferred<Unit>? {
        while (true) {
            val (owner, wait) = synchronized(lifecycleGuard) {
                if (clearing) {
                    false to clearFinished
                } else {
                    clearing = true
                    clearFinished = CompletableDeferred()
                    true to (if (activeOperations > 0) idleSignal else null)
                }
            }
            if (owner) return wait
            wait!!.await()
        }
    }

    fun getFileForId(id: Long): File = folder.resolve("$id.json")

    override suspend fun getParts(id: Long): Parts? = withRegisteredOperation {
        fileLocks.withLock(id) {
            withContext(Dispatchers.IO) {
                fileSaver.readObject(getFileForId(id))
            }
        }
    }

    override suspend fun setParts(id: Long, parts: Parts) {
        withRegisteredOperation {
            fileLocks.withLock(id) {
                val snapshot = parts.clone()
                withContext(Dispatchers.IO) {
                    fileSaver.writeObject(getFileForId(id), snapshot)
                }
            }
        }
    }

    override suspend fun removeParts(id: Long) {
        withRegisteredOperation {
            fileLocks.withLock(id) {
                withContext(Dispatchers.IO) {
                    fileSaver.delete(getFileForId(id))
                }
            }
        }
    }

    override suspend fun clear() {
        // No writer that started before clear may resurrect a deleted .json.
        // Cancellation or filesystem failure always reopens the lifecycle gate.
        val previousOperations = awaitExclusiveClear()
        try {
            previousOperations?.await()
            withContext(Dispatchers.IO) {
                if (folder.exists()) {
                    val files = folder.listFiles()
                        ?: throw IOException("Cannot enumerate download parts directory: $folder")
                    files.forEach { Files.deleteIfExists(it.toPath()) }
                }
            }
        } finally {
            val completed = synchronized(lifecycleGuard) {
                clearing = false
                clearFinished
            }
            completed.complete(Unit)
        }
    }
}
