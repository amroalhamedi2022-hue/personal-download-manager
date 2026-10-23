package com.abdownloadmanager.desktop.cli.settings

import com.abdownloadmanager.desktop.cli.settings.maxconcurrent.MaxConcurrentDownloads
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class Settings : SuspendingCliktCommand("settings") {
    init {
        subcommands(MaxConcurrentDownloads())
    }

    override fun help(context: Context) = "Read or update supported persistent application settings"
    override suspend fun run() = Unit
}
