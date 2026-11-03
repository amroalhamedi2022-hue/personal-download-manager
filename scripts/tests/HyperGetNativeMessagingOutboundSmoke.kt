// Source-only HyperGet outbound native-messaging regression; no Gradle/EXE packaging.
// kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingOutboundSmoke.kt -d /tmp/hyperget-outbound-classes
// kotlin -classpath /tmp/hyperget-outbound-classes HyperGetNativeMessagingOutboundSmokeKt
import com.abdownloadmanager.desktop.nativemessaging.host.stdio.StdioProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MIB = 1024 * 1024

private fun frame(body: String): ByteArray {
    val bytes = body.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(bytes.size).array() + bytes
}

private inline fun <reified T : Throwable> expectFailure(label: String, block: () -> Unit) {
    try {
        block()
        error("$label: expected ${T::class.simpleName}")
    } catch (e: Throwable) {
        check(e is T) { "$label: expected ${T::class.simpleName}, got ${e::class.simpleName}" }
    }
}

fun main() {
    var cases = 0
    fun pass() { cases++ }

    val output = ByteArrayOutputStream()
    val protocol = StdioProtocol(ByteArrayInputStream(byteArrayOf()), output)
    protocol.writeMessage("HyperGet")
    check(output.toByteArray().contentEquals(frame("HyperGet")))
    pass()

    output.reset()
    val unicode = "HyperGet — تنزيل 📥"
    protocol.writeMessage(unicode)
    check(output.toByteArray().contentEquals(frame(unicode)))
    pass()

    output.reset()
    protocol.writeMessage("x".repeat(MIB))
    check(output.size() == MIB + 4)
    check(ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.nativeOrder()).int == MIB)
    pass()

    output.reset()
    expectFailure<IllegalArgumentException>("outbound 1 MiB + 1") {
        protocol.writeMessage("x".repeat(MIB + 1))
    }
    check(output.size() == 0) { "Oversized frame wrote bytes before rejection" }
    pass()

    output.reset()
    expectFailure<IllegalArgumentException>("multibyte outbound 1 MiB + 1") {
        protocol.writeMessage("é".repeat(MIB / 2 + 1))
    }
    check(output.size() == 0)
    pass()

    for ((name, bad) in listOf("lone high surrogate" to "\uD800", "lone low surrogate" to "\uDC00")) {
        output.reset()
        expectFailure<IllegalArgumentException>(name) { protocol.writeMessage("bad$bad") }
        check(output.size() == 0) { "$name wrote a replacement character to stdout" }
        pass()
    }

    output.reset()
    protocol.writeMessage("\uD83D\uDCE5") // valid surrogate pair: inbox emoji
    check(output.toByteArray().contentEquals(frame("📥")))
    pass()

    // Inbound safety limit remains 4 MiB; outbound limit must not restrict reads.
    val inbound = "z".repeat(2 * MIB)
    check(StdioProtocol(ByteArrayInputStream(frame(inbound)), ByteArrayOutputStream()).readMessage() == inbound)
    pass()

    output.reset()
    protocol.writeMessage("after-rejection")
    check(output.toByteArray().contentEquals(frame("after-rejection")))
    pass()

    println("PASS: $cases HyperGet outbound native-messaging cases (isolated Kotlin/JVM)")
}
