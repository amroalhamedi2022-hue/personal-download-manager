package ir.amirab.downloader

import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.hls.HLSDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestartPersistenceSafetyTest {
    private val json = Json

    @Test
    fun bootSelectsEachPendingIdOnlyOnceAndKeepsFirstPersistedRecord() {
        val first = HttpDownloadItem(
            link = "https://example.test/first",
            id = 3,
            folder = "downloads",
            name = "first.bin",
            dateAdded = 1,
        )
        val duplicated = first.copy(
            link = "https://example.test/duplicated",
            name = "duplicated.bin",
        )
        val completed = HttpDownloadItem(
            link = "https://example.test/completed",
            id = 4,
            folder = "downloads",
            name = "completed.bin",
            dateAdded = 2,
            status = DownloadStatus.Completed,
        )
        val pending = HttpDownloadItem(
            link = "https://example.test/pending",
            id = 5,
            folder = "downloads",
            name = "pending.bin",
            dateAdded = 3,
        )

        val selected = pendingDownloadsToBoot(listOf(first, duplicated, completed, pending))

        assertEquals(listOf(3L, 5L), selected.map { it.id })
        assertEquals("first.bin", selected.first().name)
    }

    @Test
    fun httpRetryStateRoundTripsAndLegacySnapshotUsesSafeDefaults() {
        val item = HttpDownloadItem(
            link = "https://example.test/file",
            id = 7,
            folder = "downloads",
            name = "file.bin",
            dateAdded = 1,
            retryAttempt = 2,
            retryAt = 1_234_567L,
        )

        val encoded = json.encodeToString(HttpDownloadItem.serializer(), item)
        val restored = json.decodeFromString(HttpDownloadItem.serializer(), encoded)
        assertEquals(2, restored.retryAttempt)
        assertEquals(1_234_567L, restored.retryAt)

        val legacy = JsonObject(
            json.parseToJsonElement(encoded).jsonObject
                .filterKeys { it != "retryAttempt" && it != "retryAt" }
        )
        val legacyRestored = json.decodeFromString(HttpDownloadItem.serializer(), legacy.toString())
        assertEquals(0, legacyRestored.retryAttempt)
        assertNull(legacyRestored.retryAt)
    }

    @Test
    fun hlsRetryStateRoundTripsAndLegacySnapshotUsesSafeDefaults() {
        val item = HLSDownloadItem(
            id = 9,
            folder = "downloads",
            name = "video.mp4",
            link = "https://example.test/playlist.m3u8",
            dateAdded = 1,
            retryAttempt = 3,
            retryAt = 9_876_543L,
        )

        val encoded = json.encodeToString(HLSDownloadItem.serializer(), item)
        val restored = json.decodeFromString(HLSDownloadItem.serializer(), encoded)
        assertEquals(3, restored.retryAttempt)
        assertEquals(9_876_543L, restored.retryAt)

        val legacy = JsonObject(
            json.parseToJsonElement(encoded).jsonObject
                .filterKeys { it != "retryAttempt" && it != "retryAt" }
        )
        val legacyRestored = json.decodeFromString(HLSDownloadItem.serializer(), legacy.toString())
        assertEquals(0, legacyRestored.retryAttempt)
        assertNull(legacyRestored.retryAt)
    }
}
