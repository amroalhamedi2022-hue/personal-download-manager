package com.abdownloadmanager.desktop.cli.settings.maxconcurrent

import com.abdownloadmanager.desktop.utils.singleInstance.SingleInstanceManager
import com.abdownloadmanager.shared.storage.appsettings.requireSupportedMaxConcurrentDownloads
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintCompletionMessage
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int

class MaxConcurrentDownloads : SuspendingCliktCommand("max-concurrent-downloads") {
    private val newValue by option(
        "--set",
        metavar = "COUNT",
        help = "Persist a new limit; zero means unlimited",
    ).int().validate {
        requireSupportedMaxConcurrentDownloads(it)
    }

    override fun help(context: Context) =
        "Print the persistent active-download limit, or update it with --set"

    override suspend fun run() {
        val value = SingleInstanceManager.get().awokenAppIPCService().getService().useService { service ->
            newValue?.let { service.setMaxConcurrentDownloads(it) }
                ?: service.getMaxConcurrentDownloads()
        }
        throw PrintCompletionMessage(value.toString())
    }
}
