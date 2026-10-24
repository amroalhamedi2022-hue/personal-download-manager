package ir.amirab.downloader.ftp

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

private var checks = 0
private fun checkCase(ok: Boolean, description: String) {
    check(ok) { description }
    checks++
}
private inline fun withDir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("hyperget-ftp-publish-")
    try { block(dir) } finally { dir.toFile().deleteRecursively() }
}
fun main() {
    withDir { dir ->
        val part = dir.resolve("complete.bin.ftp-part")
        val target = dir.resolve("complete.bin")
        val expected = ByteArray(4096) { (it % 251).toByte() }
        Files.write(part, expected)
        publishFtpStagedFile(part, target)
        checkCase(Files.readAllBytes(target).contentEquals(expected), "published bytes changed")
        checkCase(!Files.exists(part), "staging file not removed")
    }
    withDir { dir ->
        val part = dir.resolve("existing.bin.ftp-part")
        val target = dir.resolve("existing.bin")
        Files.write(part, byteArrayOf(1, 2, 3))
        Files.write(target, byteArrayOf(9, 8, 7))
        val rejected = try { publishFtpStagedFile(part, target); false }
            catch (_: FileAlreadyExistsException) { true }
        checkCase(rejected, "preexisting destination was not rejected")
        checkCase(Files.readAllBytes(target).contentEquals(byteArrayOf(9, 8, 7)), "destination overwritten")
        checkCase(Files.readAllBytes(part).contentEquals(byteArrayOf(1, 2, 3)), "partial was lost")
    }
    withDir { dir ->
        val part = dir.resolve("missing.bin.ftp-part")
        val target = dir.resolve("missing.bin")
        val rejected = try { publishFtpStagedFile(part, target); false }
            catch (_: java.nio.file.NoSuchFileException) { true }
        checkCase(rejected, "missing partial was published")
        checkCase(!Files.exists(target), "missing partial created output")
    }
    withDir { dir ->
        val part = dir.resolve("link.bin.ftp-part")
        val target = dir.resolve("link.bin")
        val real = dir.resolve("real.bin")
        Files.write(part, byteArrayOf(1))
        Files.write(real, byteArrayOf(5))
        try {
            Files.createSymbolicLink(target, real.fileName)
            val rejected = try { publishFtpStagedFile(part, target); false }
                catch (_: FileAlreadyExistsException) { true }
            checkCase(rejected, "existing symbolic link was overwritten")
            checkCase(Files.readAllBytes(real).contentEquals(byteArrayOf(5)), "symlink target changed")
        } catch (_: UnsupportedOperationException) {
            // Symbolic links may be disabled on Windows; tested separately there.
        } catch (_: java.nio.file.FileSystemException) {
            // Windows policy may deny symlink creation.
        }
    }
    println("PASS $checks/$checks HyperGet FTP staged publish source checks")
}
