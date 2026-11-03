package com.abdownloadmanager.desktop.storage

import com.abdownloadmanager.shared.storage.appsettings.AppSettingsModel
import com.abdownloadmanager.shared.storage.appsettings.PlatformDefaultSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopSettingsBackupTest {
    private fun snapshot(): AppSettingsModel {
        val defaults = PlatformDefaultSettings
        return AppSettingsModel(
            theme = defaults.theme,
            defaultDarkTheme = defaults.defaultDarkTheme,
            defaultLightTheme = defaults.defaultLightTheme,
            language = defaults.language,
            font = defaults.font,
            uiScale = defaults.uiScale,
            showIconLabels = defaults.showIconLabels,
            useRelativeDateTime = defaults.useRelativeDateTime,
            threadCount = defaults.threadCount,
            maxConcurrentDownloads = defaults.maxConcurrentDownloads,
            maxDownloadRetryCount = defaults.maxDownloadRetryCount,
            dynamicPartCreation = defaults.dynamicPartCreation,
            useServerLastModifiedTime = defaults.useServerLastModifiedTime,
            appendExtensionToIncompleteDownloads = defaults.appendExtensionToIncompleteDownloads,
            useSparseFileAllocation = defaults.useSparseFileAllocation,
            useAverageSpeed = defaults.useAverageSpeed,
            showDownloadProgressDialog = defaults.showDownloadProgressDialog,
            showDownloadCompletionDialog = defaults.showDownloadCompletionDialog,
            useSpeedLimit = defaults.useSpeedLimit,
            speedLimit = defaults.speedLimit,
            minPartSize = defaults.minPartSize,
            autoStartOnBoot = defaults.autoStartOnBoot,
            notificationSound = defaults.notificationSound,
            generalNotificationSound = defaults.generalNotificationSound,
            errorNotificationSound = defaults.errorNotificationSound,
            successNotificationSound = defaults.successNotificationSound,
            defaultDownloadFolder = defaults.defaultDownloadFolder,
            apiEnabled = defaults.apiEnabled,
            apiPort = defaults.apiPort,
            apiAuthKey = "TEST-SECRET-NEVER-EXPORT",
            apiAuthEnabled = defaults.apiAuthEnabled,
            trackDeletedFilesOnDisk = defaults.trackDeletedFilesOnDisk,
            deletePartialFileOnDownloadCancellation = defaults.deletePartialFileOnDownloadCancellation,
            sizeUnit = defaults.sizeUnit,
            speedUnit = defaults.speedUnit,
            ignoreSSLCertificates = defaults.ignoreSSLCertificates,
            useCategoryByDefault = defaults.useCategoryByDefault,
            userAgent = defaults.userAgent,
            mergeTopBarWithTitleBar = defaults.mergeTopBarWithTitleBar,
            useNativeMenuBar = defaults.useNativeMenuBar,
            useSystemTray = defaults.useSystemTray,
        )
    }

    private fun withSetting(backup: String, name: String, value: JsonPrimitive): String {
        val parsed = Json.parseToJsonElement(backup).jsonObject
        val preferences = parsed.getValue("settings").jsonObject
        return Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(parsed + ("settings" to JsonObject(preferences + (name to value))))
        )
    }

    @Test
    fun exportDoesNotIncludeSecurityOrBrowserSecrets() {
        val model = snapshot().copy(apiEnabled = true, apiAuthEnabled = true,
            ignoreSSLCertificates = true, autoStartOnBoot = true)
        val text = DesktopSettingsBackup.export(model)
        for (sensitive in listOf(
            "apiAuthKey", "apiAuthEnabled", "apiEnabled", "apiPort",
            "ignoreSSLCertificates", "autoStartOnBoot", "TEST-SECRET-NEVER-EXPORT"
        )) {
            assertFalse(text.contains(sensitive), "Export leaked sensitive key: $sensitive")
        }
        assertTrue(text.contains("\"threadCount\""))
        assertTrue(text.contains("\"defaultDownloadFolder\""))
    }

    @Test
    fun roundTripRestoresSafeSettingsButNeverChangesProtectedFields() {
        val source = snapshot().copy(threadCount = 12, maxConcurrentDownloads = 8,
            maxDownloadRetryCount = 7, userAgent = "HyperGet/Test")
        val installed = snapshot().copy(
            threadCount = 2, maxConcurrentDownloads = 1,
            apiAuthKey = "EXISTING-PRIVATE-TOKEN",
            apiEnabled = false, apiAuthEnabled = true, apiPort = 24400,
            ignoreSSLCertificates = false, autoStartOnBoot = false
        )
        val result = DesktopSettingsBackup.restored(installed, DesktopSettingsBackup.export(source))
        assertEquals(12, result.threadCount)
        assertEquals(8, result.maxConcurrentDownloads)
        assertEquals(7, result.maxDownloadRetryCount)
        assertEquals("HyperGet/Test", result.userAgent)
        assertEquals("EXISTING-PRIVATE-TOKEN", result.apiAuthKey)
        assertFalse(result.apiEnabled)
        assertTrue(result.apiAuthEnabled)
        assertEquals(24400, result.apiPort)
        assertFalse(result.ignoreSSLCertificates)
        assertFalse(result.autoStartOnBoot)
    }

    @Test
    fun rejectsInjectedSecretOrUnsafePreference() {
        val existing = snapshot()
        val backup = DesktopSettingsBackup.export(existing)
        for (key in listOf("apiAuthKey", "ignoreSSLCertificates", "apiEnabled")) {
            val poisoned = withSetting(backup, key, JsonPrimitive("bad"))
            assertFailsWith<IllegalArgumentException> {
                DesktopSettingsBackup.restored(existing, poisoned)
            }
        }
    }

    @Test
    fun rejectsCorruptVersionAndOversizedPayload() {
        val existing = snapshot()
        assertFailsWith<IllegalArgumentException> {
            DesktopSettingsBackup.restored(existing, "{invalid json")
        }
        val wrongVersion = DesktopSettingsBackup.export(existing).replace(
            "\"version\": 1", "\"version\": 999"
        )
        assertFailsWith<IllegalArgumentException> {
            DesktopSettingsBackup.restored(existing, wrongVersion)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSettingsBackup.restored(existing, "x".repeat(DesktopSettingsBackup.MAX_BACKUP_BYTES + 1))
        }
    }

    @Test
    fun rejectsDangerousConnectionLimits() {
        val existing = snapshot()
        for ((name, value) in listOf(
            "threadCount" to -1,
            "maxConcurrentDownloads" to -100,
            "maxDownloadRetryCount" to -1,
            "threadCount" to 999999
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopSettingsBackup.restored(
                    existing, withSetting(DesktopSettingsBackup.export(existing), name, JsonPrimitive(value))
                )
            }
        }
    }
}
