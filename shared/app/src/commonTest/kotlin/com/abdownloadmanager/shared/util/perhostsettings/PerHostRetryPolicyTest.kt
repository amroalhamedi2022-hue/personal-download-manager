package com.abdownloadmanager.shared.util.perhostsettings

import com.abdownloadmanager.shared.downloaderinui.http.applyToHttpDownload
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PerHostRetryPolicyTest {
    @Test
    fun retryOverrideRoundTripsAndLegacyDataInheritsGlobalPolicy() {
        val configured = PerHostSettingsItem(
            host = "*.example.test",
            maxDownloadRetryCount = 4,
        )

        val encoded = Json.encodeToString(configured)
        assertEquals(configured, Json.decodeFromString<PerHostSettingsItem>(encoded))
        assertNull(Json.decodeFromString<PerHostSettingsItem>("""{"host":"example.test"}""").maxDownloadRetryCount)
    }

    @Test
    fun mostSpecificHostPolicyIsPersistedIntoNewDownload() {
        val storage = object : IPerHostSettingsStorage {
            override val perHostSettingsFlow = MutableStateFlow(
                listOf(
                    PerHostSettingsItem("*.example.test", maxDownloadRetryCount = 8),
                    PerHostSettingsItem("downloads.example.test", maxDownloadRetryCount = 2),
                )
            )
        }
        val policy = PerHostSettingsManager(storage)
            .getSettingsForURL("https://downloads.example.test/file.bin")!!
        val original = HttpDownloadItem(
            link = "https://downloads.example.test/file.bin",
            id = 1,
            folder = "downloads",
            name = "file.bin",
        )

        val configured = policy.applyToHttpDownload(original)

        assertEquals(2, configured.maxRetryCount)
        assertNull(original.maxRetryCount)
    }

    @Test
    fun invalidNegativeStoredValueCannotDisableRetriesByAccident() {
        val original = HttpDownloadItem(
            link = "https://example.test/file.bin",
            id = 1,
            folder = "downloads",
            name = "file.bin",
        )

        val configured = PerHostSettingsItem(
            host = "example.test",
            maxDownloadRetryCount = -5,
        ).applyToHttpDownload(original)

        assertEquals(0, configured.maxRetryCount)
    }
}
