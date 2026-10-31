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
import kotlinx.coroutines.GlobalScope
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import java.net.SocketTimeoutException
import java.io.Closeable
import okio.Buffer
import okio.Source
import okio.use
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val PART_MAX_TRIES = 10
const val RetryDelay = 1_000L

// A cancelled coroutine must not retry, and a requested stop takes effect
// before the next download/connection attempt.
internal fun shouldContinuePartWorker(coroutineActive: Boolean, stopRequested: Boolean): Boolean =
    coroutineActive && !stopRequested

// Deadline applies only to response acquisition, not streaming the file body.
private const val PART_RESPONSE_DEADLINE_MILLIS = 45_000L

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
    val getDestWriter: () -> DestWriter
) {
    private var thread: Thread? = null
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
            stop = false
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

            result.onFailure {
                if (it is ShouldNotHappened) {
                    throw it
                }
            }
        }
        this.workerJob = workerJob
        workerJob.invokeOnCompletion {
            // A worker cancelled before its first dispatch never reaches the coroutine
            // body. Release its active flag here, and avoid clearing a newer run.
            synchronized(this) {
                if (this.workerJob === workerJob) {
                    active = false
                    if (!part.isCompleted) {
                        part.statusFlow.value = PartDownloadStatus.IDLE
                    }
                }
            }
            // A SupervisorJob does not complete merely because its child completed.
            // Close the per-run scope so it cannot remain active after the part is done.
            scope.coroutineContext.job.cancel()
        }
    }

    @Volatile
    var stop = false
    fun stop() {
        val connectionToClose = synchronized(connectionLock) {
            stop = true
            activeConnection
        }
        // Closing the real transport releases blocked reads, not just the coroutine.
        runCatching { connectionToClose?.close() }
        thread?.interrupt()
        scope?.coroutineContext?.job?.cancel()
    }

    suspend fun join() {
        withContext(Dispatchers.IO) {
            workerJob?.join()
            thread?.join()
        }
    }


    private fun canRetry(e: Throwable): CanRetryResult {
        return when {
            ExceptionUtils.isNormalCancellation(e) -> {
                CanRetryResult.No
            }

            e is DownloadValidationException -> if (e.isCritical()) {
                //download validation occurs, and also it is critical,
                //so we can't proceed any further
                CanRetryResult.NoAndStopDownloadJob
            } else {
                CanRetryResult.Yes
            }

            else -> {
                CanRetryResult.Yes
            }
        }
    }

    lateinit var onTooManyErrors: ((Throwable) -> Unit)
    private fun iCantRetryAnymore(throwable: Throwable) {
        lastCriticalException = throwable
        GlobalScope.launch {
            onTooManyErrors(throwable)
        }
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
        val conn = awaitPartResponseWithDeadline(PART_RESPONSE_DEADLINE_MILLIS) {
            connectAndVerify()
        }
        synchronized(connectionLock) {
            if (stop) {
                conn.close()
                onCanceled(kotlinx.coroutines.CancellationException())
                return
            }
            activeConnection = conn
        }
        try {
            thread = thread {
                if (stop || Thread.currentThread().isInterrupted) {
                    conn.close()
                    onCanceled(kotlinx.coroutines.CancellationException())
                    synchronized(connectionLock) {
                        if (activeConnection === conn) activeConnection = null
                    }
                    return@thread
                }
                try {
                    conn.use {
                        getDestWriter().use { writer ->
                            copyDataSync(it.source, writer)
                        }
                    }
                } catch (e: Exception) {
                    onCanceled(e)
                } finally {
                    synchronized(connectionLock) {
                        if (activeConnection === conn) activeConnection = null
                    }
                    thread = null
                }
            }
        } catch (e: Exception) {
            synchronized(connectionLock) {
                if (activeConnection === conn) activeConnection = null
            }
            conn.close()
            throw e
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
            if (stop || Thread.currentThread().isInterrupted) {
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
                // A prematurely closed ranged response is not a completed download:
                // keep the persisted byte offset and let the existing retry flow resume.
                if (part is RangedPart && part.to != null && !part.isCompleted) {
                    onCanceled(java.io.EOFException("Range ended at byte ${part.current}; expected end ${part.to}"))
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


