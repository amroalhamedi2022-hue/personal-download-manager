"""Source-only guardrails. Kotlin runtime tests are deferred until final build gate."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
CORE = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader"
def read(path):
    return (CORE / path).read_text(encoding="utf-8")

class HlsSegmentCredentialsSourceTest(unittest.TestCase):
    def test_policy_uses_scheme_host_port_not_prefix(self):
        s = read("downloaditem/hls/HlsSegmentCredentialPolicy.kt")
        for term in ("first.scheme == second.scheme", "first.host == second.host",
                     "first.port == second.port", "sameHlsOrigin(originalOrigin, finalPlaylistOrigin)",
                     "sameHlsOrigin(originalOrigin, segmentOrigin)"):
            self.assertIn(term, s)

    def test_sensitive_headers_and_credentials_are_origin_scoped(self):
        s = read("downloaditem/hls/HlsSegmentCredentialPolicy.kt")
        for term in ("source.headers?.filterKeys", "if (trusted)", "source.username?.takeIf { trusted }",
                     "source.password?.takeIf { trusted }", '"proxy-authorization"', '"host"', '"range"'):
            self.assertIn(term, s)

    def test_authenticated_segments_cannot_follow_redirects(self):
        p = read("downloaditem/hls/HlsSegmentCredentialPolicy.kt")
        c = read("connection/OkHttpHttpDownloaderClient.kt")
        d = read("downloaditem/http/HttpDownloadCredentials.kt")
        self.assertIn("followRedirects = !hasForwardedSecrets", p)
        self.assertIn("!downloadCredentials.followRedirects", c)
        self.assertIn(".followRedirects(false)", c)
        self.assertIn("@Transient val followRedirects: Boolean = true", d)

    def test_part_workers_use_scoped_credentials(self):
        p = read("downloaditem/hls/HLSPartDownloader.kt")
        j = read("downloaditem/hls/HLSDownloadJob.kt")
        self.assertIn("credentialsForHlsSegment(playlistCredentials, baseURL, url)", p)
        self.assertIn("playlistCredentials = downloadItem", j)
        self.assertNotIn("HttpDownloadCredentials(url)", p)

    def test_user_agent_only_is_allowed_cross_origin(self):
        p = read("downloaditem/hls/HlsSegmentCredentialPolicy.kt")
        self.assertIn("userAgent = source.userAgent", p)
        self.assertIn("val hasForwardedSecrets = !headers.isNullOrEmpty() || hasBasicAuth", p)

    def test_deferred_unit_regressions_exist(self):
        p = ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsSegmentCredentialPolicyTest.kt"
        t = p.read_text(encoding="utf-8")
        for name in ("sameOriginPreservesRequiredHeadersAndBasicAuthWithoutRedirects",
                     "foreignHostDoesNotReceiveSecrets", "redirectedPlaylistDoesNotExpandCredentialTrust",
                     "differentPortAndSchemeAreUntrusted", "hopByHopAndHostHeadersAreNotForwarded"):
            self.assertIn(name, t)

if __name__ == "__main__":
    unittest.main()
