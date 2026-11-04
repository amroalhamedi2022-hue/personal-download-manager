package com.abdownloadmanager.desktop.cli.download.open

import com.abdownloadmanager.desktop.utils.singleInstance.SingleInstanceManager
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.long

/** Opens the native HyperGet progress/details window for persisted downloads. */
class OpenDownload : SuspendingCliktCommand("open") {
    override fun help(context: Context) = "Open download progress/details window(s)"

    private val ids by argument("id").long().multiple()

    override suspend fun run() {
        require(ids.isNotEmpty()) { "At least one download ID is required." }
        SingleInstanceManager.get().awokenAppIPCService().getService().useService {
            it.openDownload(ids)
        }
    }
}
