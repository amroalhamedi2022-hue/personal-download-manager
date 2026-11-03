package com.abdownloadmanager.desktop.pages.singleDownloadPage

import arrow.optics.optics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

interface SingleDownloadPageStateStorage {
    val singleDownloadPageState: MutableStateFlow<SingleDownloadPageStateToPersist>
}
@optics
@Serializable
data class SingleDownloadPageStateToPersist(
    // Show HyperGet's connection strip and part table for a fresh profile.
    // An explicit user collapse remains persisted by the existing storage.
    val showPartInfo: Boolean = true,
) {
    companion object
}
