package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private var passed = 0
private fun checkCase(ok: Boolean, name: String) { check(ok) { name }; passed++ }
private fun withDir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("hyperget-ftp-stage-identity-")
    try { block(dir) } finally { dir.toFile().deleteRecursively() }
}
private inline fun failsIo(block: () -> Unit) = try { block(); false } catch (_: IOException) { true }

fun main() {
    withDir { dir ->
        val part = dir.resolve("ok.ftp-part")
        val final = dir.resolve("ok.bin")
        val open = openFtpStagedOutput(part, 0)
        val expected = byteArrayOf(1, 2, 3)
        open.use { it.write(expected) }
        publishFtpStagedFile(part, final, expected.size.toLong(), open.fileKey)
        checkCase(Files.readAllBytes(final).contentEquals(expected), "ordinary FTP publish")
        checkCase(!Files.exists(part), "ordinary FTP stage cleanup")
    }
    withDir { dir ->
        val part = dir.resolve("short.ftp-part")
        val final = dir.resolve("short.bin")
        Files.write(part, byteArrayOf(1, 2))
        checkCase(failsIo { publishFtpStagedFile(part, final, 3) }, "truncated stage must reject")
        checkCase(!Files.exists(final), "truncated stage must not publish")
    }
    withDir { dir ->
        val part = dir.resolve("link.ftp-part")
        val final = dir.resolve("link.bin")
        val other = dir.resolve("other.bin")
        Files.write(other, byteArrayOf(9, 8))
        Files.createSymbolicLink(part, other.fileName)
        checkCase(failsIo { publishFtpStagedFile(part, final, 2) }, "symlink stage must reject")
        checkCase(Files.readAllBytes(other).contentEquals(byteArrayOf(9, 8)), "symlink target untouched")
    }
    withDir { dir ->
        val part = dir.resolve("swapped.ftp-part")
        val final = dir.resolve("swapped.bin")
        val moved = dir.resolve("original-opened.bin")
        val original = byteArrayOf(1, 2, 3)
        val open = openFtpStagedOutput(part, 0)
        open.use { it.write(original) }
        Files.move(part, moved)
        Files.write(part, byteArrayOf(7, 8, 9))
        if (open.fileKey != null) {
            checkCase(failsIo { publishFtpStagedFile(part, final, 3, open.fileKey) }, "same-size replacement must reject")
            checkCase(!Files.exists(final), "same-size replacement must not publish")
        } else {
            println("SKIP fileKey-specific check: filesystem returned null")
        }
        checkCase(Files.readAllBytes(moved).contentEquals(original), "opened stage preserved")
    }
    println("PASS $passed/$passed HyperGet FTP stage identity source checks")
}
