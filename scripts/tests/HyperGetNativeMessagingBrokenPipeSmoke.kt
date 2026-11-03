// HyperGet source-only regression. Compile ONLY StdioProtocol.kt, NativeMessagingFinished.kt
// and this file with kotlinc; do not build or package the Windows application.
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.StdioProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private enum class FailureAt { PREFIX, BODY, FLUSH }

private class BrokenOutput(private val failureAt: FailureAt) : OutputStream() {
    private val sink = ByteArrayOutputStream()
    var writeCalls = 0
        private set
    var flushCalls = 0
        private set

    override fun write(b: Int) { sink.write(b) }

    override fun write(b: ByteArray, off: Int, len: Int) {
        writeCalls++
        val fail = (failureAt == FailureAt.PREFIX && writeCalls == 1) ||
            (failureAt == FailureAt.BODY && writeCalls == 2)
        if (fail) {
            sink.write(b, off, minOf(2, len))
            throw IOException("simulated partial $failureAt write")
        }
        sink.write(b, off, len)
    }

    override fun flush() {
        flushCalls++
        if (failureAt == FailureAt.FLUSH) throw IOException("simulated flush failure")
    }

    fun snapshot() = sink.toByteArray()
}

private fun frame(message: String): ByteArray {
    val body = message.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(body.size).array() + body
}

fun main() {
    var cases = 0
    for (failureAt in FailureAt.entries) {
        val output = BrokenOutput(failureAt)
        val protocol = StdioProtocol(ByteArrayInputStream(byteArrayOf()), output)
        val firstError = try {
            protocol.writeMessage("HyperGet-first")
            error("Expected $failureAt write to fail")
        } catch (e: IOException) { e }
        cases++

        val before = output.snapshot()
        val writesBefore = output.writeCalls
        val flushesBefore = output.flushCalls
        val secondError = try {
            protocol.writeMessage("HyperGet-second")
            error("Broken $failureAt stream accepted another frame")
        } catch (e: IOException) { e }
        check(secondError.cause === firstError) { "Original output failure must be preserved" }
        cases++
        check(output.snapshot().contentEquals(before))
        check(output.writeCalls == writesBefore && output.flushCalls == flushesBefore) {
            "Broken $failureAt stream was written or flushed again"
        }
        cases++
    }

    val healthy = ByteArrayOutputStream()
    val freshProtocol = StdioProtocol(ByteArrayInputStream(byteArrayOf()), healthy)
    freshProtocol.writeMessage("HyperGet-healthy")
    check(healthy.toByteArray().contentEquals(frame("HyperGet-healthy")))
    cases++

    println("PASS: $cases HyperGet native-messaging broken-pipe cases (isolated Kotlin/JVM)")
}
