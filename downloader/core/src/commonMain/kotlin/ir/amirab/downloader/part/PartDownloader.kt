package ir.amirab.downloader.part

import ir.amirab.downloader.anntation.HeavyCall
import ir.amirab.downloader.connection.Connection
import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.exception.DownloadValidationException
import ir.amirab.downloader.exception.PartTooManyErrorException
import ir.amirab.downloader.utils.ExceptionUtils
import ir.amirab.downloader.utils.printStackIfNOtUsual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.net.SocketTimeoutException
import okio.Buffer
import okio.Source
import okio.use
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val PART_MAX_TRIES = 10
const val RetryDelay = 1_000L

// Bounds only the pre-response connection phase; long streaming bodies remain unrestricted.
private const val PART_RESPONSE_DEADLINE_MILLIS = 45_000L

// Reserve four workers under the default 64-worker Dispatchers.IO ceiling while
// retaining enough body-copy capacity for the installed 16-download workload. The
// 48-copy experiment reduced peak memory modestly but failed the controlled 16-way
// throughput gate; 60 is the next bounded trial, not accepted until Windows A/B passes.
// Acquire before Dispatchers.IO so excess parts suspend without occupying IO workers.
// This does not cap task admission or change the independent OkHttp request-dispatch limits.
internal const val PART_BODY_COPY_PARALLELISM = 60
private val partBodyCopyPermits = Semaphore(PART_BODY_COPY_PARALLELISM)
// Keep one connection slot available for each of the 16 queued HTTP jobs even
// when earlier downloads start eight ranges each. Non-primary parts may use
// at most 44 of the 60 shared slots; all parts still acquire the global gate.
internal const val PART_PRIMARY_COPY_RESERVE = 16
private val partNonPrimaryCopyPermits = Semaphore(PART_BODY_COPY_PARALLELISM - PART_PRIMARY_COPY_RESERVE)

internal suspend fun <T> withPartBodyCopyPermit(block: suspend () -> T): T {
    partBodyCopyPermits.acquire()
    try {
        return block()
    } finally {
        partBodyCopyPermits.release()
    }
}

/** The first range/segment receives protected admission; extra ranges cannot starve it. */
internal suspend fun <T> withFairPartBodyCopyPermit(isPrimaryPart: Boolean, block: suspend () -> T): T {
    if (isPrimaryPart) return withPartBodyCopyPermit(block)
    partNonPrimaryCopyPermits.acquire()
    try {
        return withPartBodyCopyPermit(block)
    } finally {
        partNonPrimaryCopyPermits.release()
    }
}

// A stop request must win even if the worker coroutine has not been dispatched yet.
internal fun shouldContinuePartWorker(coroutineActive: Boolean, stopRequested: Boolean): Boolean =
    coroutineActive && !stopRequested

/**
 * A suspended HTTP call may otherwise leave a part Connecting forever while the
 * persisted job reports Downloading. Turn the response-header deadline into an
 * IOException so the existing retry budget handles it; never turn user/parent
 * cancellation into a network retry. Close a connection acquired at the race edge.
 */
internal suspend fun <T : Closeable> awaitPartResponseWithDeadline(
    timeoutMillis: Long,
    connect: suspend () -> T,
): T {
    require(timeoutMillis > 0L)
    var acquired: T? = null
    try {
        return withTimeout(timeoutMillis) {
            connect().also { acquired = it }
        }
    } catch (timeout: TimeoutCancellationException) {
        runCatching { acquired?.close() }
        // An enclosing timeout cancels our caller; it is NOT a retryable socket error.
        currentCoroutineContext().ensureActive()
        throw SocketTimeoutException("HTTP response headers were not received before the connection deadline")
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        runCatching { acquired?.close() }
        throw cancelled
    }
}


abstract class PartDownloader<
        TPart : DownloadPart
        >(
    val part: TPart,
    val getDestWriter: () -> DestWriter,
    private val allowInternalRetries: Boolean = true,
) {
    private val connectionLock = Any()
    private var activeConnection: Connection<*>? = null
    private var scope: CoroutineScope? = null
    private var workerJob: Job? = null
    private val _statusFlow = part.statusFlow
    val statusFlow = _statusFlow.asStateFlow()

    @Volatile
    internal var active = false

    abstract fun howMuchCanRead(maxAllowed: Long): Long

    @Volatile
    internal var tries = 0

    // make sure to not lake resource in this exception
    @Volatile
    private var lastCriticalException: Throwable? = null

    // make sure to not lake resource in this exception
    @Volatile
    private var lastException: Throwable? = null

    //just turn on (fast)
    fun start() {
        synchronized(this) {
            if (active) {
                return
            }
            synchronized(connectionLock) {
                stop = false
            }
            active = true
        }
        val scope = CoroutineScope(SupervisorJob()).also {
            this.scope = it
        }
        val workerJob = scope.launch {
            tries = 0
            lastCriticalException = null
            lastException = null
            val result = runCatching {
                while (shouldContinuePartWorker(coroutineContext.isActive, stop)) {
                    if (tries > 0) {
                        delay(RetryDelay.milliseconds)
//                        println("#${part.from}retrying $tries")
                    }
                    if (haveToManyErrors()) {
//                        println("tell them we have error!")
                        iCantRetryAnymore(
                            PartTooManyErrorException(
                                part,
                                lastException
                                    ?: Exception("BUG : if you see me please report it to the developer! when we encounter error so it have to be a least one last exception"),
                            )
                        )
                    }
                    if (part.isCompleted) {
                        println("WARNING $part is completed")
                    }
                    try {
                        download()
                    } catch (e: Exception) {
                        tries++
                        onCanceled(e)
                        when (canRetry(e)) {
                            CanRetryResult.Yes -> continue
                            CanRetryResult.No -> {}
                            CanRetryResult.NoAndStopDownloadJob -> iCantRetryAnymore(e)
                        }
                        break
                    }
                    //download progress started, but maybe we have errors
                    //wait for a finish/error event...
                    //await for cancel status to be emitted!
                    val status = withContext(NonCancellable) {
                        awaitFinishOrError()
                    }
                    when (status) {
                        is PartDownloadStatus.Canceled -> {
                            tries++
                            when (canRetry(status.e)) {
                                CanRetryResult.Yes -> continue
                                CanRetryResult.No -> {}
                                CanRetryResult.NoAndStopDownloadJob -> iCantRetryAnymore(status.e)
                            }
                            break
                        }

                        PartDownloadStatus.Completed -> break
                        else -> throw ShouldNotHappened("should not happened!")
                    }
                }
            }

            active = false
            if (!part.isCompleted) {
                part.statusFlow.value = PartDownloadStatus.IDLE
            }

            result.onFailure {
                if (it is ShouldNotHappened) {
                    throw it
                }
            }
        }
        this.workerJob = workerJob
        workerJob.invokeOnCompletion {
            // A SupervisorJob does not complete merely because its child completed.
            // Close the per-run scope so it cannot remain active after the part is done.
            scope.coroutineContext.job.cancel()
        }
    }

    @Volatile
    var stop = false

    /**
     * Closing the active response is the cancellation signal for a blocking Okio read.
     * Interrupting a newly-created thread is not sufficient for every Source implementation.
     */
    fun stop() {
        val connectionToClose = synchronized(connectionLock) {
            stop = true
            activeConnection
        }
        runCatching { connectionToClose?.close() }
        scope?.coroutineContext?.job?.cancel()
    }

    suspend fun join() {
        workerJob?.join()
    }


    private fun canRetry(e: Throwable): CanRetryResult {
        return when {
            ExceptionUtils.isNormalCancellation(e) -> {
                CanRetryResult.No
            }

            e is DownloadValidationException -> if (e.isCritical() || !allowInternalRetries) {
                //download validation occurs, and also it is critical,
                //so we can't proceed any further
                CanRetryResult.NoAndStopDownloadJob
            } else {
                CanRetryResult.Yes
            }

            !allowInternalRetries -> CanRetryResult.NoAndStopDownloadJob

            else -> {
                CanRetryResult.Yes
            }
        }
    }

    lateinit var onTooManyErrors: ((Throwable) -> Unit)
    private fun iCantRetryAnymore(throwable: Throwable) {
        lastCriticalException = throwable
        onTooManyErrors(throwable)
    }

    private fun haveToManyErrors(): Boolean {
        return tries >= PART_MAX_TRIES
    }

    private fun haveCriticalError(): Boolean {
        return lastCriticalException != null
    }

    internal fun injured(): Boolean {
        return haveToManyErrors() || haveCriticalError()
    }

    abstract suspend fun connectAndVerify(): Connection<*>

    private suspend fun download() {
        onNewStatus(PartDownloadStatus.Connecting)
        // Reserve a body-copy slot BEFORE opening a network connection. Previously all
        // pending parts could connect first, leaving hundreds of idle HTTP responses
        // (and their socket/Okio buffers) resident while only 60 parts could copy.
        // The permit covers both the response-header wait and the body lifecycle.
        withFairPartBodyCopyPermit(part.getID() == 0L) {
            downloadWithReservedConnectionSlot()
        }
    }

    private suspend fun downloadWithReservedConnectionSlot() {
        val conn = awaitPartResponseWithDeadline(PART_RESPONSE_DEADLINE_MILLIS) {
            connectAndVerify()
        }

        // Register before entering the blocking body copy. If stop wins the race,
        // close the just-acquired connection instead of leaking a response stream.
        val registered = synchronized(connectionLock) {
            if (stop) {
                false
            } else {
                activeConnection = conn
                true
            }
        }
        if (!registered) {
            runCatching { conn.close() }
            onCanceled(kotlinx.coroutines.CancellationException("Part stopped before body copy started"))
            return
        }

        try {
            // The shared permit is already held while the connection is opened.
            // Keep the connection in use() so cancellation closes its response.
            conn.use { connection ->
                withContext(Dispatchers.IO) {
                    getDestWriter().use { writer ->
                        copyDataSync(connection.source, writer)
                    }
                }
            }
        } catch (e: Exception) {
            onCanceled(e)
        } finally {
            synchronized(connectionLock) {
                if (activeConnection === conn) {
                    activeConnection = null
                }
            }
        }
    }

    protected open fun onCanceled(e: Throwable) {
        lastException = e
        val canceled = PartDownloadStatus.Canceled(e)
        onNewStatus(canceled)
        e.printStackIfNOtUsual()
//        if (!canceled.isNormalCancellation()) {
//            e.printStackTrace()
//        } else {
//            println("part cancelled because of ${e.localizedMessage ?: e::class.simpleName}")
//            e.printStackTrace()
//        }
    }

    protected open fun onFinish() {
        onNewStatus(PartDownloadStatus.Completed)
    }

    fun onNewStatus(partDownloadStatus: PartDownloadStatus) {
        _statusFlow.value = partDownloadStatus
    }

    @HeavyCall
    private fun copyDataSync(source: Source, destWriter: DestWriter) {
//        println("copying range to file --- ${part.current}-${part.to}")
        val buffer = Buffer()
        var totalReadCount = 0L
        var firstLoop = true
        val bufferSize = DEFAULT_BUFFER_SIZE.toLong()
        while (true) {
            if (stop) {
                onCanceled(kotlinx.coroutines.CancellationException())
                break
            }
            val howMuchICanReadAllowed = howMuchCanRead(bufferSize)
            val homMuchReadFromBuffer = min(bufferSize, howMuchICanReadAllowed)
//            require(part.current + homMuchReadFromBuffer <= part.maxAllowedCurrent) {
//                """$partSplitSupport
//                canRead:${homMuchReadFromBuffer}"""
//            }
//            require(part.current + homMuchReadFromBuffer <= partSplitSupport.safeZone + 1) {
//                """a
//                    part=${part} isCompleted =${part.isCompleted}
//                    split part $partSplitSupport
//                    howMuch:${homMuchReadFromBuffer}
//                    actual:${part.current + homMuchReadFromBuffer}
//                    expected:${partSplitSupport.safeZone}
//                """.trimIndent()
//            }
            if (howMuchICanReadAllowed <= 0) {
                if (part.isCompleted) {
                    onFinish()
                } else {
                    onCanceled(kotlinx.coroutines.CancellationException("it seems our part was split so we are canceled $part"))
                }
                break
            }
            val readCount = source.read(buffer, homMuchReadFromBuffer)
//            require(readCount <= homMuchReadFromBuffer) {
//                "read count $readCount is bigger than homMuchReadFromBuffer $homMuchReadFromBuffer"
//            }
            if (readCount == -1L) {
                // A socket can end before its advertised range is complete. Never
                // persist a truncated known-length range as successfully finished;
                // retry from part.current instead of corrupting the final file.
                if (part is RangedPart && part.to != null && !part.isCompleted) {
                    onCanceled(java.io.EOFException("HTTP range ended at byte ${part.current} before the expected end ${part.to}"))
                } else {
                    onFinish()
                }
                break
            }
            destWriter.write(buffer, readCount)
            totalReadCount += readCount
            part.current += readCount
//                require (part.current-part.from == totalReadCount)
            if (firstLoop) {
                tries = 0
                onNewStatus(PartDownloadStatus.ReceivingData)
                firstLoop = false
            }
        }
    }

    suspend fun awaitFinishOrError(): PartDownloadStatus {
        return statusFlow.filter {
            when (it) {
                PartDownloadStatus.Completed,
                is PartDownloadStatus.Canceled,
                    -> true

                PartDownloadStatus.ReceivingData,
                PartDownloadStatus.Connecting,
                PartDownloadStatus.IDLE,
                    -> false
            }
        }.first()
    }

    suspend fun awaitToEnsureDataBeingTransferred(): Boolean {
        return withTimeoutOrNull(5.seconds) {
            val isThatOk = statusFlow.filter {
                when (it) {
                    PartDownloadStatus.Completed,
                    PartDownloadStatus.ReceivingData,
                        -> true

                    is PartDownloadStatus.Canceled,
                    PartDownloadStatus.Connecting,
                    PartDownloadStatus.IDLE,
                        -> false
                }
            }.first().let {
                when (it) {
                    is PartDownloadStatus.Canceled -> false
                    PartDownloadStatus.Completed -> true
                    PartDownloadStatus.ReceivingData -> true
                    PartDownloadStatus.Connecting,
                    PartDownloadStatus.IDLE,
                        -> error("should not happen")
                }
            }
            isThatOk
        } ?: false
    }

    suspend fun awaitIdle() {
        statusFlow.filter {
            when (it) {
                is PartDownloadStatus.Canceled,
                PartDownloadStatus.Completed,
                PartDownloadStatus.IDLE,
                    -> true

                PartDownloadStatus.Connecting,
                PartDownloadStatus.ReceivingData,
                    -> false
            }
        }.first()
    }

    class ShouldNotHappened(msg: String?) : RuntimeException(msg)
    private sealed interface CanRetryResult {
        data object Yes : CanRetryResult
        data object No : CanRetryResult
        data object NoAndStopDownloadJob : CanRetryResult
    }
}


