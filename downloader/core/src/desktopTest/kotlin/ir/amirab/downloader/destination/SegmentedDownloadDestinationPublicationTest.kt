package ir.amirab.downloader.destination

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentedDownloadDestinationPublicationTest {
    private fun destination(folder: File): SegmentedDownloadDestination =
        SegmentedDownloadDestination(
            outputFile = folder.resolve("movie.ts"),
            tempDirectory = folder.resolve("segments"),
            getFileName = { it.getID().toString() },
            getAllParts = { emptyList() },
            appendMode = false,
        )

    @Test fun identicalBytesAssembleWithoutDeletingOriginalSegments() {
        val dir = Files.createTempDirectory("hyperget-hls-publish-").toFile()
        try {
            val segments = dir.resolve("segments").apply { mkdirs() }
            val a = segments.resolve("a.ts").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val b = segments.resolve("b.ts").apply { writeBytes(byteArrayOf(4, 5)) }
            val progress = mutableListOf<Int>()
            destination(dir).assemble(listOf(a, b), dir.resolve("movie.ts"), progress::add)
            assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), dir.resolve("movie.ts").readBytes())
            assertTrue(a.isFile && b.isFile)
            assertEquals(100, progress.last())
            assertEquals(1, dir.listFiles().orEmpty().count { it.name == "movie.ts" })
            assertFalse(dir.listFiles().orEmpty().any { it.name.startsWith(".hyperget-assemble-") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun neverOverwriteAFileCreatedAfterDownloadWasQueued() {
        val dir = Files.createTempDirectory("hyperget-hls-conflict-").toFile()
        try {
            val a = dir.resolve("part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val existing = dir.resolve("movie.ts").apply { writeText("keep original user file") }
            assertFailsWith<Exception> { destination(dir).assemble(listOf(a), existing) {} }
            assertEquals("keep original user file", existing.readText())
            assertContentEquals(byteArrayOf(1, 2, 3), a.readBytes())
            assertFalse(dir.listFiles().orEmpty().any { it.name.startsWith(".hyperget-assemble-") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun missingSegmentNeverTruncatesExistingOrCreatesFinal() {
        val dir = Files.createTempDirectory("hyperget-hls-missing-").toFile()
        try {
            val target = dir.resolve("movie.ts")
            assertFailsWith<Exception> {
                destination(dir).assemble(listOf(dir.resolve("missing.ts")), target) {}
            }
            assertFalse(target.exists())
            assertFalse(dir.listFiles().orEmpty().any { it.name.startsWith(".hyperget-assemble-") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun emptyPlaylistCannotPublishAnEmptyFinalFile() {
        val dir = Files.createTempDirectory("hyperget-hls-empty-").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { destination(dir).assemble(emptyList(), dir.resolve("movie.ts")) {} }
            assertFalse(dir.resolve("movie.ts").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
