package com.abdownloadmanager.desktop.actions

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.desktop.DesktopDownloadDialogManager
import com.abdownloadmanager.shared.action.createStopAllAction
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import ir.amirab.downloader.queue.DownloadQueue
import ir.amirab.util.compose.action.AnAction
import ir.amirab.util.compose.action.simpleAction
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


fun createDesktopStopAllAction(
    scope: CoroutineScope,
    downloadSystem: DownloadSystem,
    desktopDownloadDialogManager: DesktopDownloadDialogManager,
    activeQueuesFlow: StateFlow<List<DownloadQueue>>
): AnAction {
    return createStopAllAction(
        scope = scope,
        downloadSystem = downloadSystem,
        activeQueuesFlow = activeQueuesFlow,
        extraJobs = {
            val activeDownloadIds = downloadSystem.downloadMonitor.activeDownloadListFlow.value.map { it.id }
            desktopDownloadDialogManager.closeDownloadDialog(activeDownloadIds)
        }
    )
}

fun createDeleteCompletedDownloadsAction(
    scope: CoroutineScope,
    completedDownloadIdsFlow: StateFlow<List<Long>>,
    requestDelete: (List<Long>) -> Unit,
): AnAction {
    return simpleAction(
        title = Res.string.all_finished.asStringSource(),
        icon = MyIcons.remove,
        checkEnable = completedDownloadIdsFlow.mapStateFlow { it.isNotEmpty() },
    ) {
        scope.launch {
            requestDelete(completedDownloadIdsFlow.value)
        }
    }
}
