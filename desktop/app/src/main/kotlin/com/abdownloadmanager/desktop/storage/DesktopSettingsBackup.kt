package com.abdownloadmanager.desktop.storage

import com.abdownloadmanager.shared.storage.appsettings.AppSettingsModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Portable, explicitly non-secret desktop preferences. Never serialize the
 * DataStore file itself: it also contains the browser integration API token.
 *
 * The allowlist is intentional. A future sensitive preference cannot be
 * exported or silently enabled from untrusted backups by default.
 */
internal object DesktopSettingsBackup {
    private const val FORMAT = "hyperget-desktop-settings"
    private const val VERSION = 1
    const val MAX_BACKUP_BYTES = 256 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val portableKeys = setOf(
        "theme", "defaultDarkTheme", "defaultLightTheme", "language", "font", "uiScale",
        "showIconLabels", "useRelativeDateTime", "threadCount", "maxConcurrentDownloads",
        "maxDownloadRetryCount", "dynamicPartCreation", "useServerLastModifiedTime",
        "appendExtensionToIncompleteDownloads", "useSparseFileAllocation", "useAverageSpeed",
        "showDownloadProgressDialog", "showDownloadCompletionDialog", "useSpeedLimit",
        "speedLimit", "minPartSize", "notificationSound", "generalNotificationSound",
        "errorNotificationSound", "successNotificationSound", "defaultDownloadFolder",
        "trackDeletedFilesOnDisk", "deletePartialFileOnDownloadCancellation", "sizeUnit",
        "speedUnit", "useCategoryByDefault", "userAgent",
        "mergeTopBarWithTitleBar", "useNativeMenuBar", "useSystemTray"
    )

    fun export(current: AppSettingsModel): String {
        val model = json.encodeToJsonElement(current).jsonObject
        val portable = JsonObject(model.filterKeys { it in portableKeys })
        val envelope = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("settings", portable)
        }
        return json.encodeToString(envelope)
    }

    /** Pure validation runs before one atomic DataStore.updateData transaction. */
    fun restored(current: AppSettingsModel, backup: String): AppSettingsModel {
        require(backup.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "Settings backup is too large."
        }
        val envelope = json.parseToJsonElement(backup).jsonObject
        require(envelope.keys == setOf("format", "version", "settings")) {
            "Invalid settings backup envelope."
        }
        require(envelope["format"]?.jsonPrimitive?.content == FORMAT) {
            "Not a HyperGet settings backup."
        }
        require(envelope["version"]?.jsonPrimitive?.int == VERSION) {
            "Unsupported settings backup version."
        }
        val settings = envelope["settings"]?.jsonObject
            ?: throw IllegalArgumentException("Missing settings object.")
        require(settings.isNotEmpty() && settings.keys.all { it in portableKeys }) {
            "Backup contains unsupported or sensitive settings."
        }

        // Overlay only approved fields onto the current installed model. This
        // preserves the existing browser token, IPC/port, security settings,
        // TLS verification choice and Windows startup registration.
        val original = json.encodeToJsonElement(current).jsonObject
        val restored = json.decodeFromJsonElement<AppSettingsModel>(
            JsonObject(original + settings)
        )
        require(restored.threadCount in 1..256) { "Invalid connection count." }
        require(restored.maxConcurrentDownloads in 0..256) { "Invalid concurrent limit." }
        require(restored.maxDownloadRetryCount in 0..100) { "Invalid retry count." }
        require(restored.speedLimit > 0L) { "Invalid speed limit." }
        require(restored.minPartSize > 0L) { "Invalid part size." }
        require(restored.uiScale == null ||
            restored.uiScale in 0.5f..3f) { "Invalid UI scale." }
        require(restored.defaultDownloadFolder.isNotBlank() &&
            restored.defaultDownloadFolder.length <= 4096) { "Invalid download folder." }
        require(restored.userAgent.length <= 1024) { "Invalid user agent." }
        return restored
    }
}
