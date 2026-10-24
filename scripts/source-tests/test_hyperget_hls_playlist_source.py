"""Source-only HLS playlist regression contracts. No application build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HLS = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls"


class HlsPlaylistSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = (HLS / "HLSDownloadJob.kt").read_text(encoding="utf-8")
        cls.helper = (HLS / "HlsPlaylistReconciliation.kt").read_text(encoding="utf-8")
        cls.smoke = (ROOT / "scripts/source-tests/HyperGetHlsReconciliationSmoke.kt").read_text(encoding="utf-8")

    def test_playlist_is_committed_not_discarded(self):
        self.assertIn("setParts(reconciled.map", self.job)
        self.assertNotIn("val newPartsToSave", self.job)

    def test_job_calls_reconciliation(self):
        self.assertIn("reconcileHlsSegments(previous, incoming)", self.job)

    def test_sequence_number_is_part_of_identity(self):
        self.assertIn("previous.associateBy { it.sequence }", self.helper)

    def test_uri_is_checked_before_reusing_completed_state(self):
        self.assertIn("old.uri == fresh.uri", self.helper)

    def test_duration_is_checked_before_reusing_completed_state(self):
        self.assertIn("old.duration == fresh.duration", self.helper)

    def test_completed_length_and_progress_are_preserved_together(self):
        self.assertIn("fresh.copy(completed = old.completed, length = old.length, current = old.current)", self.helper)
        for field in ("isCompleted = segment.completed", "length = segment.length", "current = segment.current"):
            self.assertIn(field, self.job)

    def test_refreshed_order_is_preserved(self):
        self.assertIn("return incoming.map { fresh ->", self.helper)
        self.assertIn("incoming order must be adopted", self.smoke)

    def test_changed_signed_url_regression_is_present(self):
        self.assertIn("changed signed URI must reset completion", self.smoke)

    def test_changed_duration_regression_is_present(self):
        self.assertIn("changed duration must reset completion", self.smoke)

    def test_empty_playlist_regression_is_present(self):
        self.assertIn("empty playlist removes obsolete segments", self.smoke)


if __name__ == "__main__":
    unittest.main()
