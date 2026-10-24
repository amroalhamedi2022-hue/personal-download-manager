package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private var passed = 0
private fun checkCase(condition: Boolean, description: String) {
    check(condition) { description }
    passed++
}
private fun withDir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("hyperget-ftp-stage-guard-")
    try { block(dir) } finally { dir.toFile().deleteRecursively() }
}
private inline fun failsIo(block: () -> Unit): Boolean = try {
    block()
    false
} catch (_: IOException) {
    true
}

fun main() {
    withDir { dir ->
        val part = dir.resolve("new.ftp-part")
        checkCase(ftpStagedBytes(part) == 0L, "absent stage must start at zero")
        openFtpStagedOutput(part, 0).use { it.write(byteArrayOf(1, 2, 3)) }
        checkCase(Files.readAllBytes(part).contentEquals(byteArrayOf(1, 2, 3)), "new stage bytes wrong")
        checkCase(ftpStagedBytes(part) == 3L, "stage size incorrect")
        openFtpStagedOutput(part, 3).use { it.write(byteArrayOf(4, 5)) }
        checkCase(Files.readAllBytes(part).contentEquals(byteArrayOf(1, 2, 3, 4, 5)), "resume must append")
        checkCase(failsIo { openFtpStagedOutput(part, 3).use { } }, "changed size must reject resume")
        checkCase(Files.size(part) == 5L, "size mismatch must preserve stage")
    }
    withDir { dir ->
        val target = dir.resolve("user-data.txt")
        Files.writeString(target, "USER-DATA")
        val stage = dir.resolve("download.ftp-part")
        Files.createSymbolicLink(stage, target.fileName)
        checkCase(failsIo { ftpStagedBytes(stage) }, "symlink stage must be rejected during size check")
        checkCase(failsIo { openFtpStagedOutput(stage, 9).use { it.write(42) } }, "symlink stage must not open")
        checkCase(Files.readString(target) == "USER-DATA", "symlink target was modified")
    }
    withDir { dir ->
        val stage = dir.resolve("dangling.ftp-part")
        Files.createSymbolicLink(stage, dir.resolve("nonexistent.bin").fileName)
        checkCase(failsIo { ftpStagedBytes(stage) }, "dangling symlink must be rejected")
        checkCase(failsIo { openFtpStagedOutput(stage, 0).use { } }, "dangling symlink must not be created through")
    }
    withDir { dir ->
        val stage = dir.resolve("directory.ftp-part")
        Files.createDirectory(stage)
        checkCase(failsIo { ftpStagedBytes(stage) }, "directory stage must be rejected")
        checkCase(failsIo { openFtpStagedOutput(stage, 0).use { } }, "directory stage must not open")
    }
    println("PASS $passed/$passed HyperGet FTP staged output source checks")
}
