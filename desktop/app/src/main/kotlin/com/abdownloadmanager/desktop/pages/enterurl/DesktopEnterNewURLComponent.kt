package com.abdownloadmanager.desktop.pages.enterurl

import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.pages.enterurl.BaseEnterNewURLComponent
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.ftp.FtpDownloadCredentials
import ir.amirab.downloader.ftp.parseFtpFileUri
import ir.amirab.downloader.ftp.FtpTransferClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.amirab.util.FileNameValidator
import ir.amirab.util.PathValidator
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class DesktopEnterNewURLComponent(
    ctx: ComponentContext,
    config: Config,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    onCloseRequest: () -> Unit,
    onRequestFinished: (IDownloadCredentials) -> Unit,
    private val onRequestFtp: suspend (FtpDownloadRequest) -> Unit,
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
    val ftpStartImmediately = MutableStateFlow(true)
    val ftpSubmitInProgress = MutableStateFlow(false)
    val ftpFormError = MutableStateFlow<String?>(null)
    private var fileNameEdited = false

    data class FtpCheckState(
        val checking: Boolean = false,
        val success: Boolean = false,
        val message: String? = null,
    )
    val ftpCheckState = MutableStateFlow(FtpCheckState())
    private var ftpCheckVersion: Long = 0L

    fun clearFtpCheck() {
        // Invalidates any result from a check started for old URL/login fields.
        ftpCheckVersion += 1
        ftpCheckState.value = FtpCheckState()
    }

    fun checkFtpConnection() {
        if (ftpCheckState.value.checking) return
        val checkVersion = ++ftpCheckVersion
        val credentials = runCatching {
            FtpDownloadCredentials(url.value.trim(), username = ftpUsername.value).also {
                it.validateCredentials()
            }
        }.getOrElse {
            ftpCheckState.value = FtpCheckState(message = "Invalid FTP address or user name")
            return
        }
        val user = credentials.username
        val password = if (user == "anonymous") "anonymous@" else ftpPassword.value
        if (user != "anonymous" && password.isEmpty()) {
            ftpCheckState.value = FtpCheckState(message = "Enter the FTP password before checking")
            return
        }
        ftpCheckState.value = FtpCheckState(checking = true, message = "Checking FTP connection...")
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    FtpTransferClient().inspectRemote(credentials.link, user, password)
                }
            }
            if (checkVersion != ftpCheckVersion) return@launch
            ftpCheckState.value = outcome.fold(
                onSuccess = {
                    FtpCheckState(
                        success = true,
                        message = "FTP connection succeeded. Remote size: " +
                            (it.size?.let { bytes -> "$bytes bytes" } ?: "not provided by server"),
                    )
                },
                onFailure = {
                    FtpCheckState(
                        success = false,
                        message = it.localizedMessage ?: "FTP connection failed",
                    )
                },
            )
        }
    }

    fun setFtpUsername(value: String) { clearFtpCheck(); ftpUsername.value = value }
    fun setFtpPassword(value: String) { clearFtpCheck(); ftpPassword.value = value }
    fun setFtpFolder(value: String) { ftpFormError.value = null; ftpFolder.value = value }
    fun toggleFtpStartImmediately() {
        ftpStartImmediately.value = !ftpStartImmediately.value
    }
    fun setFtpFileName(value: String) { fileNameEdited = true; ftpFileName.value = value }

    fun suggestFtpFileName() {
        if (fileNameEdited) return
        ftpFileName.value = runCatching {
            parseFtpFileUri(url.value).path.substringAfterLast('/')
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
        if (ftpSubmitInProgress.value) return
        if (!canSubmitFtp()) {
            ftpFormError.value = "Enter a valid FTP URL, file name, folder, and account details."
            return
        }
        ftpSubmitInProgress.value = true
        ftpFormError.value = null
        val linkSnapshot = url.value.trim()
        val userSnapshot = ftpUsername.value
        val passwordSnapshot = if (userSnapshot == "anonymous") "" else ftpPassword.value
        val folderSnapshot = ftpFolder.value
        val filenameSnapshot = ftpFileName.value
        val startSnapshot = ftpStartImmediately.value
        scope.launch {
            try {
                val validDestination = withContext(Dispatchers.IO) {
                    PathValidator.isValidPath(folderSnapshot) &&
                        PathValidator.canWriteToThisPath(folderSnapshot)
                }
                if (!validDestination) {
                    ftpFormError.value = "Select an existing writable folder before adding the FTP download."
                    return@launch
                }
                onRequestFtp(
                    FtpDownloadRequest(
                        link = linkSnapshot,
                        username = userSnapshot,
                        password = passwordSnapshot,
                        folder = folderSnapshot,
                        name = filenameSnapshot,
                        startImmediately = startSnapshot,
                    )
                )
                ftpPassword.value = ""
                close()
            } catch (error: Exception) {
                ftpFormError.value = error.localizedMessage ?: "Could not create FTP download."
            } finally {
                ftpSubmitInProgress.value = false
            }
        }
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
    val startImmediately: Boolean = true,
)
