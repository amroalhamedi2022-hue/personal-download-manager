package ir.amirab.downloader.destination

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimpleDownloadDestinationPublicationTest {
    @Test
    fun failedPublicationNeverDeletesThePreviousCompletedFile() {
        val directory = Files.createTempDirectory("hyperget-publication-fail")
        try {
            val previous = directory.resolve("download.bin")
            Files.writeString(previous, "previous-verified-bytes")
            // Simulate a staged file removed immediately before the final move.
            assertFailsWith<IOException> {
                publishCompletedStagedFile(directory.resolve("missing.part").toFile(), previous.toFile())
            }
            assertTrue(Files.isRegularFile(previous))
            assertEquals("previous-verified-bytes", Files.readString(previous))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun replacesAnExistingCompletedFileOnlyAfterStagedFileIsAvailable() {
        val directory = Files.createTempDirectory("hyperget-publication-replace")
        try {
            val previous = directory.resolve("download.bin")
            val staged = directory.resolve("download.part")
            Files.writeString(previous, "previous")
            Files.writeString(staged, "complete-new-content")
            publishCompletedStagedFile(staged.toFile(), previous.toFile())
            assertEquals("complete-new-content", Files.readString(previous))
            assertFalse(Files.exists(staged), "The staged file should have moved")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun publishesAFirstDownloadWithoutAnExistingTarget() {
        val directory = Files.createTempDirectory("hyperget-publication-new")
        try {
            val completed = directory.resolve("new.bin")
            val staged = directory.resolve("new.part")
            Files.writeString(staged, "new-complete-content")
            publishCompletedStagedFile(staged.toFile(), completed.toFile())
            assertEquals("new-complete-content", Files.readString(completed))
            assertFalse(Files.exists(staged))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
