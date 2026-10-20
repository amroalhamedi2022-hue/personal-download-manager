package ir.amirab.downloader.db

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.part.HttpPartDownloader
import ir.amirab.downloader.part.MinPartSizeProvider
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.part.RangedPart
import ir.amirab.util.tryAtomicMove
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.security.MessageDigest
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionalFileSaverSafetyTest {
    @Test
    fun missingFileIsDistinctFromCorruptFile() {
        val folder = Files.createTempDirectory("transactional-missing-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            assertNull(saver.readObject<Snapshot>(file))

            file.writeText("{not-json")
            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(file)
            }
            assertEquals(StoredFileReadFailureReason.MALFORMED_JSON, failure.reason)
            assertEquals("{not-json", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun validJsonWithUnsupportedSchemaIsDistinctFromMalformedJson() {
        val folder = Files.createTempDirectory("transactional-schema-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            file.writeText("{\"unexpected\":\"value\"}")

            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(file)
            }

            assertEquals(StoredFileReadFailureReason.UNSUPPORTED_SCHEMA, failure.reason)
            assertEquals("{\"unexpected\":\"value\"}", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun ioFailureIsDistinctFromMissingAndInvalidData() {
        val folder = Files.createTempDirectory("transactional-io-").toFile()
        val directoryInPlaceOfFile = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            assertTrue(directoryInPlaceOfFile.mkdir())

            val failure = assertFailsWith<StoredFileReadException> {
                saver.readObject<Snapshot>(directoryInPlaceOfFile)
            }

            assertEquals(StoredFileReadFailureReason.IO, failure.reason)
            assertTrue(directoryInPlaceOfFile.isDirectory)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptPrimaryIsQuarantinedAndRecoveredFromLastCommittedBackup() {
        val folder = Files.createTempDirectory("transactional-recovery-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            saver.writeObject(file, Snapshot(1))
            saver.writeObject(file, Snapshot(2))
            file.writeText("{broken")

            assertEquals(Snapshot(1), saver.readObject<Snapshot>(file))
            assertEquals(Snapshot(1), saver.readObject<Snapshot>(file))
            assertTrue(
                folder.listFiles().orEmpty().any {
                    it.name.startsWith("state.json.corrupt-") && it.readText() == "{broken"
                }
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun failedReplacementCanRecoverPreviousCommittedSnapshot() {
        val folder = Files.createTempDirectory("transactional-commit-failure-").toFile()
        val file = folder.resolve("state.json")
        try {
            TransactionalFileSaver(Json).writeObject(file, Snapshot(1))
            val failingSaver = TransactionalFileSaver(Json) { source, destination ->
                if (destination == file) {
                    destination.delete()
                    throw IOException("injected replacement failure")
                }
                source.tryAtomicMove(destination)
            }

            assertFailsWith<IOException> {
                failingSaver.writeObject(file, Snapshot(2))
            }

            assertEquals(Snapshot(1), TransactionalFileSaver(Json).readObject<Snapshot>(file))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun explicitDeleteRemovesPrimaryStagingAndRecoveryFiles() {
        val folder = Files.createTempDirectory("transactional-delete-").toFile()
        val file = folder.resolve("state.json")
        val saver = TransactionalFileSaver(Json)
        try {
            saver.writeObject(file, Snapshot(1))
            saver.writeObject(file, Snapshot(2))
            saver.getBakFile(file).writeText("uncommitted")

            saver.delete(file)

            assertNull(saver.readObject<Snapshot>(file))
            assertTrue(!file.exists())
            assertTrue(!saver.getBakFile(file).exists())
            assertTrue(!saver.getRecoveryFile(file).exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun forcedProcessTerminationKeepsLastCommittedSnapshotReadable() = runBlocking {
        val folder = Files.createTempDirectory("transactional-process-kill-").toFile()
        val file = folder.resolve("state.json")
        val marker = folder.resolve("commit-ready.marker")
        val childLog = folder.resolve("child.log")
        val saver = TransactionalFileSaver(Json)
        var process: Process? = null
        try {
            saver.writeObject(file, Snapshot(1))
            val classpath = testProcessClasspath()
            assertTrue(classpath.isNotBlank())

            val childProcess = ProcessBuilder(
                javaExecutable().absolutePath,
                "-cp",
                classpath,
                TransactionalFileSaverCrashWriter::class.java.name,
                file.absolutePath,
                marker.absolutePath,
            )
                .redirectErrorStream(true)
                .redirectOutput(childLog)
                .start()
            process = childProcess

            withTimeout(20_000) {
                while (!marker.exists()) {
                    check(childProcess.isAlive) {
                        "Crash writer exited before the commit barrier: ${childLog.takeIf(File::exists)?.readText()}"
                    }
                    delay(20)
                }
            }

            childProcess.destroyForcibly()
            assertTrue(childProcess.waitFor(10, TimeUnit.SECONDS))
            assertEquals(Snapshot(1), TransactionalFileSaver(Json).readObject<Snapshot>(file))
            assertEquals("{\"value\":2}", saver.getBakFile(file).readText())
        } finally {
            process?.destroyForcibly()
            folder.deleteRecursively()
        }
        Unit
    }

    @Test
    fun forcedProcessTerminationDuringHttpDownloadResumesExactBytesAndHash() = runBlocking {
        val folder = Files.createTempDirectory("http-download-process-kill-").toFile()
        val target = folder.resolve("payload.bin")
        val stateFile = folder.resolve("payload.parts.json")
        val marker = folder.resolve("download-progress.marker")
        val childLog = folder.resolve("download-child.log")
        val payload = ByteArray(HTTP_CRASH_PAYLOAD_SIZE) { index -> (index % 251).toByte() }
        val requestedRanges = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var process: Process? = null

        server.createContext("/payload.bin") { exchange ->
            respondSlowRange(exchange, payload, requestedRanges)
        }
        server.start()

        try {
            RandomAccessFile(target, "rw").use { it.setLength(payload.size.toLong()) }
            val url = "http://127.0.0.1:${server.address.port}/payload.bin"
            val childProcess = ProcessBuilder(
                javaExecutable().absolutePath,
                "-cp",
                testProcessClasspath(),
                HttpDownloadCrashWriter::class.java.name,
                url,
                target.absolutePath,
                stateFile.absolutePath,
                marker.absolutePath,
                payload.size.toString(),
            )
                .redirectErrorStream(true)
                .redirectOutput(childLog)
                .start()
            process = childProcess

            withTimeout(30_000) {
                while (!marker.exists()) {
                    check(childProcess.isAlive) {
                        "Download child exited before the progress barrier: ${childLog.takeIf(File::exists)?.readText()}"
                    }
                    delay(20)
                }
            }

            val saver = TransactionalFileSaver(Json)
            val committedBeforeKill = requireNotNull(
                saver.readObject<HttpDownloadCrashSnapshot>(stateFile)
            )
            assertTrue(committedBeforeKill.current in 1 until payload.size.toLong())
            assertTrue(childProcess.isAlive)

            childProcess.destroyForcibly()
            assertTrue(childProcess.waitFor(10, TimeUnit.SECONDS))

            val restored = requireNotNull(saver.readObject<HttpDownloadCrashSnapshot>(stateFile))
            assertEquals(committedBeforeKill, restored)
            assertContentEquals(
                payload.copyOfRange(0, restored.current.toInt()),
                target.inputStream().use { it.readNBytes(restored.current.toInt()) },
            )

            val part = RangedPart(
                from = 0,
                to = payload.lastIndex.toLong(),
                current = restored.current,
            )
            val handle = FileSystem.SYSTEM.openReadWrite(target.toOkioPath())
            val writer = DestWriter(
                id = part.getID(),
                file = target,
                seekPos = part.current,
                writer = handle,
            )
            val terminalFailure = AtomicReference<Throwable?>(null)
            val downloader = createProcessKillDownloader(url, part, writer).also {
                it.onTooManyErrors = terminalFailure::set
            }

            try {
                withTimeout(30_000) {
                    downloader.start()
                    assertEquals(PartDownloadStatus.Completed, downloader.awaitFinishOrError())
                    downloader.join()
                }
                handle.flush()
            } finally {
                downloader.stop()
                handle.close()
            }

            saver.writeObject(
                stateFile,
                HttpDownloadCrashSnapshot(current = part.current, length = payload.size.toLong()),
            )
            assertNull(terminalFailure.get())
            assertTrue(part.isCompleted)
            assertEquals(payload.size.toLong(), requireNotNull(
                saver.readObject<HttpDownloadCrashSnapshot>(stateFile)
            ).current)
            assertContentEquals(payload, target.readBytes())
            assertEquals(HTTP_CRASH_PAYLOAD_SHA256, target.readBytes().sha256())
            assertEquals("bytes=0-${payload.lastIndex}", requestedRanges.first())
            assertEquals(
                "bytes=${restored.current}-${payload.lastIndex}",
                requestedRanges.last(),
            )
        } finally {
            process?.destroyForcibly()
            server.stop(0)
            folder.deleteRecursively()
        }
        Unit
    }

    @Test
    fun corruptDownloadRecordIsReportedInsteadOfFilteredOut() = runBlocking {
        val folder = Files.createTempDirectory("download-list-corrupt-").toFile()
        val saver = TransactionalFileSaver(Json)
        try {
            val storage = DownloadListFileStorage(folder, saver)
            folder.resolve("2.json").writeText("{broken")

            assertFailsWith<StoredFileReadException> {
                storage.getAll()
            }
            Unit
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptQueueRecordIsReportedInsteadOfFilteredOut() = runBlocking {
        val folder = Files.createTempDirectory("queue-list-corrupt-").toFile()
        val saver = TransactionalFileSaver(Json)
        try {
            folder.resolve("7.json").writeText("{broken")

            assertFailsWith<StoredFileReadException> {
                DownloadQueueFileStorageDatabase(saver, folder).getAllQueues()
            }
            Unit
        } finally {
            folder.deleteRecursively()
        }
    }

    @Serializable
    private data class Snapshot(val value: Int)

    private fun javaExecutable(): File {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        return File(File(System.getProperty("java.home"), "bin"), executable)
    }

    private fun testProcessClasspath(): String {
        val entries = linkedSetOf<String>()
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filterTo(entries) { it.isNotBlank() }

        var loader: ClassLoader? = TransactionalFileSaverSafetyTest::class.java.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) {
                loader.urLs
                    .filter { it.protocol == "file" }
                    .mapTo(entries) { File(it.toURI()).absolutePath }
            }
            loader = loader.parent
        }

        listOf(
            TransactionalFileSaverCrashWriter::class.java,
            HttpDownloadCrashWriter::class.java,
            TransactionalFileSaver::class.java,
            HttpPartDownloader::class.java,
            OkHttpClient::class.java,
            Json::class.java,
            FileSystem::class.java,
            Unit::class.java,
        ).mapNotNullTo(entries) { type ->
            type.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }?.absolutePath
        }
        return entries.joinToString(File.pathSeparator)
    }

    private fun respondSlowRange(
        exchange: HttpExchange,
        payload: ByteArray,
        requestedRanges: MutableList<String>,
    ) {
        val requestedRange = requireNotNull(exchange.requestHeaders.getFirst("Range"))
        requestedRanges += requestedRange
        val match = requireNotNull(HTTP_RANGE_PATTERN.matchEntire(requestedRange))
        val start = match.groupValues[1].toInt()
        val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
            ?: payload.lastIndex

        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
        exchange.sendResponseHeaders(206, (end - start + 1).toLong())
        try {
            exchange.responseBody.use { output ->
                var offset = start
                while (offset <= end) {
                    val length = minOf(8_192, end - offset + 1)
                    output.write(payload, offset, length)
                    output.flush()
                    offset += length
                    Thread.sleep(5)
                }
            }
        } catch (_: IOException) {
            // The first connection is expected to break when its JVM is killed forcibly.
        } finally {
            exchange.close()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val HTTP_CRASH_PAYLOAD_SIZE = 4 * 1024 * 1024
        const val HTTP_CRASH_PAYLOAD_SHA256 =
            "a117210941a0b00dcb2d8577e680d84b6fa0eaf760d2afc654c953b9859d54fa"
        val HTTP_RANGE_PATTERN = Regex("""bytes=(\d+)-(\d*)""")
    }
}

/** Child-process entry point used to stop a transactional write exactly before primary commit. */
object TransactionalFileSaverCrashWriter {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2)
        val target = File(args[0]).canonicalFile
        val marker = File(args[1])
        val staging = File("$target.tmp").canonicalFile
        val saver = TransactionalFileSaver(Json) { source, destination ->
            if (source.canonicalFile == staging && destination.canonicalFile == target) {
                FileOutputStream(marker).use { output ->
                    output.write("ready".encodeToByteArray())
                    output.fd.sync()
                }
                while (true) {
                    Thread.sleep(1_000)
                }
            }
            source.tryAtomicMove(destination)
        }
        saver.writeText(target, "{\"value\":2}")
    }
}

@Serializable
data class HttpDownloadCrashSnapshot(
    val current: Long,
    val length: Long,
)

/** Child-process entry point that persists flushed HTTP progress, then remains killable mid-transfer. */
object HttpDownloadCrashWriter {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        require(args.size == 5)
        val url = args[0]
        val target = File(args[1]).canonicalFile
        val stateFile = File(args[2]).canonicalFile
        val marker = File(args[3]).canonicalFile
        val length = args[4].toLong()
        val part = RangedPart(from = 0, to = length - 1)
        val handle = FileSystem.SYSTEM.openReadWrite(target.toOkioPath())
        val writer = DestWriter(
            id = part.getID(),
            file = target,
            seekPos = part.current,
            writer = handle,
        )
        val failure = AtomicReference<Throwable?>(null)
        val downloader = createProcessKillDownloader(url, part, writer).also {
            it.onTooManyErrors = failure::set
        }

        try {
            downloader.start()
            withTimeout(30_000) {
                while (part.current < HTTP_CRASH_COMMIT_THRESHOLD) {
                    failure.get()?.let { throw it }
                    delay(10)
                }
            }

            val committedCurrent = part.current
            handle.flush()
            TransactionalFileSaver(Json).writeObject(
                stateFile,
                HttpDownloadCrashSnapshot(current = committedCurrent, length = length),
            )
            FileOutputStream(marker).use { output ->
                output.write("ready".encodeToByteArray())
                output.fd.sync()
            }

            downloader.awaitFinishOrError()
            downloader.join()
            failure.get()?.let { throw it }
        } finally {
            downloader.stop()
            handle.close()
        }
    }

    private const val HTTP_CRASH_COMMIT_THRESHOLD = 256L * 1024L
}

private fun createProcessKillDownloader(
    url: String,
    part: RangedPart,
    writer: DestWriter,
): HttpPartDownloader = HttpPartDownloader(
    credentials = HttpDownloadCredentials(url),
    getDestWriter = { writer },
    part = part,
    client = OkHttpHttpDownloaderClient(
        okHttpClient = OkHttpClient.Builder()
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
        customUserAgentProvider = object : UserAgentProvider {
            override fun getUserAgent() = "process-kill-download-test"
        },
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String) = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    ),
    speedLimiters = emptyList(),
    strictMode = true,
    partSplitLock = Any(),
    minPartProvider = MinPartSizeProvider { 1 },
    allowInternalRetries = false,
)
