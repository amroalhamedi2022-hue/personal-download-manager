package ir.amirab.downloader.ftp

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop FTP transport, implemented using Apache Commons Net 3.13.0 (Apache-2.0).
 *
 * The remote protocol is FTP (unencrypted); never log user/password or URL user-info.
 * Output is staged in a .ftp-part file, and committed only after a successful 226
 * response and byte-count validation. The metadata sidecar stores no credentials.
 *
 * This is intentionally independent of IDownloadItem and the persisted JSON schema.
 * The UI/queue adapter must be added separately before accepting ftp:// in Add URL.
 */
class FtpTransferClient {
    private val activeConnection = AtomicReference<FTPClient?>(null)

    data class TransferResult(
        val destination: Path,
        val transferredBytes: Long,
        val resumedFrom: Long,
        val remoteSize: Long?,
        val remoteModificationTime: String?,
    )

    data class RemoteIdentity(val size: Long?, val modificationTime: String?)

    fun cancel() {
        // Closing the socket interrupts blocking read() on the worker thread.
        val client = activeConnection.get()
        runCatching { client?.disconnect() }
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
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): TransferResult {
        val endpoint = parseEndpoint(url)
        require(username.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(password.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(!connectTimeout.isNegative && !connectTimeout.isZero)
        require(!dataTimeout.isNegative && !dataTimeout.isZero)
        val absolute = output.toAbsolutePath().normalize()
        val parent = absolute.parent ?: throw IOException("FTP destination has no parent")
        Files.createDirectories(parent)
        val part = absolute.resolveSibling(absolute.fileName.toString() + ".ftp-part")
        val meta = absolute.resolveSibling(absolute.fileName.toString() + ".ftp-part.properties")
        if (Files.exists(absolute)) {
            throw IOException("FTP destination already exists; refusing to overwrite: ${absolute.fileName}")
        }

        val ftp = FTPClient()
        if (!activeConnection.compareAndSet(null, ftp)) {
            throw IllegalStateException("An FTP transfer is already in progress on this client")
        }
        try {
            ftp.setAutodetectUTF8(true)
            ftp.setControlEncoding("UTF-8")
            ftp.setConnectTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDefaultTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ftp.setDataTimeout(dataTimeout)
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

            val identity = RemoteIdentity(
                size = responseValue(ftp, "SIZE", endpoint.path)?.toLongOrNull(),
                modificationTime = responseValue(ftp, "MDTM", endpoint.path)
            )
            val existing = if (Files.exists(part)) Files.size(part) else 0L
            if (existing > 0) {
                val old = loadIdentity(meta)
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
            } else {
                // No nonzero prefix exists. An obsolete sidecar is safe to replace.
                storeIdentity(meta, endpoint.identity, identity)
            }

            if (existing > 0) {
                ftp.setRestartOffset(existing)
            }
            val stream = ftp.retrieveFileStream(endpoint.path)
                ?: throw IOException("FTP RETR rejected (code ${ftp.replyCode})")
            var downloaded = existing
            stream.use { input ->
                Files.newOutputStream(
                    part, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
                ).buffered().use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        outputStream.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, identity.size)
                    }
                    outputStream.flush()
                }
            }
            if (!ftp.completePendingCommand()) {
                throw IOException("FTP transfer did not receive successful completion (code ${ftp.replyCode})")
            }
            if (identity.size != null && downloaded != identity.size) {
                throw IOException("FTP byte-count mismatch: expected ${identity.size}, got $downloaded")
            }
            try {
                Files.move(part, absolute, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(part, absolute)
            }
            Files.deleteIfExists(meta)
            return TransferResult(absolute, downloaded - existing, existing, identity.size, identity.modificationTime)
        } finally {
            activeConnection.compareAndSet(ftp, null)
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
            val uri = try {
                URI(value.trim())
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid FTP URL")
            }
            require(uri.scheme.equals("ftp", ignoreCase = true)) { "Only ftp:// is supported" }
            require(uri.host?.isNotBlank() == true) { "FTP URL requires a host" }
            require(uri.userInfo == null) { "Pass FTP credentials separately; do not embed passwords in URL" }
            require(uri.query == null && uri.fragment == null) { "FTP URLs cannot include query/fragment" }
            val path = uri.path.orEmpty()
            require(path.startsWith("/") && path.length > 1) { "FTP URL requires a file path" }
            require(path.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Unsafe FTP path" }
            val port = if (uri.port == -1) 21 else uri.port
            require(port in 1..65535) { "Invalid FTP port" }
            return Endpoint(uri.host, port, path)
        }

        private fun responseValue(client: FTPClient, command: String, path: String): String? {
            val code = client.sendCommand(command, path)
            if (code != 213) return null
            return client.replyString
                ?.lineSequence()?.firstOrNull()
                ?.trim()?.removePrefix("213")?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun loadIdentity(path: Path): Properties? {
            if (!Files.isRegularFile(path)) return null
            return Properties().also { properties ->
                Files.newInputStream(path).use(properties::load)
            }
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
