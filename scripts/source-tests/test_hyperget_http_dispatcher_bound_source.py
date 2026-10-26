"""Regression contract for bounded OkHttp request dispatch on desktop Windows.

The active 1/4/16 benchmark is the runtime gate: this source contract permits
controlled tuning between 16 and 64 requests, but cannot prove transfer fairness,
throughput, memory, or SHA-256 integrity.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
DI = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/di/Di.kt").read_text(encoding="utf-8")


class HttpDispatcherBoundContract(unittest.TestCase):
    def test_global_and_per_host_async_request_caps_are_finite_and_support_16_tasks(self):
        marker = "single<OkHttpClient>(BaseOKHttpClientQualifier)"
        self.assertIn(marker, DI)
        block = DI.split(marker, 1)[1].split("single<AppDns>", 1)[0]
        global_match = re.search(r"\bmaxRequests\s*=\s*(\d+)", block)
        host_match = re.search(r"\bmaxRequestsPerHost\s*=\s*(\d+)", block)
        self.assertIsNotNone(global_match, "global OkHttp request cap is missing")
        self.assertIsNotNone(host_match, "per-host OkHttp request cap is missing")
        global_limit = int(global_match.group(1))
        host_limit = int(host_match.group(1))
        for label, limit in (("global", global_limit), ("per-host", host_limit)):
            with self.subTest(scope=label):
                self.assertGreaterEqual(limit, 16, "cap must allow one live request per 16-task workload")
                self.assertLessEqual(limit, 64, "cap must remain bounded during segmented-download bursts")
        self.assertEqual(global_limit, host_limit, "global and per-host policies must stay aligned")
        self.assertNotIn("Int.MAX_VALUE", block)
        self.assertIn("installed", __doc__.lower())


if __name__ == "__main__":
    unittest.main()
