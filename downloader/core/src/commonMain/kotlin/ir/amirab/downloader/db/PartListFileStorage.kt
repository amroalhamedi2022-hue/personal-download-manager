package ir.amirab.downloader.db

import ir.amirab.downloader.part.Parts
import ir.amirab.downloader.utils.SuspendLockList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PartListFileStorage(
    val folder: File,
    val fileSaver: TransactionalFileSaver,
) : IDownloadPartListDb {
    // One in-flight writer/reader/remover per download ID. Distinct downloads
    // still persist independently, without serializing all 16 active workers.
    private val fileLocks = SuspendLockList<Long>()

    fun getFileForId(id: Long): File {
        val resolve = folder.resolve("$id.json")
        return resolve
    }

    override suspend fun getParts(id: Long): Parts? = fileLocks.withLock(id) {
        withContext(Dispatchers.IO) {
            fileSaver.readObject(getFileForId(id))
        }
    }

    override suspend fun setParts(id: Long, parts: Parts) {
        fileLocks.withLock(id) {
            // Freeze mutable progress before switching to an I/O dispatcher.
            // The transactional saver uses a fixed .tmp/.bak path per download.
            val snapshot = parts.clone()
            withContext(Dispatchers.IO) {
                fileSaver.writeObject(getFileForId(id), snapshot)
            }
        }
    }

    override suspend fun removeParts(id: Long) {
        fileLocks.withLock(id) {
            withContext(Dispatchers.IO) {
                fileSaver.delete(getFileForId(id))
            }
        }
    }

    override suspend fun clear() {
        // A global clear versus new downloads still needs lifecycle coordination;
        // it is not claimed safe merely because individual IDs are serialized.
        kotlin.runCatching {
            folder.listFiles()
        }.getOrNull()?.let {
            for (file in it) {
                file.delete()
            }
        }
    }
}
