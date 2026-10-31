"""Source-only contracts for HLS restart playlist and worker lifecycle.

These tests intentionally read Kotlin source; they do NOT compile the app.
The playlist reconciliation's isolated Kotlin behavior is tested separately.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JOB = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt"


def body_between(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class HlsRefreshLifecycleSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = JOB.read_text(encoding="utf-8")

    def test_refresh_is_unconditional_even_with_saved_parts(self):
        body = body_between(self.job, "private suspend fun fetchDownloadInfoAndValidate(", "// Refresh the segment list")
        self.assertIn("initialParts()", body)
        self.assertNotIn("if (parts.isEmpty())", body)
        self.assertLess(body.index("initialParts()"), body.index("parts\n            .filter { it.isCompleted }"))

    def test_remote_refresh_precedes_fast_completion(self):
        body = body_between(self.job, "private suspend fun resumeWithNewScope(", "override fun getDownloadedSize")
        self.assertLess(body.index("fetchDownloadInfoAndValidate()"), body.index("parts.all { it.isCompleted }"))

    def test_workers_stop_before_replacing_segment_objects(self):
        body = body_between(self.job, "private suspend fun initialParts()", "suspend fun cancelDownloadScope()")
        self.assertLess(body.index("stopAllParts()"), body.index("clearPartDownloaderList()"))
        self.assertLess(body.index("clearPartDownloaderList()"), body.index("updateParts("))
        self.assertIn("HLSResponseInfo.fromConnection(it)", body)

    def test_all_cached_workers_and_listeners_are_retired(self):
        body = body_between(self.job, "private fun clearPartDownloaderList()", "private val speedLimiter")
        self.assertIn("listenerJobs.values.forEach { it.cancel() }", body)
        self.assertIn("listenerJobs.clear()", body)
        self.assertIn("partDownloaderList.clear()", body)
        self.assertNotIn("parts.forEach", body)

    def test_refreshed_segments_are_reconciled_by_identity(self):
        body = body_between(self.job, "fun updateParts(playlist: MediaPlaylist)", "private suspend fun initialParts()")
        self.assertIn("reconcileHlsSegments(previous, incoming)", body)
        self.assertIn("setParts(reconciled.map", body)

    def test_corrupt_staged_completion_is_reset_after_refresh(self):
        body = body_between(self.job, "private suspend fun fetchDownloadInfoAndValidate(", "// Refresh the segment list")
        self.assertIn("if (!destination.isDownloadPartValid(it))", body)
        self.assertIn("it.isCompleted = false", body)
        self.assertIn("it.resetCurrent()", body)
        self.assertLess(body.index("initialParts()"), body.index("saveState()"))


if __name__ == "__main__":
    unittest.main()
