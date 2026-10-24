"""HLS MIME safety/source wiring checks. Stdlib only; no app build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HLS = ROOT / 'downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls'


class HlsMimeSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = (HLS / 'HLSResponseInfo.kt').read_text(encoding='utf-8')
        cls.helper = (HLS / 'HlsContentType.kt').read_text(encoding='utf-8')
        cls.smoke = (ROOT / 'scripts/source-tests/HyperGetHlsContentTypeSmoke.kt').read_text(encoding='utf-8')

    def test_mime_is_normalized_before_comparison(self):
        self.assertIn("substringBefore(';')?.trim()?.lowercase()", self.helper)

    def test_both_supported_types_are_exact(self):
        self.assertIn('mediaType == "application/x-mpegurl"', self.helper)
        self.assertIn('mediaType == "application/vnd.apple.mpegurl"', self.helper)

    def test_old_prefix_comparison_is_gone(self):
        self.assertNotIn('it.startsWith(contentType', self.job)

    def test_production_uses_guard(self):
        self.assertIn('isHlsPlaylistContentType(contentType)', self.job)

    def test_bounded_reader_still_used(self):
        self.assertIn('readBoundedHlsPlaylist(it.inputStream())', self.job)
        self.assertIn('declaredLength > MAX_HLS_PLAYLIST_BYTES', self.job)

    def test_positive_charset_case(self):
        self.assertIn('application/vnd.apple.mpegurl; charset=utf-8', self.smoke)

    def test_negative_spoof_case(self):
        self.assertIn('application/vnd.apple.mpegurl-evil', self.smoke)

    def test_missing_and_unrelated_types(self):
        self.assertIn('"missing header"', self.smoke)
        self.assertIn('"unrelated MIME"', self.smoke)


if __name__ == '__main__':
    unittest.main()
