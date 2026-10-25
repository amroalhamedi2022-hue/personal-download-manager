package ir.amirab.downloader.ftp

import ir.amirab.downloader.utils.speedlimiter.SpeedLimiter
import okio.buffer
import okio.source
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop FTP transport, implemented using Apache Commons Net 3.13.0 (Apache-2.0).
 *
 * The remote protocol is FTP (unencrypted); never log user/password or URL user-info.
 * Output is staged in a .ftp-part file, and committed only after a successful 226
 * response and byte-count validation. The metadata sidecar stores no credentials.
 *
 * This transport remains independent of IDownloadItem and the persisted JSON
 * schema; FtpDownloadJob is the reviewed adapter used by GUI, queue and CLI.
 */
class FtpTransferClient {
    /**
     * RETR uses a separate data socket. Disconnecting only the FTP control
     * connection does not reliably interrupt a live data-stream read.
     * Keep cancellation state per transfer so an old Pause cannot cancel
     * a later Resume on the same FtpTransferClient.
     */
    private class ActiveTransfer(val client: FTPClient) {
        val cancelled = AtomicBoolean(false)
        val dataStream = AtomicReference<InputStream?>(null)

        fun ensureNotCancelled() {
            if (cancelled.get()) throw IOException("FTP transfer cancelled")
        }

        fun cancel() {
            cancelled.set(true)
            // Closing retrieveFileStream's input also closes its data socket.
            runCatching { dataStream.get()?.close() }
            runCatching { client.disconnect() }
        }
    }

    private val activeTransfer = AtomicReference<ActiveTransfer?>(null)

    data class TransferResult(
        val destination: Path,
        val transferredBytes: Long,
        val resumedFrom: Long,
        val remoteSize: Long?,
        val remoteModificationTime: String?,
    )

    data class RemoteIdentity(val size: Long?, val modificationTime: String?)

    /**
     * Probe a single FTP file without opening an output file or downloading
     * file contents. Invoke off the Compose/UI thread. FTP is plain-text on
     * the wire; do not use it as a secure login transport.
     */
    fun inspectRemote(
        url: String,
        username: String = "anonymous",
        password: String = "anonymous@",
        connectTimeout: Duration = Duration.ofSeconds(8),
    ): RemoteIdentity {
        val endpoint = parseEndpoint(url)
        require(username.isNotBlank() && username.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(password.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(!connectTimeout.isNegative && !connectTimeout.isZero)
        val ftp = FTPClient()
        try {
            ftp.setAutodetectUTF8(true)
            ftp.setControlEncoding("UTF-8")
            ftp.setConnectTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDefaultTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDataTimeout(connectTimeout)
            ftp.connect(endpoint.host, endpoint.port)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw IOException("FTP connection rejected (code ${ftp.replyCode})")
            }
            if (!ftp.login(username, password)) {
                throw IOException("FTP login rejected (code ${ftp.replyCode})")
            }
            ftp.enterLocalPassiveMode()
            if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) {
                throw IOException("FTP binary mode unavailable (code ${ftp.replyCode})")
            }
            val sizeCode = ftp.sendCommand("SIZE", endpoint.path)
            if (sizeCode == 550) throw IOException("FTP file not found or access denied (code 550)")
            val size = if (sizeCode == 213) {
                ftp.replyString?.lineSequence()?.firstOrNull()
                    ?.trim()?.removePrefix("213")?.trim()?.toLongOrNull()
            } else null
            val timestamp = responseValue(ftp, "MDTM", endpoint.path)
            return RemoteIdentity(size = size, modificationTime = timestamp)
        } finally {
            if (ftp.isConnected) {
                runCatching { ftp.logout() }
                runCatching { ftp.disconnect() }
            }
        }
    }

    fun cancel() {
        activeTransfer.get()?.cancel()
    }

    /**
     * Blocking transport: invoke on Dispatchers.IO, never from the Compose UI thread.
     * A repeated call resumes only when it can verify both remote SIZE and MDTM.
     * An unknown remote identity is allowed for initial download, never for resume.
     */
    fun download(
        url: String,
        output: Path,
        username: String = "anonymous",
        password: String = "anonymous@",
        connectTimeout: Duration = Duration.ofSeconds(15),
        dataTimeout: Duration = Duration.ofSeconds(30),
        speedLimitBytesPerSecond: Long = 0L,
        sharedSpeedLimiters: List<SpeedLimiter> = emptyList(),
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): TransferResult {
        val endpoint = parseEndpoint(url)
        require(username.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(password.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(!connectTimeout.isNegative && !connectTimeout.isZero)
        require(!dataTimeout.isNegative && !dataTimeout.isZero)
        require(speedLimitBytesPerSecond >= 0L) { "FTP speed limit must not be negative" }
        val absolute = output.toAbsolutePath().normalize()
        val parent = absolute.parent ?: throw IOException("FTP destination has no parent")
        Files.createDirectories(parent)
        val part = absolute.resolveSibling(absolute.fileName.toString() + ".ftp-part")
        val meta = absolute.resolveSibling(absolute.fileName.toString() + ".ftp-part.properties")
        if (Files.exists(absolute)) {
            throw IOException("FTP destination already exists; refusing to overwrite: ${absolute.fileName}")
        }

        val ftp = FTPClient()
        val transfer = ActiveTransfer(ftp)
        if (!activeTransfer.compareAndSet(null, transfer)) {
            throw IllegalStateException("An FTP transfer is already in progress on this client")
        }
        try {
            ftp.setAutodetectUTF8(true)
            ftp.setControlEncoding("UTF-8")
            ftp.setConnectTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDefaultTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDataTimeout(dataTimeout)
            ftp.connect(endpoint.host, endpoint.port)
            transfer.ensureNotCancelled()
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw IOException("FTP connection rejected (code ${ftp.replyCode})")
            }
            if (!ftp.login(username, password)) {
                throw IOException("FTP login rejected (code ${ftp.replyCode})")
            }
            transfer.ensureNotCancelled()
            ftp.enterLocalPassiveMode()
            if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) {
                throw IOException("FTP binary mode unavailable (code ${ftp.replyCode})")
            }

            val identity = RemoteIdentity(
                size = responseValue(ftp, "SIZE", endpoint.path)?.toLongOrNull(),
                modificationTime = responseValue(ftp, "MDTM", endpoint.path)
            )
            val existing = ftpStagedBytes(part)
            if (existing > 0) {
                val old = loadFtpResumeIdentity(meta)
                val canResume = old != null &&
                    old.getProperty("endpoint") == endpoint.identity &&
                    identity.size != null &&
                    identity.modificationTime != null &&
                    old.getProperty("size") == identity.size.toString() &&
                    old.getProperty("modificationTime") == identity.modificationTime &&
                    existing <= identity.size
                if (!canResume) {
                    throw IOException("FTP resume blocked: remote identity unavailable or changed")
                }
            }

            if (existing > 0) {
                ftp.setRestartOffset(existing)
            }
            val stream = ftp.retrieveFileStream(endpoint.path)
                ?: throw IOException("FTP RETR rejected (code ${ftp.replyCode})")
            transfer.dataStream.set(stream)
            var downloaded = existing
            var stagedFileKey: Any? = null
            var stagedCreationTime: FileTime? = null
            withFtpDataStream(
                stream = stream,
                unregister = { transfer.dataStream.compareAndSet(stream, null) },
                checkCancellation = { transfer.ensureNotCancelled() },
            ) {
                // A server may advertise metadata for a path that is not readable.
                // Don't leave a misleading resume sidecar unless RETR was accepted.
                if (existing == 0L) {
                    storeIdentity(meta, endpoint.identity, identity)
                }
                stream.use { input ->
                    // Reuse the existing downloader's throttler, rather than inventing
                    // a second speed-control algorithm specific to FTP.
                    val limiter = SpeedLimiter().apply { bytesPerSecond(speedLimitBytesPerSecond) }
                    // All FTP reads pass through the same global/host byte budgets as
                    // HTTP/HLS; a file-local throttle is applied on top.
                    val rateLimited = (sharedSpeedLimiters + limiter).fold(input.source()) { source, rate ->
                        rate.source(source)
                    }
                    rateLimited.buffer().use { received ->
                        val openedStage = openFtpStagedOutput(part, existing)
                        stagedFileKey = openedStage.fileKey
                        stagedCreationTime = openedStage.creationTime
                        openedStage.buffered().use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = received.read(buffer)
                                transfer.ensureNotCancelled()
                                if (n == -1) break
                                outputStream.write(buffer, 0, n)
                                downloaded += n
                                onProgress(downloaded, identity.size)
                                transfer.ensureNotCancelled()
                            }
                            outputStream.flush()
                        }
                    }
                }
            }
            transfer.ensureNotCancelled()
            if (!ftp.completePendingCommand()) {
                throw IOException("FTP transfer did not receive successful completion (code ${ftp.replyCode})")
            }
            if (identity.size != null && downloaded != identity.size) {
                throw IOException("FTP byte-count mismatch: expected ${identity.size}, got $downloaded")
            }
            transfer.ensureNotCancelled()
            // Never use ATOMIC_MOVE here: it can replace a file that appeared
            // while RETR was running, even without REPLACE_EXISTING.
            publishFtpStagedFile(part, absolute, downloaded, stagedFileKey, stagedCreationTime)
            Files.deleteIfExists(meta)
            return TransferResult(absolute, downloaded - existing, existing, identity.size, identity.modificationTime)
        } finally {
            activeTransfer.compareAndSet(transfer, null)
            if (ftp.isConnected) {
                runCatching { ftp.logout() }
                runCatching { ftp.disconnect() }
            }
        }
    }

    companion object {
        private data class Endpoint(val host: String, val port: Int, val path: String) {
            val identity: String = "ftp://$host:$port$path"
        }

        private fun parseEndpoint(value: String): Endpoint {
            val uri = parseFtpFileUri(value)
            val port = if (uri.port == -1) 21 else uri.port
            return Endpoint(uri.host, port, uri.path)
        }

        private fun responseValue(client: FTPClient, command: String, path: String): String? {
            val code = client.sendCommand(command, path)
            if (code != 213) return null
            return client.replyString
                ?.lineSequence()?.firstOrNull()
                ?.trim()?.removePrefix("213")?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun storeIdentity(path: Path, endpoint: String, identity: RemoteIdentity) {
            val properties = Properties()
            properties.setProperty("endpoint", endpoint)
            identity.size?.let { properties.setProperty("size", it.toString()) }
            identity.modificationTime?.let { properties.setProperty("modificationTime", it) }
            val temp = Files.createTempFile(path.parent, ".ftp-sidecar-", ".tmp")
            try {
                Files.newOutputStream(temp).use { properties.store(it, "FTP resume identity; no credentials") }
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }
}
