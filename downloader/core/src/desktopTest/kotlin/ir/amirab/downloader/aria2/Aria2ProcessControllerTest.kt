package ir.amirab.downloader.aria2

import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Aria2ProcessControllerTest {
    @Test
    fun startUsesLoopbackSecretConfigAndGracefulShutdownCleansIt() {
        val fixture = Fixture()
        val process = FakeProcess()
        lateinit var command: List<String>
        var readinessAttempts = 0
        val rpc = FakeRpc(
            getVersion = {
                readinessAttempts++
                if (readinessAttempts == 1) throw Aria2RpcTransportException()
                Aria2Version("1.37.0", listOf("BitTorrent"))
            },
            shutdown = { process.alive = false },
        )
        val secret = Aria2SessionSecret.generate()
        var now = 0L
        val controller = fixture.controller(
            process = process,
            rpc = rpc,
            secret = secret,
            onCommand = { command = it },
            nanoTime = { now },
            sleep = { now += it * 1_000_000 },
        )

        assertEquals(rpc, controller.start())
        assertEquals(Aria2ProcessState.READY, controller.state())
        assertEquals("127.0.0.1", controller.endpoint()?.host)
        assertEquals(2, readinessAttempts)

        val configPath = command.configPath()
        val rawSecret = secret.rpcToken().removePrefix("token:")
        assertTrue(configPath.exists())
        val contents = configPath.readText()
        assertTrue(contents.contains("rpc-listen-all=false"))
        assertTrue(contents.contains("disable-ipv6=true"))
        assertTrue(contents.contains("rpc-secret=$rawSecret"))
        assertTrue(command.none { it.contains(rawSecret) })
        assertFalse(controller.toString().contains(rawSecret))

        controller.shutdown()

        assertEquals(Aria2ProcessState.STOPPED, controller.state())
        assertFalse(configPath.exists())
        assertEquals(1, rpc.shutdownCalls)
        assertFalse(process.destroyCalled)
        assertFalse(process.forceCalled)
    }

    @Test
    fun readinessTimeoutTerminatesProcessAndRedactsSecret() {
        val fixture = Fixture(startupTimeout = Duration.ofMillis(3))
        val process = FakeProcess(waitAfterDestroy = true)
        val secret = Aria2SessionSecret.generate()
        var now = 0L
        lateinit var configPath: Path
        val controller = fixture.controller(
            process = process,
            rpc = FakeRpc(getVersion = { throw Aria2RpcRemoteException(1) }),
            secret = secret,
            onCommand = { configPath = it.configPath() },
            nanoTime = { now },
            sleep = { now += it * 1_000_000 },
        )

        val failure = assertFailsWith<Aria2ProcessException> { controller.start() }

        assertEquals(Aria2ProcessState.CRASHED, controller.state())
        assertTrue(process.destroyCalled)
        assertFalse(configPath.exists())
        assertFalse(failure.toString().contains(secret.rpcToken().removePrefix("token:")))
    }

    @Test
    fun processCrashAfterReadinessIsObservedAndConfigIsRemoved() {
        val fixture = Fixture()
        val process = FakeProcess()
        lateinit var configPath: Path
        val controller = fixture.controller(
            process = process,
            rpc = FakeRpc(),
            onCommand = { configPath = it.configPath() },
        )
        controller.start()
        assertTrue(configPath.exists())

        process.alive = false

        assertEquals(Aria2ProcessState.CRASHED, controller.state())
        assertEquals(null, controller.endpoint())
        assertFalse(configPath.exists())
        controller.shutdown()
        assertEquals(Aria2ProcessState.STOPPED, controller.state())
    }

    @Test
    fun unavailableSelectedPortFailsBeforeLaunchingAProcess() {
        val fixture = Fixture()
        var launched = false
        ServerSocket(0).use { occupied ->
            val controller = fixture.controller(
                process = FakeProcess(),
                rpc = FakeRpc(),
                port = occupied.localPort,
                onLaunch = { launched = true },
            )

            val failure = assertFailsWith<Aria2ProcessException> { controller.start() }

            assertFalse(launched)
            assertEquals(Aria2ProcessState.CRASHED, controller.state())
            assertTrue(failure.message.orEmpty().contains("loopback"))
        }
    }

    @Test
    fun failedRpcShutdownEscalatesToForcedProcessTermination() {
        val fixture = Fixture()
        val process = FakeProcess(waitAfterDestroy = false, waitAfterForce = true)
        lateinit var configPath: Path
        val controller = fixture.controller(
            process = process,
            rpc = FakeRpc(shutdown = { throw Aria2RpcTransportException() }),
            onCommand = { configPath = it.configPath() },
        )
        controller.start()

        controller.shutdown()

        assertTrue(process.destroyCalled)
        assertTrue(process.forceCalled)
        assertFalse(configPath.exists())
        assertEquals(Aria2ProcessState.STOPPED, controller.state())
    }

    @Test
    fun missingExecutableIsRejectedWithoutCreatingAProcess() {
        val root = Files.createTempDirectory("aria2-process-missing")
        val controller = Aria2ProcessController(
            config = Aria2ProcessConfig(
                executable = root.resolve("missing-aria2c"),
                workingDirectory = root,
                sessionDirectory = root.resolve("session"),
            ),
            rpcFactory = { _, _ -> FakeRpc() },
            launcher = Aria2ProcessLauncher { _, _ -> error("must not launch") },
        )

        assertFailsWith<IllegalArgumentException> { controller.start() }
        assertEquals(Aria2ProcessState.STOPPED, controller.state())
    }

    private class Fixture(
        private val startupTimeout: Duration = Duration.ofMillis(50),
    ) {
        private val root = Files.createTempDirectory("aria2-process-test")
        private val executable = root.resolve("aria2c-test").createFile()
        private val work = root.resolve("work").createDirectories()
        private val session = root.resolve("session")

        fun controller(
            process: FakeProcess,
            rpc: FakeRpc,
            secret: Aria2SessionSecret = Aria2SessionSecret.generate(),
            port: Int = availablePort(),
            onCommand: (List<String>) -> Unit = {},
            onLaunch: () -> Unit = {},
            nanoTime: () -> Long = System::nanoTime,
            sleep: (Long) -> Unit = {},
        ): Aria2ProcessController = Aria2ProcessController(
            config = Aria2ProcessConfig(
                executable = executable,
                workingDirectory = work,
                sessionDirectory = session,
                startupTimeout = startupTimeout,
                shutdownTimeout = Duration.ofMillis(1),
                readinessPollInterval = Duration.ofMillis(1),
            ),
            rpcFactory = { endpoint, receivedSecret ->
                assertEquals(URI("http://127.0.0.1:$port/jsonrpc"), endpoint)
                assertEquals(secret.rpcToken(), receivedSecret.rpcToken())
                rpc
            },
            launcher = Aria2ProcessLauncher { command, workingDirectory ->
                onLaunch()
                assertEquals(work, workingDirectory)
                onCommand(command)
                process
            },
            portSelector = Aria2LoopbackPortSelector { port },
            secretFactory = { secret },
            nanoTime = nanoTime,
            sleepMillis = sleep,
        )
    }

    private class FakeProcess(
        private val waitAfterDestroy: Boolean = true,
        private val waitAfterForce: Boolean = true,
    ) : Aria2OwnedProcess {
        var alive = true
        var destroyCalled = false
        var forceCalled = false

        override fun isAlive(): Boolean = alive

        override fun waitFor(timeoutMillis: Long): Boolean = when {
            !alive -> true
            forceCalled && waitAfterForce -> {
                alive = false
                true
            }
            destroyCalled && waitAfterDestroy -> {
                alive = false
                true
            }
            else -> false
        }

        override fun destroy() {
            destroyCalled = true
        }

        override fun destroyForcibly() {
            forceCalled = true
        }
    }

    private class FakeRpc(
        private val getVersion: () -> Aria2Version = { Aria2Version("1.37.0", emptyList()) },
        private val shutdown: () -> Unit = {},
    ) : Aria2RpcApi {
        var shutdownCalls = 0

        override fun getVersion(): Aria2Version = getVersion.invoke()
        override fun addUri(uri: String, options: Map<String, String>): String = "1111111111111111"
        override fun tellStatus(gid: String): Aria2Status = error("not used")
        override fun pause(gid: String): String = gid
        override fun unpause(gid: String): String = gid
        override fun remove(gid: String): String = gid
        override fun shutdown() {
            shutdownCalls++
            shutdown.invoke()
        }
    }

    private fun List<String>.configPath(): Path {
        val argument = singleOrNull { it.startsWith("--conf-path=") }
        return Path.of(assertNotNull(argument).removePrefix("--conf-path="))
    }

    private companion object {
        fun availablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
