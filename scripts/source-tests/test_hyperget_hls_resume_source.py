"""Source-only persisted HLS completion safety contracts; no application build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader"


class HlsPersistedResumeSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = (CORE / "downloaditem/hls/HLSDownloadJob.kt").read_text(encoding="utf-8")
        cls.destination = (CORE / "destination/SegmentedDownloadDestination.kt").read_text(encoding="utf-8")
        cls.helper = (CORE / "destination/StagedSegmentValidity.kt").read_text(encoding="utf-8")
        cls.smoke = (ROOT / "scripts/source-tests/HyperGetHlsStagedSegmentSmoke.kt").read_text(encoding="utf-8")

    def test_validation_precedes_completed_fast_path(self):
        resume = self.job.split("private suspend fun resumeWithNewScope(", 1)[1].split("override fun getDownloadedSize", 1)[0]
        self.assertLess(resume.index("fetchDownloadInfoAndValidate()"), resume.index("parts.all { it.isCompleted }"))

    def test_missing_or_invalid_completed_part_is_redownloaded(self):
        self.assertIn("if (!destination.isDownloadPartValid(it))", self.job)
        self.assertIn("it.isCompleted = false", self.job)
        self.assertIn("it.resetCurrent()", self.job)
        self.assertIn("it.statusFlow.update { PartDownloadStatus.IDLE }", self.job)

    def test_stage_check_rejects_symlinks_and_directories(self):
        self.assertIn("LinkOption.NOFOLLOW_LINKS", self.destination)
        self.assertIn("Files.readAttributes(path, BasicFileAttributes::class.java", self.destination)
        self.assertIn("isRegularFile = attributes.isRegularFile", self.destination)

    def test_stage_check_uses_actual_size_and_expected_hls_length(self):
        self.assertIn("actualSize = attributes.size()", self.destination)
        self.assertIn("expectedSize = (part as? MediaSegment)?.length", self.destination)
        self.assertIn("isStagedSegmentValid(", self.destination)

    def test_stage_check_rejects_zero_and_truncated_data(self):
        self.assertIn("!isRegularFile || actualSize <= 0L", self.helper)
        self.assertIn("actualSize == expectedSize", self.helper)
        self.assertIn("Triple(true, 1023L, 1024L) to false", self.smoke)
        self.assertIn("Triple(true, 0L, 0L) to false", self.smoke)

    def test_unknown_length_remains_compatible(self):
        self.assertIn("expectedSize == null || expectedSize <= 0L", self.helper)
        self.assertIn("Triple(true, 123L, null) to true", self.smoke)


if __name__ == "__main__":
    unittest.main()
