"""Source contract for shared-IO HTTP part streaming and cancellation cleanup.

This structural check is deliberately paired with Windows JUnit and installed 1/4/16
benchmarks: source text alone cannot prove socket unblocking, thread reuse, or byte integrity.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PART_PATH = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt"
TEST_PATH = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/PartDownloaderIoLifecycleTest.kt"
RETRY_TEST_PATH = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/ActualRestoredRetryJobTest.kt"
ACTIVE_WORKFLOW_PATH = ROOT / ".github/workflows/windows-installed-active-benchmark.yml"
MEMORY_WORKFLOW_PATH = ROOT / ".github/workflows/windows-installed-memory-profile.yml"


class PartDownloaderIoLifecycleContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.part = PART_PATH.read_text(encoding="utf-8")
        cls.test = TEST_PATH.read_text(encoding="utf-8")
        cls.retry_test = RETRY_TEST_PATH.read_text(encoding="utf-8")
        cls.active_workflow = ACTIVE_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.memory_workflow = MEMORY_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.part_path = "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt"

    def test_stop_requested_before_worker_dispatch_cannot_start_late_request(self):
        self.assertIn(
            "while (shouldContinuePartWorker(coroutineContext.isActive, stop))",
            self.part,
        )
        self.assertIn("coroutineActive && !stopRequested", self.part)
        self.assertNotIn("while (coroutineContext.isActive || !stop)", self.part)
        self.assertIn("stopRequestedBeforeCoroutineDispatchPreventsAnyDownloadAttempt", self.test)
        self.assertIn("stopAllWaitsForActualHttpResumingThenLeavesNoLateWorker", self.retry_test)
        self.assertIn(
            "Stop All must not allow a part worker to issue a late request",
            self.retry_test,
        )

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

    def test_shared_body_copy_gate_is_global_and_released_in_finally(self):
        self.assertIn("internal const val PART_BODY_COPY_PARALLELISM = 60", self.part)
        self.assertIn("private val partBodyCopyPermits = Semaphore(PART_BODY_COPY_PARALLELISM)", self.part)
        helper = self.part.split("internal suspend fun <T> withPartBodyCopyPermit", 1)[1].split("abstract class PartDownloader", 1)[0]
        self.assertIn("partBodyCopyPermits.acquire()", helper)
        self.assertIn("finally", helper)
        self.assertIn("partBodyCopyPermits.release()", helper)
        self.assertIn("sharedBodyCopyPermitNeverExceedsConfiguredParallelism", self.test)
        self.assertIn("val taskCount = PART_BODY_COPY_PARALLELISM * 2", self.test)
        self.assertIn("val overflowEntered = CompletableDeferred<Unit>()", self.test)
        self.assertIn("withTimeoutOrNull(250L)", self.test)
        self.assertIn("the 61st body copy must remain suspended", self.test)
        self.assertIn("bodyCopyPermitIsReleasedAfterCopyFailure", self.test)

    def test_permit_is_acquired_before_network_connection_and_held_through_copy(self):
        download = self.part.split("private suspend fun download()", 1)[1].split("protected open fun onCanceled", 1)[0]
        self.assertRegex(
            download,
            r"withFairPartBodyCopyPermit\(part\.getID\(\) == 0L\)\s*\{\s*downloadWithReservedConnectionSlot\(\)",
        )
        self.assertLess(
            download.index("withFairPartBodyCopyPermit(part.getID() == 0L) {"),
            download.index("val conn = awaitPartResponseWithDeadline"),
        )
        self.assertRegex(
            download,
            r"conn\.use\s*\{\s*connection\s*->\s*withContext\(Dispatchers\.IO\)",
        )
        self.assertIn("copyDataSync(connection.source, writer)", download)
        self.assertIn("connectionAdmissionWaitsForSharedPermit", self.test)
        self.assertIn("a waiting part must not open an HTTP socket", self.test)

    def test_first_ranges_keep_sixteen_reserved_connection_slots(self):
        self.assertIn('internal const val PART_PRIMARY_COPY_RESERVE = 16', self.part)
        self.assertIn('Semaphore(PART_BODY_COPY_PARALLELISM - PART_PRIMARY_COPY_RESERVE)', self.part)
        self.assertIn('withFairPartBodyCopyPermit(part.getID() == 0L)', self.part)
        self.assertIn('primaryRangesCannotBeStarvedByEarlierMultiPartDownloads', self.test)
        self.assertIn('all 16 first ranges must start', self.test)

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
