"""Regression contract for bounded OkHttp request dispatch on desktop Windows.

The installed active 1/4/16 benchmark is the runtime gate. A real 32-request trial passed integrity/concurrency but showed no resource win and higher peak memory, so the previously verified 64/64 cap is restored. This source contract only locks that configuration; it cannot prove transfer fairness, throughput, memory, or SHA-256 integrity.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
CONNECTION = (ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/Connection.kt").read_text(encoding="utf-8")
HTTP_CLIENT = (ROOT / "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/OkHttpHttpDownloaderClient.kt").read_text(encoding="utf-8")
LIFECYCLE_TEST = (ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/PartDownloaderIoLifecycleTest.kt").read_text(encoding="utf-8")
DI = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/di/Di.kt").read_text(encoding="utf-8")


class HttpDispatcherBoundContract(unittest.TestCase):
    def test_closing_http_connection_cancels_call_and_closes_source(self):
        close_block = CONNECTION.split("override fun close()", 1)[1].split("\\n    }", 1)[0]
        self.assertIn("cancelTransport?.invoke()", close_block)
        self.assertIn("finally", close_block)
        self.assertIn("source.close()", close_block)
        connect_block = HTTP_CLIENT.split("override suspend fun actualConnect(", 1)[1]
        self.assertIn("val call = newCall(", connect_block)
        self.assertIn("val response = call.await()", connect_block)
        self.assertIn("cancelTransport = { call.cancel() }", connect_block)
        self.assertIn("connectionCloseCancelsTransportAndClosesSource", LIFECYCLE_TEST)

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
        self.assertEqual(global_limit, 64, "restore the verified baseline after the 32-call trial showed no resource benefit")
        self.assertEqual(host_limit, 64, "per-host cap must match the verified global baseline")
        self.assertGreaterEqual(global_limit, 16, "cap must allow one live request per 16-task workload")
        self.assertNotIn("Int.MAX_VALUE", block)
        self.assertIn("installed", __doc__.lower())


if __name__ == "__main__":
    unittest.main()
