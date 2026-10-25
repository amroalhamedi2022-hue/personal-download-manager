"""Fast structural check; Windows Kotlin/JUnit is a separate acceptance gate."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class LifecycleSource(unittest.TestCase):
    def test_opposite_actor_removed_in_both_transitions(self):
        code = (ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/DownloadManager.kt").read_text()
        for name in ("contextForLifecyclePause", "contextForLifecycleResume"):
            body = code.split("internal fun " + name, 1)[1].split("\n\n", 1)[0]
            self.assertIn("minusKey(StoppedBy).minusKey(ResumedBy)", body)
            self.assertIn("contextContainer.updateContext(id) { " + name + "(it, context) }", code)
    def test_kotlin_regression_has_four_cases(self):
        tests = (ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/DownloadManagerLifecycleContextTest.kt").read_text()
        self.assertEqual(tests.count("@Test"), 4)
        self.assertIn("repeat(16)", tests)
        self.assertIn("RemovedBy(User)", tests)
if __name__ == "__main__":
    unittest.main()
