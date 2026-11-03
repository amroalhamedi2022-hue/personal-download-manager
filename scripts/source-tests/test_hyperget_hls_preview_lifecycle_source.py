"""Source-only HLS preview-vs-active-worker lifecycle contracts.

These tests inspect production Kotlin source; they do not compile the app or
claim to prove runtime concurrency safety. Full Windows HLS race tests remain
a separate release gate.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JOB = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt"


def section(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class HlsPreviewLifecycleSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = JOB.read_text(encoding="utf-8")
        cls.remote = section(cls.source, "private suspend fun initialParts()", "suspend fun cancelDownloadScope()")
        cls.preview = section(cls.source, "override suspend fun extraConfigsReceived(", "\n}")

    def test_shared_playlist_mutation_mutex_exists(self):
        self.assertIn("private val playlistMutationLock = Mutex()", self.source)

    def test_remote_refresh_uses_lock_and_finally_unlock(self):
        self.assertLess(self.remote.index("playlistMutationLock.lock()"), self.remote.index("stopAllParts()"))
        self.assertLess(self.remote.index("updateParts(response.hlsManifest)"), self.remote.index("playlistMutationLock.unlock()"))
        self.assertIn("finally {", self.remote)

    def test_preview_replacement_uses_same_lock_and_finally_unlock(self):
        self.assertLess(self.preview.index("playlistMutationLock.lock()"), self.preview.index("updateParts(preview)"))
        self.assertLess(self.preview.index("updateParts(preview)"), self.preview.index("playlistMutationLock.unlock()"))
        self.assertIn("finally {", self.preview)

    def test_active_job_and_scope_both_block_preview_replacement(self):
        self.assertIn("!isDownloadActive.value && activeDownloadScope?.isActive != true", self.preview)
        self.assertLess(self.preview.index("if (!isDownloadActive.value"), self.preview.index("updateParts(preview)"))

    def test_preview_stops_old_workers_before_replacing_segments(self):
        self.assertLess(self.preview.index("stopAllParts()"), self.preview.index("clearPartDownloaderList()"))
        self.assertLess(self.preview.index("clearPartDownloaderList()"), self.preview.index("updateParts(preview)"))

    def test_preview_is_saved_only_after_safe_reconciliation(self):
        self.assertLess(self.preview.index("updateParts(preview)"), self.preview.index("saveParts()"))
        self.assertIn("config.hlsManifest?.let { preview ->", self.preview)


if __name__ == "__main__":
    unittest.main()
