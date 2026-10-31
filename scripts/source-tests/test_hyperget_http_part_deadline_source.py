"""Part connection response deadline must be retryable without hiding cancellation."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
PART = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt"
TEST = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/PartResponseDeadlineTest.kt"

class PartDeadlineContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = PART.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_deadline_wraps_only_connect_not_streaming(self):
        self.assertIn("awaitPartResponseWithDeadline(PART_RESPONSE_DEADLINE_MILLIS)", self.source)
        self.assertIn("connectAndVerify()", self.source)
        self.assertLess(self.source.index("awaitPartResponseWithDeadline(PART_RESPONSE_DEADLINE_MILLIS)"), self.source.index("withContext(Dispatchers.IO)"))
        self.assertIn("private const val PART_RESPONSE_DEADLINE_MILLIS = 45_000L", self.source)

    def test_caller_cancellation_not_reclassified_as_io(self):
        self.assertIn("currentCoroutineContext().ensureActive()", self.source)
        self.assertIn("catch (cancelled: kotlinx.coroutines.CancellationException)", self.source)
        self.assertIn("SocketTimeoutException", self.source)
        self.assertIn("acquired?.close()", self.source)

    def test_runtime_tests_cover_timeout_and_cancellation(self):
        for name in ("fastResponseIsReturnedWithoutClosingTheStream", "noResponseBecomesRetryableIOExceptionRatherThanSilentConnecting", "explicitCancellationIsNotConvertedIntoRetryableSocketTimeout", "outerDeadlineRemainsCancellationRatherThanNetworkRetry"):
            self.assertIn(name, self.test)

if __name__ == "__main__":
    unittest.main()
