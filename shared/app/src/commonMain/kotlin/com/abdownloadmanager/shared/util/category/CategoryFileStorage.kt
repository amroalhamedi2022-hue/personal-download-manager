package com.abdownloadmanager.shared.util.category

import ir.amirab.downloader.db.TransactionalFileSaver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class CategoryFileStorage(
    val file: File,
    val fileSaver: TransactionalFileSaver,
) : CategoryStorage {
    val lock = Mutex()
    override suspend fun setCategories(categories: List<Category>) {
        lock.withLock {
            fileSaver.writeObject(file, categories)
        }
    }

    override suspend fun getCategories(): List<Category> = lock.withLock {
        // On Windows, a concurrent read can hold the destination open while
        // writeObject atomically moves its .tmp file into place. Serialize
        // reads with writes so the auto-save collector never dies from an
        // AccessDeniedException caused by our own category readers.
        fileSaver.readObject(file) ?: emptyList()
    }

    override suspend fun isCategoriesSet(): Boolean {
        return file.exists()
    }
}
