#!/usr/bin/env python3
"""Hermetic tests for the loopback-only HTTP Range and concurrency fixture."""
import http.client
import json
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path


class LocalHttpRangeFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="idm-http-fixture-test-")
        cls.root = Path(cls.temp.name)
        cls.payload = bytes(range(256)) * 1024
        cls.file = cls.root / "fixture.bin"
        cls.file.write_bytes(cls.payload)
        cls.port_file = cls.root / "port.txt"
        cls.process = subprocess.Popen(
            [
                sys.executable,
                str(Path(__file__).resolve().with_name("LocalHttpRangeFixture.py")),
                "--file", str(cls.file),
                "--port-file", str(cls.port_file),
                "--chunk-delay-ms", "80"
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        for _ in range(100):
            if cls.port_file.exists():
                break
            if cls.process.poll() is not None:
                raise RuntimeError(f"Fixture exited: {cls.process.stderr.read().decode()}")
            time.sleep(0.05)
        if not cls.port_file.exists():
            cls.process.kill()
            raise RuntimeError("Fixture server did not start")
        cls.port = int(cls.port_file.read_text())

    @classmethod
    def tearDownClass(cls):
        cls.process.terminate()
        try:
            cls.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            cls.process.kill()
        cls.process.stderr.close()
        cls.temp.cleanup()

    def request(self, method, path, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            conn.request(method, path, headers=headers or {})
            response = conn.getresponse()
            status = response.status
            fields = dict(response.getheaders())
            data = response.read()
            return status, fields, data
        finally:
            conn.close()

    def get_stats(self, reset=False):
        status, fields, body = self.request("GET", "/stats?reset=1" if reset else "/stats")
        self.assertEqual(200, status)
        self.assertEqual("application/json", fields["Content-Type"])
        return json.loads(body)

    def wait_idle(self):
        for _ in range(100):
            if self.get_stats()["active_requests"] == 0:
                return
            time.sleep(0.03)
        self.fail("Fixture leaked active request(s)")

    def test_range_response_body_and_headers(self):
        status, headers, data = self.request("HEAD", "/fixture.bin")
        self.assertEqual(200, status)
        self.assertEqual(b"", data)
        self.assertEqual(str(len(self.payload)), headers["Content-Length"])
        self.assertEqual("bytes", headers["Accept-Ranges"])
        status, headers, data = self.request(
            "GET", "/fixture.bin?task=range-test", {"Range": "bytes=7-70"}
        )
        self.assertEqual(206, status)
        self.assertEqual("bytes 7-70/262144", headers["Content-Range"])
        self.assertEqual(self.payload[7:71], data)
        self.wait_idle()

    def test_invalid_ranges_and_paths_do_not_expose_files(self):
        for bad in ("bytes=999999-", "bytes=99-7", "garbage"):
            status, headers, data = self.request(
                "GET", "/fixture.bin", {"Range": bad}
            )
            self.assertEqual(416, status)
            self.assertEqual("bytes */262144", headers["Content-Range"])
            self.assertEqual(b"", data)
        status, _, _ = self.request("GET", "/../../etc/passwd")
        self.assertEqual(404, status)

    def test_concurrency_counts_distinct_tasks_not_range_parts(self):
        self.wait_idle()
        self.get_stats(reset=True)
        start = threading.Barrier(3)
        errors = []
        def fetch(task):
            try:
                start.wait(timeout=4)
                status, _, body = self.request("GET", f"/fixture.bin?task={task}")
                if status != 200 or body != self.payload:
                    errors.append(f"bad response for {task}: {status}")
            except Exception as error:
                errors.append(str(error))
        workers = [
            threading.Thread(target=fetch, args=("task-one",)),
            threading.Thread(target=fetch, args=("task-two",)),
        ]
        for worker in workers:
            worker.start()
        start.wait(timeout=4)
        for worker in workers:
            worker.join(timeout=10)
            self.assertFalse(worker.is_alive())
        self.assertEqual([], errors)
        self.wait_idle()
        snapshot = self.get_stats()
        self.assertEqual(2, snapshot["peak_distinct_tasks"])
        self.assertEqual(2, snapshot["unique_tasks_seen"])
        self.assertEqual(2, snapshot["total_requests"])
        self.assertEqual(len(self.payload) * 2, snapshot["bytes_sent"])
        self.assertEqual(0, snapshot["active_distinct_tasks"])
        self.assertEqual(0, snapshot["active_requests"])
        zero = self.get_stats(reset=True)
        self.assertEqual(0, zero["total_requests"])
        self.assertEqual(0, zero["peak_distinct_tasks"])
        self.assertEqual(0, zero["unique_tasks_seen"])


if __name__ == "__main__":
    unittest.main()
