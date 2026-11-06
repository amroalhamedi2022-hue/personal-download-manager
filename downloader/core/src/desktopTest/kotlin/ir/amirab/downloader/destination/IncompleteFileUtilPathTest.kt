package ir.amirab.downloader.destination

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncompleteFileUtilPathTest {
    @Test
    fun relativeDownloadWithoutParentGetsUniqueIncompleteName() {
        val original = File("relative-hyperget-file.bin")
        val first = IncompleteFileUtil.addIncompleteIndicator(original, 17L)
        val second = IncompleteFileUtil.addIncompleteIndicator(original, 18L)
        assertEquals(File("relative-hyperget-file.bin.dl-17.abdm.part"), first)
        assertEquals(File("relative-hyperget-file.bin.dl-18.abdm.part"), second)
        assertFalse(first.isAbsolute)
        assertFalse(second.isAbsolute)
    }

    @Test
    fun relativeNestedDirectoryRemainsTheParent() {
        val original = File("relative-downloads/file.txt")
        assertEquals(
            File("relative-downloads/file.txt.dl-23.abdm.part"),
            IncompleteFileUtil.addIncompleteIndicator(original, 23L),
        )
    }

    @Test
    fun incompleteNamingIsIdempotentForSameDownloadId() {
        val original = File("relative-hyperget-archive.zip")
        val once = IncompleteFileUtil.addIncompleteIndicator(original, 999L)
        val twice = IncompleteFileUtil.addIncompleteIndicator(once, 999L)
        assertEquals(once, twice)
        assertTrue(twice.name.endsWith(".dl-999.abdm.part"))
    }
}
