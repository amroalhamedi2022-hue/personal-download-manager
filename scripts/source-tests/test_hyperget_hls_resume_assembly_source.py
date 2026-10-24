"""Source contract for persisted HLS resume and safe final publication.

This is structural; the targeted Windows Kotlin/HTTP test is the behavioral gate.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/destination/SegmentedDownloadDestination.kt"
TEST_DIR = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls"


class HlsResumeAssemblyContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dest = DEST.read_text(encoding="utf-8")
        cls.resume = (TEST_DIR / "HlsDownloadJobResumeLoopbackTest.kt").read_text(encoding="utf-8")
        cls.assembly = (TEST_DIR / "HlsSegmentAssemblySafetyTest.kt").read_text(encoding="utf-8")

    def test_finalization_revalidates_every_stage(self):
        self.assertIn("completedParts.any { !it.isCompleted || !isDownloadPartValid(it) }", self.dest)

    def test_output_is_staged_before_publish(self):
        self.assertIn("Files.createTempFile(", self.dest)
        self.assertIn("Files.move(temporary, destination.toPath())", self.dest)
        self.assertLess(self.dest.index("Files.newOutputStream(temporary)"), self.dest.index("Files.move(temporary"))

    def test_failed_assembly_cleans_temporary_file(self):
        self.assertIn("Files.deleteIfExists(temporary)", self.dest)
        self.assertIn("copied != snapshots[index]", self.dest)

    def test_job_fixture_boots_persisted_completed_segments(self):
        for marker in ("MemoryDownloadPartStatesDB()", "partsDb.setParts(88", "manager.boot()", "manager.resume(88)", "DownloadStatus.Completed"):
            self.assertIn(marker, self.resume)

    def test_real_job_checks_relocated_output_hash(self):
        for marker in ('server.createContext("/entry.m3u8")', 'server.createContext("/new/one.ts")', 'server.createContext("/new/two.ts")', 'MessageDigest.getInstance("SHA-256")', 'assertContentEquals(first + second, actual)'):
            self.assertIn(marker, self.resume)

    def test_assembly_negative_regressions(self):
        for marker in ("missingCompletedStageNeverPublishesTruncatedOutput", "existingOutputIsNeverOverwrittenByAssemblyRetry", "completedSegmentsPublishOnlyExactBytes"):
            self.assertIn(marker, self.assembly)


if __name__ == "__main__":
    unittest.main()
