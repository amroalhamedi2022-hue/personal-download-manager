package ir.amirab.downloader.aria2

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclFileAttributeView
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Aria2RealRuntimeTest {
    @Test
    fun pinnedWindowsAria2RunsPausesResumesRemovesAndShutsDown() {
        val executable = System.getenv("ARIA2_EXE")?.takeIf { it.isNotBlank() } ?: return
        val root = Files.createTempDirectory("hyperget-aria2-runtime-")
        val payload = ByteArray(8 * 1024 * 1024) { index -> ((index * 31 + 17) and 0xff).toByte() }
        val server = slowRangeServer(payload)
        val processConfig = Aria2ProcessConfig(
            executable = Path.of(executable),
            workingDirectory = root,
            sessionDirectory = root.resolve("session"),
            startupTimeout = Duration.ofSeconds(20),
            shutdownTimeout = Duration.ofSeconds(10),
        )
        val sessionSecrets = mutableListOf<String>()
        fun newController() = Aria2ProcessController(
            config = processConfig,
            rpcFactory = { endpoint, secret ->
                Aria2HttpRpcClient(endpoint, secret, timeout = Duration.ofSeconds(5))
            },
            secretFactory = {
                Aria2SessionSecret.generate().also { sessionSecrets += it.rpcToken() }
            },
        )
        var controller = newController()
        try {
            val rpc = controller.start()
            assertEquals("1.37.0", rpc.getVersion().version)
            assertFalse(hasRpcSecretConfig(processConfig.sessionDirectory))
            val bindingStore = Aria2BindingStore(root.resolve("bindings.json"))
            var adapter = Aria2EngineAdapter.restore(rpc, bindingStore)
            adapter.add(
                Aria2AddRequest(
                    hyperGetTaskId = 9001,
                    source = "http://127.0.0.1:${server.address.port}/fixture.bin",
                    destinationDirectory = root,
                    fileName = "result.bin",
                )
            )

            await(Duration.ofSeconds(30)) { adapter.status(9001).progress.completedLength >= 128 * 1024 }
            adapter.pause(9001)
            await(Duration.ofSeconds(10)) { adapter.status(9001).progress.state == Aria2TransferState.PAUSED }
            val pausedBytes = adapter.status(9001).progress.completedLength
            Thread.sleep(300)
            assertEquals(pausedBytes, adapter.status(9001).progress.completedLength)

            val durableBinding = adapter.bindings().single()
            controller.shutdown()
            assertEquals(Aria2ProcessState.STOPPED, controller.state())
            assertTrue(Files.size(processConfig.sessionStateFile) > 0)

            controller = newController()
            val restartedRpc = controller.start()
            assertEquals(2, sessionSecrets.distinct().size)
            assertFalse(hasRpcSecretConfig(processConfig.sessionDirectory))
            assertOwnerOnlyAclWhenAvailable(processConfig.sessionStateFile)
            adapter = Aria2EngineAdapter.restore(restartedRpc, bindingStore)
            assertEquals(durableBinding, adapter.reconcileRestored().single())
            if (adapter.status(9001).progress.state == Aria2TransferState.PAUSED) {
                adapter.unpause(9001)
            }
            await(Duration.ofSeconds(90)) { adapter.status(9001).progress.state == Aria2TransferState.COMPLETE }
            val output = root.resolve("result.bin")
            assertTrue(Files.isRegularFile(output))
            assertEquals(payload.size.toLong(), Files.size(output))
            assertTrue(sha256(payload).contentEquals(sha256(Files.readAllBytes(output))))
            assertTrue(adapter.remove(9001))
            assertTrue(adapter.bindings().isEmpty())
            assertFalse(Files.readString(root.resolve("bindings.json")).contains("token:"))
        } finally {
            controller.shutdown()
            server.stop(0)
        }
        assertEquals(Aria2ProcessState.STOPPED, controller.state())
    }

    private fun slowRangeServer(payload: ByteArray): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/fixture.bin") { exchange -> serveRange(exchange, payload) }
        server.start()
        return server
    }

    private fun serveRange(exchange: HttpExchange, payload: ByteArray) {
        val range = exchange.requestHeaders.getFirst("Range")
        val start = range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
        if (start !in 0 until payload.size) {
            exchange.sendResponseHeaders(416, -1)
            exchange.close()
            return
        }
        val length = payload.size - start
        exchange.responseHeaders.set("Accept-Ranges", "bytes")
        exchange.responseHeaders.set("ETag", "\"hyperget-aria2-runtime-v1\"")
        if (range != null) exchange.responseHeaders.set("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}")
        exchange.sendResponseHeaders(if (range == null) 200 else 206, length.toLong())
        try {
            exchange.responseBody.use { output ->
                var offset = start
                while (offset < payload.size) {
                    val count = minOf(16 * 1024, payload.size - offset)
                    output.write(payload, offset, count)
                    output.flush()
                    offset += count
                    Thread.sleep(2)
                }
            }
        } catch (_: Exception) {
            exchange.close()
        }
    }

    private fun await(timeout: Duration, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastFailure: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                if (condition()) return
            } catch (failure: Throwable) {
                lastFailure = failure
            }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for aria2 runtime condition.", lastFailure)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hasRpcSecretConfig(directory: Path): Boolean =
        Files.list(directory).use { entries ->
            entries.anyMatch { it.fileName.toString().startsWith("aria2-rpc-") }
        }

    private fun assertOwnerOnlyAclWhenAvailable(path: Path) {
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
        val owner = Files.getOwner(path)
        assertTrue(view.acl.isNotEmpty())
        assertTrue(view.acl.all { it.principal() == owner })
    }
}
