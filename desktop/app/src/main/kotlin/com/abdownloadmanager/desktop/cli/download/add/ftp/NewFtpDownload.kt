package com.abdownloadmanager.desktop.cli.download.add.ftp

import com.abdownloadmanager.desktop.utils.singleInstance.SingleInstanceManager
import com.abdownloadmanager.desktop.utils.singleInstance.service.AddFtpDownloadIPC
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintCompletionMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.file
import ir.amirab.downloader.ftp.FtpDownloadCredentials
import ir.amirab.util.FileNameValidator

/**
 * Adds FTP through the running installed application, using the same validated
 * persistence and Windows Credential Manager path as the GUI. Authenticated
 * passwords are accepted from stdin only, never from process arguments.
 */
class NewFtpDownload : SuspendingCliktCommand("ftp") {
    override fun help(context: Context) = "Add a binary passive-mode FTP download"

    private val link by option("--link")
        .required()
        .validate {
            runCatching { FtpDownloadCredentials(it).validateCredentials() }.isSuccess
        }
    private val outputDir by option("--folder")
        .file(mustExist = true, canBeFile = false)
        .required()
    private val outputName by option("--name")
        .required()
        .validate { FileNameValidator.isValidFileName(it) }
    private val username by option("--username")
        .default("anonymous")
    private val passwordStdin by option(
        "--password-stdin",
        help = "Read one FTP password line from standard input; required for non-anonymous accounts",
    ).flag()
    private val start by option("--start", help = "also start the download").flag()

    override suspend fun run() {
        FtpDownloadCredentials(link, username = username).validateCredentials()
        val password = when {
            username == "anonymous" && passwordStdin ->
                throw UsageError("--password-stdin is not accepted for anonymous FTP")
            username == "anonymous" -> ""
            !passwordStdin ->
                throw UsageError("Non-anonymous FTP requires --password-stdin")
            else -> System.`in`.bufferedReader(Charsets.UTF_8).readLine()
                ?.takeIf { it.isNotEmpty() }
                ?: throw UsageError("No FTP password was received on standard input")
        }
        val id = SingleInstanceManager.get().awokenAppIPCService().getService().useService {
            it.addFtpDownload(
                AddFtpDownloadIPC(
                    link = link,
                    username = username,
                    password = password,
                    folder = outputDir.path,
                    name = outputName,
                    startImmediately = start,
                )
            )
        }
        throw PrintCompletionMessage(id.toString())
    }
}
