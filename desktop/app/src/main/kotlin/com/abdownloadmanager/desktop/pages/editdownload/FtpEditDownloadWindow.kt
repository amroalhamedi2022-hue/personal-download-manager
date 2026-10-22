package com.abdownloadmanager.desktop.pages.editdownload

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.ui.util.rememberMyDirectoryPickerLauncher
import java.io.File
import com.abdownloadmanager.desktop.pages.enterurl.FtpDownloadRequest
import com.abdownloadmanager.desktop.window.custom.CustomWindow
import com.abdownloadmanager.desktop.window.custom.rememberWindowController
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.myColors

/**
 * Desktop-only FTP editor. The shared HTTP/HLS edit checker is not an FTP
 * protocol checker; credentials are written only on explicit Save.
 */
@Composable
fun FtpEditDownloadWindow(appComponent: AppComponent) {
    val item by appComponent.ftpEditItem.collectAsState()
    val entry = item ?: return
    val error by appComponent.ftpEditError.collectAsState()
    val busy by appComponent.ftpEditBusy.collectAsState()

    var link by remember(entry.id) { mutableStateOf(entry.link) }
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var folder by remember(entry.id) { mutableStateOf(entry.folder) }
    var username by remember(entry.id) { mutableStateOf(entry.username) }
    var newPassword by remember(entry.id) { mutableStateOf("") }
    CustomWindow(
        state = rememberWindowState(
            initialBoundsProvider = WindowBoundsProvider(
                sizeProvider = WindowSizeProvider.Fixed(DpSize(520.dp, 360.dp)),
                positionProvider = WindowPositionProvider.CenteredOnScreen,
            )
        ),
        alwaysOnTop = true,
        onCloseRequest = appComponent::closeEditDownloadDialog,
        windowController = rememberWindowController(title = "Edit FTP download"),
    ) {
        // FileKit's LocalWindow is supplied inside CustomWindow, not outside.
        val folderPicker = rememberMyDirectoryPickerLauncher(
            title = "Select FTP download folder",
            initialDirectory = remember(folder) {
                runCatching { File(folder).canonicalPath }.getOrNull()
            },
            attachToWindow = true,
        ) { chosen -> chosen?.let { folder = it } }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            MyTextField(link, { link = it }, "ftp://server/path/file", Modifier.fillMaxWidth(), enabled = !busy)
            Spacer(Modifier.height(8.dp))
            MyTextField(name, { name = it }, "File name", Modifier.fillMaxWidth(), enabled = !busy)
            Spacer(Modifier.height(8.dp))
            Row {
                MyTextField(folder, { folder = it }, "Save folder", Modifier.weight(1f), enabled = !busy)
                Spacer(Modifier.width(8.dp))
                ActionButton("Browse", enabled = !busy, onClick = folderPicker::launch)
            }
            Spacer(Modifier.height(8.dp))
            MyTextField(username, { username = it }, "FTP username (anonymous)", Modifier.fillMaxWidth(), enabled = !busy)
            Spacer(Modifier.height(8.dp))
            if (username != "anonymous") {
                MyTextField(
                    newPassword,
                    { newPassword = it },
                    "New password (leave blank to keep existing)",
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("FTP traffic is not encrypted. Passwords are stored in Windows Credential Manager.")
            if (error != null) {
                Spacer(Modifier.height(4.dp))
                Text(error.orEmpty(), color = myColors.error)
            }
            Spacer(Modifier.weight(1f))
            Row {
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", enabled = !busy, onClick = appComponent::closeEditDownloadDialog)
                Spacer(Modifier.width(8.dp))
                ActionButton("Save", enabled = !busy && link.isNotBlank() && name.isNotBlank(), onClick = {
                    appComponent.saveFtpEdit(
                        FtpDownloadRequest(
                            link.trim(),
                            username.trim(),
                            if (username.trim() == "anonymous") "" else newPassword,
                            folder,
                            name,
                        )
                    )
                })
            }
        }
    }
}
