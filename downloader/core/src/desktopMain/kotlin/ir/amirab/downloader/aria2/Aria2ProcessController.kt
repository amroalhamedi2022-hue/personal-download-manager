package ir.amirab.downloader.aria2

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.EnumSet

private const val ARIA2_LOOPBACK_HOST = "127.0.0.1"

enum class Aria2ProcessState {
    STOPPED,
    STARTING,
    READY,
    STOPPING,
    CRASHED,
}

class Aria2ProcessException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

fun interface Aria2LoopbackPortSelector {
    fun select(): Int

    companion object {
        val Ephemeral = Aria2LoopbackPortSelector {
            ServerSocket(0, 1, InetAddress.getByName(ARIA2_LOOPBACK_HOST)).use { it.localPort }
        }
    }
}

interface Aria2OwnedProcess {
    fun isAlive(): Boolean
    fun waitFor(timeoutMillis: Long): Boolean
    fun destroy()
    fun destroyForcibly()
}

fun interface Aria2ProcessLauncher {
    fun launch(command: List<String>, workingDirectory: Path): Aria2OwnedProcess
}

object SystemAria2ProcessLauncher : Aria2ProcessLauncher {
    override fun launch(command: List<String>, workingDirectory: Path): Aria2OwnedProcess {
        val process = ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.outputStream.close()
        return object : Aria2OwnedProcess {
            override fun isAlive(): Boolean = process.isAlive
            override fun waitFor(timeoutMillis: Long): Boolean =
                process.waitFor(timeoutMillis.coerceAtLeast(0), java.util.concurrent.TimeUnit.MILLISECONDS)

            override fun destroy() = process.destroy()
            override fun destroyForcibly() {
                process.destroyForcibly()
            }
        }
    }
}

data class Aria2ProcessConfig(
    val executable: Path,
    val workingDirectory: Path,
    val sessionDirectory: Path,
    val sessionStateFile: Path = sessionDirectory.resolve("aria2.session"),
    val startupTimeout: Duration = Duration.ofSeconds(15),
    val shutdownTimeout: Duration = Duration.ofSeconds(5),
    val readinessPollInterval: Duration = Duration.ofMillis(50),
) {
    init {
        require(!startupTimeout.isNegative && !startupTimeout.isZero) { "Startup timeout must be positive." }
        require(!shutdownTimeout.isNegative && !shutdownTimeout.isZero) { "Shutdown timeout must be positive." }
        require(!readinessPollInterval.isNegative && !readinessPollInterval.isZero) {
            "Readiness poll interval must be positive."
        }
    }
}

/**
 * Owns exactly one aria2c child process. The RPC secret is written to a short-lived
 * owner-only configuration file and is never included in argv, exception messages,
 * or a printable object.
 */
class Aria2ProcessController(
    private val config: Aria2ProcessConfig,
    private val rpcFactory: (URI, Aria2SessionSecret) -> Aria2RpcApi,
    private val launcher: Aria2ProcessLauncher = SystemAria2ProcessLauncher,
    private val portSelector: Aria2LoopbackPortSelector = Aria2LoopbackPortSelector.Ephemeral,
    private val secretFactory: () -> Aria2SessionSecret = { Aria2SessionSecret.generate() },
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
) : AutoCloseable {
    private var lifecycleState = Aria2ProcessState.STOPPED
    private var process: Aria2OwnedProcess? = null
    private var rpc: Aria2RpcApi? = null
    private var sessionConfigPath: Path? = null
    private var rpcEndpoint: URI? = null

    @Synchronized
    fun state(): Aria2ProcessState {
        if (lifecycleState == Aria2ProcessState.READY && process?.isAlive() != true) {
            lifecycleState = Aria2ProcessState.CRASHED
            deleteSessionConfig()
            rpc = null
            process = null
            rpcEndpoint = null
        }
        return lifecycleState
    }

    @Synchronized
    fun endpoint(): URI? = if (state() == Aria2ProcessState.READY) rpcEndpoint else null

    @Synchronized
    fun start(): Aria2RpcApi {
        check(lifecycleState == Aria2ProcessState.STOPPED) { "aria2 process is already owned by this controller." }
        validatePaths()

        lifecycleState = Aria2ProcessState.STARTING
        try {
            val secret = secretFactory()
            val port = selectAvailablePort()
            val endpoint = URI("http://$ARIA2_LOOPBACK_HOST:$port/jsonrpc")
            val sessionPath = writeSessionConfig(port, secret)
            sessionConfigPath = sessionPath
            val command = listOf(
                config.executable.toAbsolutePath().normalize().toString(),
                "--conf-path=${sessionPath.toAbsolutePath().normalize()}",
                "--daemon=false",
            )
            val ownedProcess = launcher.launch(command, config.workingDirectory)
            process = ownedProcess
            val client = rpcFactory(endpoint, secret)
            rpc = client
            rpcEndpoint = endpoint
            awaitReadiness(ownedProcess, client)
            // aria2 has parsed the config. Remove the only plaintext copy of
            // the per-session RPC secret before exposing the ready adapter.
            deleteSessionConfig(strict = true)
            lifecycleState = Aria2ProcessState.READY
            return client
        } catch (failure: Throwable) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            val terminated = terminateOwnedProcess()
            deleteSessionConfig()
            rpc = null
            if (terminated) process = null
            rpcEndpoint = null
            lifecycleState = Aria2ProcessState.CRASHED
            throw Aria2ProcessException("aria2 failed to reach RPC readiness on loopback.", failure)
        }
    }

    @Synchronized
    fun shutdown() {
        if (lifecycleState == Aria2ProcessState.STOPPED) return
        lifecycleState = Aria2ProcessState.STOPPING
        val ownedProcess = process
        var terminated = ownedProcess?.isAlive() != true
        try {
            if (ownedProcess?.isAlive() == true) {
                runCatching { rpc?.shutdown() }
                terminated = ownedProcess.waitFor(config.shutdownTimeout.toMillis())
                if (!terminated) {
                    ownedProcess.destroy()
                    terminated = ownedProcess.waitFor(config.shutdownTimeout.toMillis())
                    if (!terminated) {
                        ownedProcess.destroyForcibly()
                        terminated = ownedProcess.waitFor(config.shutdownTimeout.toMillis())
                    }
                }
            }
        } finally {
            deleteSessionConfig()
            restrictToOwnerWhenSupported(config.sessionStateFile)
            rpc = null
            rpcEndpoint = null
            if (terminated) {
                process = null
                lifecycleState = Aria2ProcessState.STOPPED
            } else {
                // Retain ownership so a later shutdown attempt can retry; never
                // report STOPPED while the child is still alive.
                process = ownedProcess
                lifecycleState = Aria2ProcessState.CRASHED
            }
        }
        if (!terminated) throw Aria2ProcessException("aria2 child process remained alive after forced shutdown.")
    }

    override fun close() = shutdown()

    private fun validatePaths() {
        require(Files.isRegularFile(config.executable)) { "aria2 executable is missing or is not a file." }
        require(Files.isDirectory(config.workingDirectory)) { "aria2 working directory is missing." }
        Files.createDirectories(config.sessionDirectory)
        require(Files.isDirectory(config.sessionDirectory)) { "aria2 session directory is unavailable." }
        restrictToOwnerWhenSupported(config.sessionDirectory, directory = true)
        val stateParent = config.sessionStateFile.toAbsolutePath().normalize().parent
        require(stateParent == config.sessionDirectory.toAbsolutePath().normalize()) {
            "aria2 session state must stay inside the application-owned session directory."
        }
        if (Files.notExists(config.sessionStateFile)) Files.createFile(config.sessionStateFile)
        require(Files.isRegularFile(config.sessionStateFile)) { "aria2 session state is unavailable." }
        restrictToOwnerWhenSupported(config.sessionStateFile)
    }

    private fun selectAvailablePort(): Int {
        val port = portSelector.select()
        require(port in 1..65535) { "RPC port is outside the valid range." }
        try {
            ServerSocket(port, 1, InetAddress.getByName(ARIA2_LOOPBACK_HOST)).use { socket ->
                require(socket.inetAddress.isLoopbackAddress) { "RPC probe did not bind to loopback." }
            }
        } catch (failure: Exception) {
            throw Aria2ProcessException("Selected aria2 RPC loopback port is unavailable.", failure)
        }
        return port
    }

    private fun writeSessionConfig(port: Int, secret: Aria2SessionSecret): Path {
        val attributes = runCatching {
            arrayOf(PosixFilePermissions.asFileAttribute(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)))
        }.getOrDefault(emptyArray())
        val path = try {
            Files.createTempFile(config.sessionDirectory, "aria2-rpc-", ".conf", *attributes)
        } catch (_: UnsupportedOperationException) {
            Files.createTempFile(config.sessionDirectory, "aria2-rpc-", ".conf")
        }
        val rawSecret = secret.rpcToken().removePrefix("token:")
        val durableSession = config.sessionStateFile.toAbsolutePath().normalize().toString().replace('\\', '/')
        val contents = buildString {
            appendLine("enable-rpc=true")
            appendLine("rpc-listen-all=false")
            appendLine("rpc-listen-port=$port")
            appendLine("rpc-secret=$rawSecret")
            appendLine("disable-ipv6=true")
            appendLine("console-log-level=warn")
            appendLine("input-file=$durableSession")
            appendLine("save-session=$durableSession")
            appendLine("save-session-interval=1")
            appendLine("auto-save-interval=1")
            appendLine("always-resume=true")
            appendLine("force-save=true")
        }
        try {
            Files.writeString(path, contents, StandardCharsets.UTF_8)
            restrictToOwnerWhenSupported(path)
            return path
        } catch (failure: Throwable) {
            runCatching { Files.deleteIfExists(path) }
            throw failure
        }
    }

    private fun restrictToOwnerWhenSupported(path: Path, directory: Boolean = false) {
        val posixApplied = runCatching {
            val permissions = mutableSetOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
            if (directory) permissions += PosixFilePermission.OWNER_EXECUTE
            Files.setPosixFilePermissions(
                path,
                permissions,
            )
        }.isSuccess
        if (posixApplied) return

        // Windows has no POSIX permission view. Replace inherited ACL entries
        // with one full-control entry for the file owner (the application user).
        runCatching {
            val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                ?: return@runCatching
            val owner = Files.getOwner(path)
            val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
                .build()
            view.acl = listOf(entry)
        }.getOrElse { throw Aria2ProcessException("Unable to restrict aria2 session file to its owner.", it) }
    }

    private fun awaitReadiness(ownedProcess: Aria2OwnedProcess, client: Aria2RpcApi) {
        val deadline = saturatingAdd(nanoTime(), config.startupTimeout.toNanos())
        var lastFailure: Throwable? = null
        while (nanoTime() < deadline) {
            if (!ownedProcess.isAlive()) {
                throw Aria2ProcessException("aria2 exited before RPC readiness.")
            }
            try {
                client.getVersion()
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                sleepMillis(config.readinessPollInterval.toMillis().coerceAtLeast(1))
            }
        }
        throw Aria2ProcessException("aria2 RPC readiness timed out.", lastFailure)
    }

    private fun terminateOwnedProcess(): Boolean {
        val ownedProcess = process ?: return true
        if (!ownedProcess.isAlive()) return true
        ownedProcess.destroy()
        if (!ownedProcess.waitFor(config.shutdownTimeout.toMillis())) {
            ownedProcess.destroyForcibly()
            return ownedProcess.waitFor(config.shutdownTimeout.toMillis())
        }
        return true
    }

    private fun deleteSessionConfig(strict: Boolean = false) {
        val path = sessionConfigPath ?: return
        try {
            Files.deleteIfExists(path)
            if (strict && Files.exists(path)) {
                throw Aria2ProcessException("Unable to remove the aria2 RPC secret file after startup.")
            }
            if (Files.notExists(path)) sessionConfigPath = null
        } catch (failure: Throwable) {
            if (strict) throw Aria2ProcessException(
                "Unable to remove the aria2 RPC secret file after startup.",
                failure,
            )
        }
    }

    private fun saturatingAdd(value: Long, increment: Long): Long =
        if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment

    override fun toString(): String = "Aria2ProcessController(state=${state()}, endpoint=LOOPBACK_REDACTED)"

}
