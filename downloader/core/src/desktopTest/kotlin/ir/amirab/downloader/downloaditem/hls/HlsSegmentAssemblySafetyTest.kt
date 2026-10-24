package ir.amirab.downloader.downloaditem.hls

import ir.amirab.downloader.destination.SegmentedDownloadDestination
import ir.amirab.downloader.part.MediaSegment
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Final HLS publication must not expose a partial output or trust stale stage flags. */
class HlsSegmentAssemblySafetyTest {
    private fun segment(id: Long, bytes: ByteArray) = MediaSegment(
        segmentIndex = id, link = "https://example.test/segment", duration = 2.0,
        isCompleted = true, length = bytes.size.toLong(), current = bytes.size.toLong()
    )

    @Test fun completedSegmentsPublishOnlyExactBytes() {
        val root = Files.createTempDirectory("hyperget-assembly-ok-").toFile()
        try {
            val one = byteArrayOf(1, 2, 3, 4)
            val two = byteArrayOf(5, 6, 7)
            val parts = listOf(segment(1, one), segment(2, two))
            val stage = root.resolve("stage").apply { mkdirs() }
            stage.resolve("1").writeBytes(one)
            stage.resolve("2").writeBytes(two)
            val output = root.resolve("finished.ts")
            val dest = SegmentedDownloadDestination(
                tempDirectory = stage, outputFile = output,
                getAllParts = { parts }, getFileName = { it.getID().toString() },
                appendMode = false
            )
            dest.onAllPartsCompleted { }
            val actual = output.readBytes()
            assertContentEquals(one + two, actual)
            assertContentEquals(
                MessageDigest.getInstance("SHA-256").digest(one + two),
                MessageDigest.getInstance("SHA-256").digest(actual)
            )
            assertFalse(stage.exists())
            assertTrue(root.listFiles()?.none { it.name.endsWith(".assembling") } == true)
        } finally { root.deleteRecursively() }
    }

    @Test fun missingCompletedStageNeverPublishesTruncatedOutput() {
        val root = Files.createTempDirectory("hyperget-assembly-missing-").toFile()
        try {
            val part = segment(9, byteArrayOf(1, 2, 3))
            val stage = root.resolve("stage").apply { mkdirs() }
            val output = root.resolve("finished.ts")
            val dest = SegmentedDownloadDestination(
                tempDirectory = stage, outputFile = output,
                getAllParts = { listOf(part) }, getFileName = { it.getID().toString() },
                appendMode = false
            )
            assertFailsWith<IOException> { dest.onAllPartsCompleted { } }
            assertFalse(output.exists())
            assertTrue(stage.exists(), "failed finalization must retain staged data")
        } finally { root.deleteRecursively() }
    }

    @Test fun existingOutputIsNeverOverwrittenByAssemblyRetry() {
        val root = Files.createTempDirectory("hyperget-assembly-existing-").toFile()
        try {
            val data = byteArrayOf(7, 8, 9)
            val part = segment(4, data)
            val stage = root.resolve("stage").apply { mkdirs() }
            stage.resolve("4").writeBytes(data)
            val output = root.resolve("finished.ts")
            val original = byteArrayOf(99, 98)
            output.writeBytes(original)
            val dest = SegmentedDownloadDestination(
                tempDirectory = stage, outputFile = output,
                getAllParts = { listOf(part) }, getFileName = { it.getID().toString() },
                appendMode = false
            )
            assertFailsWith<Exception> { dest.onAllPartsCompleted { } }
            assertContentEquals(original, output.readBytes())
            assertTrue(stage.exists())
            assertTrue(root.listFiles()?.none { it.name.endsWith(".assembling") } == true)
        } finally { root.deleteRecursively() }
    }
}
