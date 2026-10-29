package ir.amirab.downloader.aria2

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration

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
            lifecycleState = Aria2ProcessState.READY
            return client
        } catch (failure: Throwable) {
            terminateOwnedProcess()
            deleteSessionConfig()
            rpc = null
            process = null
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
        try {
            if (ownedProcess?.isAlive() == true) {
                runCatching { rpc?.shutdown() }
                if (!ownedProcess.waitFor(config.shutdownTimeout.toMillis())) {
                    ownedProcess.destroy()
                    if (!ownedProcess.waitFor(config.shutdownTimeout.toMillis())) {
                        ownedProcess.destroyForcibly()
                        ownedProcess.waitFor(config.shutdownTimeout.toMillis())
                    }
                }
            }
        } finally {
            deleteSessionConfig()
            rpc = null
            process = null
            rpcEndpoint = null
            lifecycleState = Aria2ProcessState.STOPPED
        }
    }

    override fun close() = shutdown()

    private fun validatePaths() {
        require(Files.isRegularFile(config.executable)) { "aria2 executable is missing or is not a file." }
        require(Files.isDirectory(config.workingDirectory)) { "aria2 working directory is missing." }
        Files.createDirectories(config.sessionDirectory)
        require(Files.isDirectory(config.sessionDirectory)) { "aria2 session directory is unavailable." }
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
        val contents = buildString {
            appendLine("enable-rpc=true")
            appendLine("rpc-listen-all=false")
            appendLine("rpc-listen-port=$port")
            appendLine("rpc-secret=$rawSecret")
            appendLine("disable-ipv6=true")
            appendLine("console-log-level=warn")
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

    private fun restrictToOwnerWhenSupported(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
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

    private fun terminateOwnedProcess() {
        val ownedProcess = process ?: return
        if (!ownedProcess.isAlive()) return
        ownedProcess.destroy()
        if (!ownedProcess.waitFor(config.shutdownTimeout.toMillis())) {
            ownedProcess.destroyForcibly()
            ownedProcess.waitFor(config.shutdownTimeout.toMillis())
        }
    }

    private fun deleteSessionConfig() {
        val path = sessionConfigPath
        sessionConfigPath = null
        if (path != null) runCatching { Files.deleteIfExists(path) }
    }

    private fun saturatingAdd(value: Long, increment: Long): Long =
        if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment

    override fun toString(): String = "Aria2ProcessController(state=${state()}, endpoint=LOOPBACK_REDACTED)"

}
