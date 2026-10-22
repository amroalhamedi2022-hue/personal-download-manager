package com.abdownloadmanager.desktop.pages.enterurl

import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.pages.enterurl.BaseEnterNewURLComponent
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.ftp.FtpDownloadCredentials
import ir.amirab.util.FileNameValidator
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI

class DesktopEnterNewURLComponent(
    ctx: ComponentContext,
    config: Config,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    onCloseRequest: () -> Unit,
    onRequestFinished: (IDownloadCredentials) -> Unit,
    private val onRequestFtp: (FtpDownloadRequest) -> Unit,
    initialFtpFolder: String,
) : BaseEnterNewURLComponent(
    ctx = ctx,
    config = config,
    downloaderInUiRegistry = downloaderInUiRegistry,
    onCloseRequest = onCloseRequest,
    onRequestFinished = onRequestFinished,
) {
    sealed interface Effects : BaseEnterNewURLComponent.Effects.PlatformEffects {
        data object BringToFront : Effects
    }

    data object Config : BaseEnterNewURLComponent.Config

    val isFtpUrl = url.mapStateFlow { it.trim().startsWith("ftp://", ignoreCase = true) }
    val ftpUsername = MutableStateFlow("anonymous")
    val ftpPassword = MutableStateFlow("")
    val ftpFolder = MutableStateFlow(initialFtpFolder)
    val ftpFileName = MutableStateFlow("")
    private var fileNameEdited = false

    fun setFtpUsername(value: String) { ftpUsername.value = value }
    fun setFtpPassword(value: String) { ftpPassword.value = value }
    fun setFtpFolder(value: String) { ftpFolder.value = value }
    fun setFtpFileName(value: String) { fileNameEdited = true; ftpFileName.value = value }

    fun suggestFtpFileName() {
        if (fileNameEdited) return
        ftpFileName.value = runCatching {
            URI(url.value.trim()).path.substringAfterLast('/')
        }.getOrNull().orEmpty()
    }

    fun canSubmitFtp(): Boolean = runCatching {
        FtpDownloadCredentials(url.value.trim(), username = ftpUsername.value).validateCredentials()
        require(ftpFolder.value.isNotBlank())
        require(FileNameValidator.isValidFileName(ftpFileName.value))
        require(ftpUsername.value == "anonymous" || ftpPassword.value.isNotEmpty())
        true
    }.getOrDefault(false)

    fun newFtpDownloadEntered() {
        if (!canSubmitFtp()) return
        val request = FtpDownloadRequest(
            link = url.value.trim(),
            username = ftpUsername.value,
            password = if (ftpUsername.value == "anonymous") "" else ftpPassword.value,
            folder = ftpFolder.value,
            name = ftpFileName.value,
        )
        ftpPassword.value = ""
        onRequestFtp(request)
        close()
    }

    fun bringToFront() {
        sendEffect(Effects.BringToFront)
    }
}


/** In-memory only: never serialize a password from the Add URL form. */
class FtpDownloadRequest(
    val link: String,
    val username: String,
    val password: String,
    val folder: String,
    val name: String,
)
