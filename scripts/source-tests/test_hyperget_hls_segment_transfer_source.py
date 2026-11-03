"""Fast source contract for the HLS complete-segment milestone.

These assertions protect production wiring but do NOT substitute for the
Windows loopback socket/JUnit test or installed downloader acceptance.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HLS = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls"
TEST = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsSegmentTransferLoopbackTest.kt"


class HlsCompleteSegmentContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = (HLS / "HLSPartDownloader.kt").read_text(encoding="utf-8")
        cls.validator = (HLS / "HlsSegmentTransferValidation.kt").read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_only_full_http_200_is_accepted(self):
        self.assertIn("statusCode != 200", self.validator)
        self.assertIn("requireCompleteHlsSegmentResponse(connect.responseInfo.statusCode)", self.worker)

    def test_length_checked_before_completion_flag(self):
        self.assertLess(
            self.worker.index("requireCompleteHlsSegmentBytes(part.length, part.current)"),
            self.worker.index("part.isCompleted = true"),
        )

    def test_failed_eof_uses_failure_not_completed(self):
        self.assertIn("onCanceled(failure)", self.worker)
        self.assertIn("receivedBytes <= 0L", self.validator)
        self.assertIn("receivedBytes != expectedBytes", self.validator)

    def test_real_socket_assembly_and_hash_fixture(self):
        for marker in (
            "HttpServer.create", "HLSResponseInfo.fromConnection",
            "HLSPartDownloader(", "destination.onAllPartsCompleted",
            "assertContentEquals(expected, output)", "MessageDigest.getInstance(\"SHA-256\")",
        ):
            self.assertIn(marker, self.test)

    def test_partial_response_negative_fixture(self):
        self.assertIn("respond(exchange, 206", self.test)
        self.assertIn("assertFalse(part.isCompleted)", self.test)


if __name__ == "__main__":
    unittest.main()
