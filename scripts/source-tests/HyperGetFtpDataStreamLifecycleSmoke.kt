import ir.amirab.downloader.ftp.withFtpDataStream
import java.io.ByteArrayInputStream
import java.io.IOException

private class TrackedStream : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
    var closed = false
    override fun close() {
        closed = true
        super.close()
    }
}

fun main() {
    var cases = 0
    run {
        val stream = TrackedStream()
        var unregistered = false
        var enteredBody = false
        val result = runCatching {
            withFtpDataStream(stream, { unregistered = true }, { throw IOException("paused") }) {
                enteredBody = true
            }
        }
        check(result.exceptionOrNull() is IOException)
        check(!enteredBody && stream.closed && unregistered)
        cases++
    }
    run {
        val stream = TrackedStream()
        var unregistered = false
        val bytes = withFtpDataStream(stream, { unregistered = true }, {}, { stream.read() })
        check(bytes == 1 && stream.closed && unregistered)
        cases++
    }
    run {
        val stream = TrackedStream()
        var unregistered = false
        val result = runCatching {
            withFtpDataStream(stream, { unregistered = true }, {}, { throw IllegalStateException("body") })
        }
        check(result.exceptionOrNull() is IllegalStateException)
        check(stream.closed && unregistered)
        cases++
    }
    run {
        val stream = TrackedStream()
        val result = runCatching {
            withFtpDataStream(stream, { throw IllegalStateException("unregister") }, {}, { 5 })
        }
        check(result.exceptionOrNull() is IllegalStateException && stream.closed)
        cases++
    }
    println("PASS HyperGet FTP data stream lifecycle: $cases cases")
}
