package ir.amirab.downloader.ftp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Source-level regression; full Windows FTP acceptance remains deferred. */
class FtpResumeIdentityTest {
    private inline fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("hyperget-ftp-sidecar-junit-")
        try { block(dir) } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun missingSidecarIsNotAResume() = withDir { dir ->
        assertNull(loadFtpResumeIdentity(dir.resolve("missing.properties")))
    }

    @Test
    fun validSidecarPreservesResumeIdentity() = withDir { dir ->
        val path = dir.resolve("file.ftp-part.properties")
        Files.writeString(path, "endpoint=ftp\\://example.org\\:21/file\nsize=42\nmodificationTime=20261024000000\n")
        val identity = loadFtpResumeIdentity(path)
        assertEquals("ftp://example.org:21/file", identity?.getProperty("endpoint"))
        assertEquals("42", identity?.getProperty("size"))
        assertEquals("20261024000000", identity?.getProperty("modificationTime"))
    }

    @Test
    fun symlinkSidecarDoesNotReadUnrelatedFile() = withDir { dir ->
        val unrelated = dir.resolve("unrelated.properties")
        Files.writeString(unrelated, "endpoint=unrelated\n")
        val link = dir.resolve("file.ftp-part.properties")
        Files.createSymbolicLink(link, unrelated.fileName)
        assertFailsWith<IOException> { loadFtpResumeIdentity(link) }
        assertEquals("endpoint=unrelated\n", Files.readString(unrelated))
    }

    @Test
    fun danglingLinkAndDirectoryAreRejected() = withDir { dir ->
        val link = dir.resolve("dangling.properties")
        Files.createSymbolicLink(link, dir.resolve("missing").fileName)
        assertFailsWith<IOException> { loadFtpResumeIdentity(link) }
        assertFailsWith<IOException> { loadFtpResumeIdentity(dir) }
    }

    @Test
    fun oversizedAndMalformedSidecarsAreRejected() = withDir { dir ->
        val path = dir.resolve("bad.properties")
        Files.write(path, ByteArray(16 * 1024 + 1) { 65 })
        assertFailsWith<IOException> { loadFtpResumeIdentity(path) }
        Files.writeString(path, "size=\\uZZZZ\n")
        assertFailsWith<IOException> { loadFtpResumeIdentity(path) }
    }
}
