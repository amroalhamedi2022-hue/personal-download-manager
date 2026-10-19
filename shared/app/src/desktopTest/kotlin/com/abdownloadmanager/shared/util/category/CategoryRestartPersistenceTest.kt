package com.abdownloadmanager.shared.util.category

import com.abdownloadmanager.shared.util.ui.IMyIcons
import ir.amirab.downloader.db.TransactionalFileSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CategoryRestartPersistenceTest {
    @Test
    fun restartRestoresCategoriesAndRepairsDuplicateMemberships() = runBlocking {
        val folder = Files.createTempDirectory("download-category-restart-").toFile()
        val storageFile = folder.resolve("categories.json")
        val initialCategories = listOf(
            category(id = 1, name = "First", items = listOf(1, 1, 2)),
            category(id = 2, name = "Second", items = listOf(2, 3, 3)),
        )

        try {
            val firstStorage = CategoryFileStorage(
                file = storageFile,
                fileSaver = TransactionalFileSaver(Json),
            )
            firstStorage.setCategories(initialCategories)
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val firstManager = categoryManager(firstStorage, firstScope)
            try {
                firstManager.boot()

                assertEquals(listOf(1L, 2L), firstManager.getCategoryById(1)?.items)
                assertEquals(listOf(3L), firstManager.getCategoryById(2)?.items)

                firstManager.addItemsToCategory(
                    categoryId = 2,
                    itemIds = listOf(1, 4, 4),
                )

                withTimeout(2.seconds) {
                    while (
                        firstStorage.getCategories() != listOf(
                            category(id = 1, name = "First", items = listOf(2)),
                            category(id = 2, name = "Second", items = listOf(3, 1, 4)),
                        )
                    ) {
                        delay(20)
                    }
                }
            } finally {
                firstScope.cancel()
            }

            val restartedStorage = CategoryFileStorage(
                file = storageFile,
                fileSaver = TransactionalFileSaver(Json),
            )
            val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val restartedManager = categoryManager(restartedStorage, restartedScope)
            try {
                restartedManager.boot()

                assertEquals(listOf(2L), restartedManager.getCategoryById(1)?.items)
                assertEquals(listOf(3L, 1L, 4L), restartedManager.getCategoryById(2)?.items)
            } finally {
                restartedScope.cancel()
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun category(
        id: Long,
        name: String,
        items: List<Long>,
    ) = Category(
        id = id,
        name = name,
        icon = "test-icon",
        path = "",
        usePath = false,
        items = items,
    )

    private fun categoryManager(
        storage: CategoryStorage,
        scope: CoroutineScope,
    ): CategoryManager {
        val unusedIcons = Proxy.newProxyInstance(
            IMyIcons::class.java.classLoader,
            arrayOf(IMyIcons::class.java),
        ) { _, _, _ -> null } as IMyIcons
        return CategoryManager(
            categoryStorage = storage,
            scope = scope,
            defaultCategoriesFactory = DefaultCategories(unusedIcons) { "." },
            categoryItemProvider = object : ICategoryItemProvider {
                override suspend fun getAll(): List<CategoryItemWithId> = emptyList()
            },
        )
    }
}
