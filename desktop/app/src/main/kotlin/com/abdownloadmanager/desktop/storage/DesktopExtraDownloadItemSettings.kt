package com.abdownloadmanager.desktop.storage

import com.abdownloadmanager.shared.storage.IExtraDownloadItemSettings
import ir.amirab.util.desktop.poweraction.ContainsPowerActionConfigOnFinish
import ir.amirab.util.desktop.poweraction.PowerActionConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class DesktopExtraDownloadItemSettings(
    override val id: Long,
    val powerActionTypeOnFinish: PowerActionConfig.Type? = null,
    val powerActionUseForceOnFinish: Boolean = false,
    // Null follows the global preference. A non-null per-download choice must
    // survive closing and reopening the file progress window and application.
    val showCompletionDialogOverride: Boolean? = null,
) : IExtraDownloadItemSettings, ContainsPowerActionConfigOnFinish {

    override fun getPowerActionConfigOnFinish() = powerActionTypeOnFinish?.let {
        PowerActionConfig(
            powerActionTypeOnFinish,
            powerActionUseForceOnFinish,
        )
    }

    companion object : IExtraDownloadItemSettings.DataClassDefinitions<DesktopExtraDownloadItemSettings> {
        override fun createDefault(id: Long) = DesktopExtraDownloadItemSettings(id = id)
        override val serializer: KSerializer<DesktopExtraDownloadItemSettings> =
            DesktopExtraDownloadItemSettings.serializer()
    }
}
