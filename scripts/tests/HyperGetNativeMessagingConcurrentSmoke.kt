// HyperGet source-only regression: parallel replies must not interleave frame headers and bodies.
// Compile only these Kotlin files with kotlinc; never Gradle or Windows packaging.
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.StdioProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private class GateOutputStream : OutputStream() {
    private val sink = ByteArrayOutputStream()
    val firstPrefixWritten = CountDownLatch(1)
    val releaseFirstPrefix = CountDownLatch(1)
    private var writes = 0

    override fun write(b: Int) = synchronized(sink) { sink.write(b) }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val first = synchronized(sink) {
            sink.write(b, off, len)
            writes++ == 0
        }
        if (first) {
            firstPrefixWritten.countDown()
            check(releaseFirstPrefix.await(5, TimeUnit.SECONDS)) { "Timed out waiting for frame gate" }
        }
    }

    fun snapshot(): ByteArray = synchronized(sink) { sink.toByteArray() }
}

private fun frame(message: String): ByteArray {
    val payload = message.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(payload.size).array() + payload
}

fun main() {
    val gate = GateOutputStream()
    val protocol = StdioProtocol(ByteArrayInputStream(byteArrayOf()), gate)
    val failure = AtomicReference<Throwable?>(null)
    val secondStarted = CountDownLatch(1)
    val secondDone = CountDownLatch(1)
    val first = Thread {
        try { protocol.writeMessage("HyperGet-first") } catch (e: Throwable) { failure.set(e) }
    }
    val second = Thread {
        secondStarted.countDown()
        try { protocol.writeMessage("HyperGet-second") } catch (e: Throwable) { failure.set(e) }
        finally { secondDone.countDown() }
    }
    first.start()
    check(gate.firstPrefixWritten.await(3, TimeUnit.SECONDS))
    second.start()
    check(secondStarted.await(3, TimeUnit.SECONDS))
    // The first frame is paused immediately after writing its length prefix.
    // The second sender must not finish while the first frame is incomplete.
    val prematurelyCompleted = secondDone.await(350, TimeUnit.MILLISECONDS)
    gate.releaseFirstPrefix.countDown()
    first.join(3000)
    second.join(3000)
    check(!first.isAlive && !second.isAlive) { "Writer threads did not terminate" }
    failure.get()?.let { throw it }
    check(!prematurelyCompleted) { "Second sender overtook the first incomplete frame" }
    check(gate.snapshot().contentEquals(frame("HyperGet-first") + frame("HyperGet-second"))) {
        "Native-messaging output frames were interleaved"
    }
    println("PASS HyperGet concurrent native-messaging frame serialization (real StdioProtocol source)")
}
