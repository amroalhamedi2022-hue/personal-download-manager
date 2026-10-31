"""Regression contracts for HTTP part scheduling under 16-job admission.

Source-only guard; Windows Kotlin compile and installed 1/4/16 remain separate gates.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
JOB = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt"


class HttpPartSchedulingContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = JOB.read_text(encoding="utf-8")
        cls.start = cls.text.index("private suspend fun beginDownloadParts()")
        cls.end = cls.text.index("private fun onPartHaveToManyError", cls.start)
        cls.method = cls.text[cls.start:cls.end]

    def test_busy_mutex_waits_instead_of_silently_dropping_admission(self):
        self.assertIn("partLoopLock.withLock {", self.method)
        self.assertNotIn("partLoopLock.tryLock()", self.method)
        self.assertNotIn("partLoopLock.isLocked", self.method)

    def test_first_part_admission_precedes_downloading_status(self):
        start = self.text.index("private suspend fun resumeWithNewScope(")
        end = self.text.index("private suspend fun prepareDestination(", start)
        body = self.text[start:end]
        self.assertLess(body.index("beginDownloadParts()"), body.index("onDownloadResumed()"))
        self.assertNotIn("activeDownloadScope?.launch {\n            if (!partLoopLock.tryLock())", self.method)

    def test_completed_part_refill_is_dispatched_from_coroutine(self):
        self.assertRegex(
            self.text,
            r"PartDownloadStatus\.Completed\s*->\s*\{[\s\S]*?scope\.launch\s*\{\s*beginDownloadParts\(\)"
        )

    def test_preference_change_remains_async_and_cancellable(self):
        self.assertRegex(
            self.text,
            r"fun onPreferredConnectionCountChanged\(\)\s*\{\s*activeDownloadScope\?\.launch\s*\{\s*beginDownloadParts\(\)"
        )


if __name__ == "__main__":
    unittest.main()
