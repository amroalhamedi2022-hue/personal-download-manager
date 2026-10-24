"""Structural wiring checks; Kotlin desktopTest is the behavioral gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader"
HLS = CORE / "downloaditem/hls"
UI = ROOT / "shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/downloaderinui/hls"


class GuardedHlsRedirectSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guard = (HLS / "HlsGuardedRedirects.kt").read_text(encoding="utf-8")
        cls.job = (HLS / "HLSDownloadJob.kt").read_text(encoding="utf-8")
        cls.part = (HLS / "HLSPartDownloader.kt").read_text(encoding="utf-8")
        cls.preview = (UI / "HLSLinkChecker.kt").read_text(encoding="utf-8")
        cls.tests = (ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsGuardedRedirectTest.kt").read_text(encoding="utf-8")

    def test_manifest_refresh_uses_guard(self):
        self.assertIn("client.connectHlsWithOriginGuard(", self.job)
        self.assertIn("credentialsForHlsPlaylist(downloadItem)", self.job)

    def test_preview_uses_guard(self):
        self.assertIn("client.connectHlsWithOriginGuard(credentialsForHlsPlaylist(credentials))", self.preview)

    def test_segment_workers_use_guard(self):
        self.assertIn("client.connectHlsWithOriginGuard(", self.part)
        self.assertIn("credentialsForHlsSegment(playlistCredentials, baseURL, url)", self.part)

    def test_anonymous_behavior_is_preserved(self):
        self.assertIn("if (credentials.followRedirects) return connect(credentials, null, null)", self.guard)

    def test_redirect_is_origin_checked_on_every_hop(self):
        self.assertIn("sameHlsOrigin(originalUrl, target)", self.guard)
        self.assertIn("sameHlsOrigin(originalUrl, responseUrl)", self.guard)
        self.assertIn("target.username.isNotEmpty()", self.guard)
        self.assertIn("target.password.isNotEmpty()", self.guard)

    def test_intermediate_responses_are_closed(self):
        self.assertLess(self.guard.index("connection.close()"), self.guard.index("sameHlsOrigin(originalUrl, target)"))

    def test_bounded_and_loop_safe(self):
        self.assertIn("MAX_GUARDED_HLS_REDIRECTS = 5", self.guard)
        self.assertIn("!visited.add(target.toString())", self.guard)

    def test_real_loopback_network_regressions_are_present(self):
        path = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsGuardedRedirectLoopbackTest.kt"
        content = path.read_text(encoding="utf-8")
        self.assertIn("sameOrigin302PreservesExplicitCookieWithoutAmbientJarCookie", content)
        self.assertIn("crossPort302NeverContactsSecondServer", content)
        self.assertIn("OkHttpHttpDownloaderClient(", content)
        self.assertIn("HttpServer.create(", content)

    def test_kotlin_behavioral_regressions_are_present(self):
        for name in ("followsRelativeSameOriginRedirectsAndRetainsExplicitCookies",
                     "crossHostRedirectIsRejectedBeforeSecondRequest",
                     "protocolDowngradeIsRejected", "portChangeIsRejected",
                     "urlEmbeddedCredentialsAreRejected", "cycleIsRejectedWithoutRepeatingFirstRequest",
                     "longChainStopsAfterFiveRedirects", "protectedSegmentUsesSameGuardForRedirects"):
            self.assertIn(name, self.tests)


if __name__ == "__main__":
    unittest.main()
