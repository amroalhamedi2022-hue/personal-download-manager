package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private var passed = 0
private fun verify(value: Boolean, message: String) { check(value) { message }; passed++ }
private inline fun failsIo(block: () -> Unit): Boolean = try { block(); false } catch (_: IOException) { true }
private fun withDir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("hyperget-ftp-resume-identity-")
    try { block(dir) } finally { dir.toFile().deleteRecursively() }
}

fun main() {
    withDir { dir ->
        val sidecar = dir.resolve("file.ftp-part.properties")
        verify(loadFtpResumeIdentity(sidecar) == null, "missing identity is not a resume")
        Files.writeString(sidecar, "endpoint=ftp\\://example.org\\:21/file\nsize=10\nmodificationTime=20261024000000\n")
        val parsed = loadFtpResumeIdentity(sidecar)
        verify(parsed?.getProperty("size") == "10", "valid size")
        verify(parsed?.getProperty("endpoint") == "ftp://example.org:21/file", "valid endpoint")
        verify(parsed?.getProperty("modificationTime") == "20261024000000", "valid timestamp")
    }
    withDir { dir ->
        val secret = dir.resolve("unrelated.txt")
        Files.writeString(secret, "endpoint=secret\n")
        val link = dir.resolve("sidecar.properties")
        Files.createSymbolicLink(link, secret.fileName)
        verify(failsIo { loadFtpResumeIdentity(link) }, "symlink must reject")
        verify(Files.readString(secret) == "endpoint=secret\n", "symlink target unchanged")
    }
    withDir { dir ->
        val link = dir.resolve("dangling.properties")
        Files.createSymbolicLink(link, dir.resolve("missing").fileName)
        verify(failsIo { loadFtpResumeIdentity(link) }, "dangling symlink must reject")
        verify(failsIo { loadFtpResumeIdentity(dir) }, "directory must reject")
    }
    withDir { dir ->
        val file = dir.resolve("huge.properties")
        Files.write(file, ByteArray(16 * 1024 + 1) { 65 })
        verify(failsIo { loadFtpResumeIdentity(file) }, "oversized identity must reject")
        Files.write(file, ByteArray(16 * 1024) { 35 })
        verify(loadFtpResumeIdentity(file) != null, "bounded identity accepted")
    }
    withDir { dir ->
        val file = dir.resolve("bad.properties")
        Files.writeString(file, "size=\\uZZZZ\n")
        verify(failsIo { loadFtpResumeIdentity(file) }, "malformed unicode escape must reject")
    }
    println("PASS $passed/$passed HyperGet FTP resume identity checks")
}
