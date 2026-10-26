"""Source contract for shared-IO HTTP part streaming and cancellation cleanup.

This structural check is deliberately paired with Windows JUnit and installed 1/4/16
benchmarks: source text alone cannot prove socket unblocking, thread reuse, or byte integrity.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PART_PATH = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt"
TEST_PATH = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/PartDownloaderIoLifecycleTest.kt"
ACTIVE_WORKFLOW_PATH = ROOT / ".github/workflows/windows-installed-active-benchmark.yml"
MEMORY_WORKFLOW_PATH = ROOT / ".github/workflows/windows-installed-memory-profile.yml"


class PartDownloaderIoLifecycleContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.part = PART_PATH.read_text(encoding="utf-8")
        cls.test = TEST_PATH.read_text(encoding="utf-8")
        cls.active_workflow = ACTIVE_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.memory_workflow = MEMORY_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.part_path = "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt"

    def test_raw_thread_per_part_is_removed(self):
        self.assertNotIn("kotlin.concurrent.thread", self.part)
        self.assertNotIn("private var thread: Thread?", self.part)
        self.assertIn("withContext(Dispatchers.IO)", self.part)
        self.assertNotIn("Thread.currentThread().isInterrupted", self.part)

    def test_stop_closes_active_connection_before_cancelling_scope(self):
        stop_body = self.part.split("fun stop()", 1)[1].split("suspend fun join()", 1)[0]
        self.assertIn("activeConnection", stop_body)
        self.assertIn("connectionToClose?.close()", stop_body)
        self.assertLess(stop_body.index("connectionToClose?.close()"), stop_body.index("scope?.coroutineContext?.job?.cancel()"))

    def test_connection_registration_handles_stop_race(self):
        download_body = self.part.split("private suspend fun download()", 1)[1].split("protected open fun onCanceled", 1)[0]
        self.assertIn("if (stop)", download_body)
        self.assertIn("activeConnection = conn", download_body)
        self.assertIn("if (!registered)", download_body)
        self.assertIn("conn.close()", download_body)
        self.assertIn("if (activeConnection === conn)", download_body)

    def test_runtime_regression_uses_source_that_ignores_thread_interrupts(self):
        self.assertIn("class PartDownloaderIoLifecycleTest", self.test)
        self.assertIn("while (!closed)", self.test)
        self.assertIn("catch (_: InterruptedException)", self.test)
        self.assertIn("stopClosesBlockingSourceAndJoinsWorker", self.test)
        self.assertIn("withTimeout(2_000L)", self.test)
        self.assertIn("assertTrue(source.closed", self.test)

    def test_installed_benchmark_still_triggers_for_part_downloader_changes(self):
        self.assertIn(f'"{self.part_path}"', self.active_workflow)
        self.assertIn(self.part_path, self.active_workflow.split("case \"$file\" in", 1)[1].split("esac", 1)[0])

    def test_memory_profile_triggers_for_part_downloader_changes(self):
        self.assertIn(f'"{self.part_path}"', self.memory_workflow)
        self.assertIn(self.part_path, self.memory_workflow.split("case \"$file\" in", 1)[1].split("esac", 1)[0])


if __name__ == "__main__":
    unittest.main()
