"""HLS body size source contracts. Python stdlib only; no application build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HLS = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls"


class HlsBodySafetyContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.response = (HLS / "HLSResponseInfo.kt").read_text(encoding="utf-8")
        cls.reader = (HLS / "HlsPlaylistBodyReader.kt").read_text(encoding="utf-8")
        cls.smoke = (ROOT / "scripts/source-tests/HyperGetHlsBodyReaderSmoke.kt").read_text(encoding="utf-8")

    def test_production_uses_bounded_reader(self):
        self.assertIn("readBoundedHlsPlaylist(it.inputStream())", self.response)
        self.assertNotIn("it.readUtf8()", self.response)

    def test_declared_size_checked_unconditionally(self):
        check = "declaredLength != null && declaredLength > MAX_HLS_PLAYLIST_BYTES"
        self.assertIn(check, self.response)
        self.assertLess(self.response.index(check), self.response.index("val hlsPossibleHeaders"))

    def test_actual_bytes_capped(self):
        self.assertIn("MAX_HLS_PLAYLIST_BYTES: Int = 2 * 1024 * 1024", self.reader)
        self.assertIn("maxBytes.toLong() - total + 1L", self.reader)
        self.assertIn("read > maxBytes - total", self.reader)

    def test_oversize_fails_closed(self):
        self.assertIn('throw IOException("HLS playlist exceeds maximum allowed size")', self.reader)

    def test_zero_read_handled(self):
        self.assertIn("if (read == 0)", self.reader)
        self.assertIn("val single = input.read()", self.reader)

    def test_boundary_and_stream_regressions_present(self):
        for label in (
            "exact size accepted",
            "2MiB exact limit accepted",
            "2MiB plus one rejected",
            "oversized rejected",
            "oversized across buffer rejected",
            "zero-length read handled",
            "utf8 preserved",
        ):
            self.assertIn(label, self.smoke)


if __name__ == "__main__":
    unittest.main()
