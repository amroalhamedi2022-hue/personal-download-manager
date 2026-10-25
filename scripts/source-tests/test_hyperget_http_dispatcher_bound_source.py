"""Regression contract for bounded OkHttp request dispatch on desktop Windows.

The installed 1/4/16 benchmark remains mandatory: this source contract alone
cannot prove all 16 downloads still reach real data transfer after the cap.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DI = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/di/Di.kt").read_text(encoding="utf-8")


class HttpDispatcherBoundContract(unittest.TestCase):
    def test_global_and_per_host_async_request_caps_are_finite(self):
        marker = "single<OkHttpClient>(BaseOKHttpClientQualifier)"
        self.assertIn(marker, DI)
        block = DI.split(marker, 1)[1].split("single<AppDns>", 1)[0]
        self.assertIn("maxRequests = 64", block)
        self.assertIn("maxRequestsPerHost = 64", block)
        self.assertNotIn("Int.MAX_VALUE", block)
        self.assertIn("installed", __doc__.lower())


if __name__ == "__main__":
    unittest.main()
