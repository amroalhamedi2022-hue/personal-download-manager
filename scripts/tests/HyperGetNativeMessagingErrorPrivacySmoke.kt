// Isolated Kotlin/JVM source test. No Gradle, Compose, installer or EXE.
import com.abdownloadmanager.desktop.nativemessaging.host.safeBrowserError
import com.abdownloadmanager.desktop.nativemessaging.host.browserReplyId
import java.io.IOException

fun main() {
    var cases = 0
    val secrets = listOf(
        "Authorization: Bearer super-secret-token",
        "https://example.invalid/private?signature=abc123",
        "password=my-private-password",
        "Cookie: session=private-cookie"
    )
    for (secret in secrets) {
        for (error in listOf(IllegalArgumentException(secret), IOException(secret), RuntimeException(secret))) {
            val result = safeBrowserError(error)
            check(secret !in result.message && secret !in result.errorType)
            check(result.errorType == if (error is IllegalArgumentException) "INVALID_REQUEST" else "REQUEST_FAILED")
            check(result.message.startsWith("HyperGet "))
            cases++
        }
    }
    val benign = safeBrowserError(IllegalArgumentException("bad input"))
    check(benign == safeBrowserError(IllegalArgumentException("different sensitive input")))
    cases++
    check(safeBrowserError(IllegalStateException("internal detail")) == safeBrowserError(IOException("other detail")))
    cases++
    var generated = 0
    check(browserReplyId("B_original-request", { generated++; "N_generated" }) == "B_original-request")
    cases++
    check(generated == 0) // must not generate a different ID for an existing request
    cases++
    check(browserReplyId(null, { generated++; "N_generated" }) == "N_generated" && generated == 1)
    cases++
    println("PASS: $cases HyperGet browser-error privacy/correlation source cases")
}
