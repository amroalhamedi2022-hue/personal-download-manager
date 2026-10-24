"""HLS manifest credential redirect source contracts; no application compilation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader"
UI = ROOT / "shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared"


def read(path):
    return path.read_text(encoding="utf-8")


class HlsManifestRedirectSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = read(CORE / "downloaditem/hls/HlsSegmentCredentialPolicy.kt")
        cls.job = read(CORE / "downloaditem/hls/HLSDownloadJob.kt")
        cls.checker = read(UI / "downloaderinui/hls/HLSLinkChecker.kt")
        cls.client = read(CORE / "connection/OkHttpHttpDownloaderClient.kt")
        cls.creds = read(CORE / "downloaditem/http/HttpDownloadCredentials.kt")
        cls.kotlin_tests = read(
            ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsSegmentCredentialPolicyTest.kt"
        )

    def test_playlist_policy_is_explicit(self):
        self.assertIn("fun credentialsForHlsPlaylist(source: IHLSCredentials): HttpDownloadCredentials", self.policy)
        self.assertIn("link = source.link", self.policy)
        self.assertIn("userAgent = source.userAgent", self.policy)

    def test_auth_headers_are_retained_only_for_initial_request(self):
        body = self.policy.split("fun credentialsForHlsPlaylist(", 1)[1].split(
            "internal fun credentialsForHlsSegment(", 1
        )[0]
        self.assertIn("source.headers", body)
        self.assertIn("source.username", body)
        self.assertIn("source.password", body)
        self.assertIn("followRedirects = headers.isNullOrEmpty() && !hasBasicAuth", body)

    def test_host_proxy_and_transport_headers_are_stripped(self):
        self.assertIn("it.lowercase() !in forbiddenSegmentHeaders", self.policy)
        for name in ('"host"', '"proxy-authorization"', '"connection"', '"range"'):
            self.assertIn(name, self.policy)

    def test_download_resume_uses_guarded_playlist_request(self):
        self.assertIn("credentialsForHlsPlaylist(downloadItem), null, null", self.job)
        self.assertNotIn("client.connect(\n            downloadItem, null, null", self.job)

    def test_ui_preview_uses_same_guard(self):
        self.assertIn("import ir.amirab.downloader.downloaditem.hls.credentialsForHlsPlaylist", self.checker)
        self.assertIn("client.connect(credentialsForHlsPlaylist(credentials), null, null)", self.checker)
        self.assertNotIn("client.connect(credentials, null, null)", self.checker)

    def test_transport_has_no_redirect_client_and_transient_flag(self):
        self.assertIn(".followRedirects(false)", self.client)
        self.assertIn(".followSslRedirects(false)", self.client)
        self.assertIn("!downloadCredentials.followRedirects", self.client)
        self.assertIn("@Transient val followRedirects: Boolean = true", self.creds)

    def test_normal_anonymous_playlist_redirects_remain_possible(self):
        self.assertIn("headers.isNullOrEmpty() && !hasBasicAuth", self.policy)
        self.assertIn("anonymousPlaylistPreservesOrdinaryRedirects", self.kotlin_tests)
        self.assertIn("onlyFilteredHeadersDoNotBlockAnonymousPlaylistRedirects", self.kotlin_tests)

    def test_deferred_runtime_regressions_cover_sensitive_inputs(self):
        for test in (
            "authenticatedPlaylistNeverFollowsRedirects",
            "basicAuthAloneDisablesPlaylistRedirects",
            "cookieAloneDisablesPlaylistRedirects",
            "playlistDropsHopByHopAndProxyAuthHeaders",
            "userAgentAloneDoesNotBlockAnonymousRedirects",
        ):
            self.assertIn(test, self.kotlin_tests)


if __name__ == "__main__":
    unittest.main()
