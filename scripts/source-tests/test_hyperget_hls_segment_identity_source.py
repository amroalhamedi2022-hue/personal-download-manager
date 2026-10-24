"""HLS media identity contract: relocated playlists cannot reuse stale staged bytes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HLS = ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls"


class HlsResolvedMediaIdentitySourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = (HLS / "HLSDownloadJob.kt").read_text(encoding="utf-8")
        cls.identity = (HLS / "HlsSegmentIdentity.kt").read_text(encoding="utf-8")
        cls.test = (ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/hls/HlsSegmentIdentityTest.kt").read_text(encoding="utf-8")

    def test_job_persists_resolved_media_uri(self):
        self.assertIn("uri = resolveHlsSegmentIdentity(segmentBaseUrl, segment.uri())", self.job)
        self.assertNotIn("uri = segment.uri(),", self.job)

    def test_final_redirect_url_precedes_playlist_update(self):
        self.assertLess(self.job.index("segmentBaseUrl = response.httpResponseInfo.requestUrl"), self.job.index("updateParts(response.hlsManifest)", self.job.index("segmentBaseUrl = response.httpResponseInfo.requestUrl")))

    def test_media_identity_uses_existing_okhttp_url_resolution(self):
        self.assertIn("HttpUrlUtils.createURL(finalPlaylistUrl).resolve(mediaUri)", self.identity)
        self.assertIn("resolved.newBuilder().fragment(null).build().toString()", self.identity)

    def test_url_embedded_credentials_rejected(self):
        self.assertIn("resolved.username.isEmpty() && resolved.password.isEmpty()", self.identity)

    def test_relocation_and_legacy_regressions(self):
        for name in ("redirectedPlaylistDirectoryInvalidatesIdenticalRelativeFilename", "redirectedPlaylistHostInvalidatesSameRelativeFilename", "legacyRelativeIdentityIsConservativelyInvalidated", "absoluteSegmentRetainsIdentityWhenPlaylistMoves", "identicalFinalPlaylistAndSegmentPreservesCompletedBytes"):
            self.assertIn(name, self.test)


if __name__ == "__main__":
    unittest.main()
