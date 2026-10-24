"""Source-only regression: HLS segment URLs use the final redirected manifest URL.

No Gradle, application compilation, executable, or network service is invoked.
These structural assertions are not a substitute for Windows HLS E2E tests.
"""
from pathlib import Path
from urllib.parse import urljoin
import unittest

ROOT = Path(__file__).resolve().parents[2]
JOB = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt"
PART = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSPartDownloader.kt"
RESPONSE = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSResponseInfo.kt"


def section(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class HlsRedirectedManifestBaseSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = JOB.read_text(encoding="utf-8")
        cls.part = PART.read_text(encoding="utf-8")
        cls.response = RESPONSE.read_text(encoding="utf-8")

    def test_final_url_is_available_from_http_response(self):
        self.assertIn("requestUrl = response.request.url.toString()", (
            ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/OkHttpHttpDownloaderClient.kt"
        ).read_text(encoding="utf-8"))
        self.assertIn("val httpResponseInfo: HttpResponseInfo", self.response)

    def test_base_url_is_initialized_for_pre_refresh_state(self):
        self.assertIn("private var segmentBaseUrl: String = downloadItem.link", self.job)

    def test_refresh_updates_base_from_actual_response(self):
        refresh = section(self.job, "private suspend fun initialParts()", "suspend fun cancelDownloadScope()")
        self.assertIn("segmentBaseUrl = response.httpResponseInfo.requestUrl", refresh)
        self.assertLess(refresh.index("clearPartDownloaderList()"), refresh.index("segmentBaseUrl ="))
        self.assertLess(refresh.index("segmentBaseUrl ="), refresh.index("updateParts(response.hlsManifest)"))

    def test_segment_worker_uses_refreshed_base_not_original_link(self):
        create = section(self.job, "private fun getOrCreatePartDownloader(", "private fun destroyPartDownloader(")
        self.assertIn("baseURL = segmentBaseUrl", create)
        self.assertNotIn("baseURL = downloadItem.link", create)

    def test_segment_resolver_uses_worker_base(self):
        self.assertIn("HttpUrlUtils.createURL(baseURL).resolve(part.link)", self.part)

    def test_redirected_path_and_origin_change_relative_resolution(self):
        initial = "https://front.example/media/master.m3u8"
        final = "https://cdn.example.net/v2/session/playlist.m3u8"
        segment = "chunks/001.ts"
        self.assertEqual(urljoin(final, segment), "https://cdn.example.net/v2/session/chunks/001.ts")
        self.assertNotEqual(urljoin(initial, segment), urljoin(final, segment))

    def test_absolute_segment_urls_are_not_rebased(self):
        final = "https://cdn.example.net/v2/playlist.m3u8"
        segment = "https://other.example.org/signed/part.ts?sig=abc"
        self.assertEqual(urljoin(final, segment), segment)


if __name__ == "__main__":
    unittest.main()
