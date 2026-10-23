// Source-only HyperGet native-messaging protocol smoke test.
// Run without Gradle, desktop packaging or installer generation:
// kotlinc desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/nativemessaging/host/stdio/{NativeMessagingFinished,StdioProtocol}.kt scripts/tests/HyperGetNativeMessagingFramingSmoke.kt -include-runtime -d /tmp/hyperget-native-framing-test.jar
// java -jar /tmp/hyperget-native-framing-test.jar
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.InvalidNativeMessagingFrameException
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.NativeMessagingFinished
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.StdioProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun header(size: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(size).array()

private fun frame(content: String): ByteArray {
    val payload = content.toByteArray(Charsets.UTF_8)
    return header(payload.size) + payload
}

private inline fun <reified T : Throwable> expectFailure(label: String, block: () -> Unit) {
    try {
        block()
        error("$label: expected ${T::class.simpleName}")
    } catch (e: Throwable) {
        check(e is T) { "$label: expected ${T::class.simpleName}, got ${e::class.simpleName}: $e" }
    }
}

private fun reader(input: ByteArray): StdioProtocol =
    StdioProtocol(ByteArrayInputStream(input), ByteArrayOutputStream())

fun main() {
    var cases = 0
    fun pass() { cases++ }

    check(reader(frame("""{"action":"ping"}""")).readMessage() == """{"action":"ping"}""")
    pass()

    val unicode = """{"name":"HyperGet — تنزيل"}"""
    check(reader(frame(unicode)).readMessage() == unicode)
    pass()

    val stream = StdioProtocol(
        ByteArrayInputStream(frame("first") + frame("second")),
        ByteArrayOutputStream(),
    )
    check(stream.readMessage() == "first" && stream.readMessage() == "second")
    pass()

    val output = ByteArrayOutputStream()
    val writer = StdioProtocol(ByteArrayInputStream(byteArrayOf()), output)
    writer.writeMessage("hello")
    check(output.toByteArray().contentEquals(frame("hello")))
    pass()

    // Zero, negative (unsigned overflow), and oversized prefixes must all
    // fail with the fatal framing type, not a recoverable JSON parse error.
    for (badLength in listOf(0, -1, 4 * 1024 * 1024 + 1)) {
        expectFailure<InvalidNativeMessagingFrameException>("bad length $badLength") {
            reader(header(badLength) + frame("must not be reinterpreted")).readMessage()
        }
        pass()
    }

    // The allowed upper bound remains 4 MiB (Firefox's native-host limit).
    val maximum = "x".repeat(4 * 1024 * 1024)
    check(reader(frame(maximum)).readMessage().length == maximum.length)
    pass()

    expectFailure<IllegalArgumentException>("oversized outbound message") {
        writer.writeMessage("x".repeat(4 * 1024 * 1024 + 1))
    }
    pass()

    expectFailure<EOFException>("truncated length prefix") {
        reader(byteArrayOf(1, 0)).readMessage()
    }
    pass()

    expectFailure<EOFException>("truncated body") {
        reader(header(8) + byteArrayOf(1, 2)).readMessage()
    }
    pass()

    expectFailure<NativeMessagingFinished>("clean disconnect") {
        reader(byteArrayOf()).readMessage()
    }
    pass()

    println("PASS: $cases HyperGet native-messaging framing cases (isolated Kotlin/JVM)")
}
