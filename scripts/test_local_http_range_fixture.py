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
                "--chunk-delay-ms", "80",
                "--task-chunk-delay-ms", "120"
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        # On Windows the file may become visible before write_text has
        # flushed its port digits. Wait for a complete numeric value.
        port_text = ""
        for _ in range(100):
            if cls.port_file.exists():
                port_text = cls.port_file.read_text().strip()
                if port_text.isdecimal():
                    break
            if cls.process.poll() is not None:
                raise RuntimeError(f"Fixture exited: {cls.process.stderr.read().decode()}")
            time.sleep(0.05)
        if not port_text.isdecimal():
            cls.process.kill()
            raise RuntimeError("Fixture server did not publish a valid port")
        cls.port = int(port_text)

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

    def get_stats(self, reset=False, gate_tasks=0):
        path = (f"/stats?reset=1&gate_tasks={gate_tasks}" if reset else "/stats")
        status, fields, body = self.request("GET", path)
        self.assertEqual(200, status)
        self.assertEqual("application/json", fields["Content-Type"])
        return json.loads(body)

    def wait_idle(self):
        for _ in range(100):
            if self.get_stats()["active_requests"] == 0:
                return
            time.sleep(0.03)
        self.fail("Fixture leaked active request(s)")

    def test_gated_two_task_socket_admission_before_body(self):
        self.wait_idle()
        before = self.get_stats(reset=True, gate_tasks=2)
        self.assertEqual(2, before["gate_expected_tasks"])
        self.assertFalse(before["gate_released"])
        results, errors = {}, []
        def fetch(task):
            try:
                results[task] = self.request("GET", f"/fixture.bin?task={task}")
            except Exception as exc:
                errors.append(str(exc))
        first = threading.Thread(target=fetch, args=("gate-first",))
        second = threading.Thread(target=fetch, args=("gate-second",))
        first.start()
        for _ in range(100):
            snapshot = self.get_stats()
            if snapshot["active_distinct_tasks"] == 1:
                break
            time.sleep(0.02)
        else:
            self.fail("First gated socket was not registered")
        self.assertEqual(0, snapshot["bytes_sent"])
        self.assertFalse(snapshot["gate_released"])
        self.assertTrue(first.is_alive())
        second.start()
        for worker in (first, second):
            worker.join(timeout=12)
            self.assertFalse(worker.is_alive())
        self.assertEqual([], errors)
        for task in ("gate-first", "gate-second"):
            self.assertEqual(200, results[task][0])
            self.assertEqual(self.payload, results[task][2])
        self.wait_idle()
        after = self.get_stats()
        self.assertTrue(after["gate_released"])
        self.assertEqual(2, after["peak_distinct_tasks"])
        self.assertEqual(2, after["unique_tasks_seen"])
        self.get_stats(reset=True)

    def test_gated_metadata_probes_do_not_release_data_socket_barrier(self):
        # Windows evidence showed four tasks with only 256/512 bytes:
        # those completed probes must not masquerade as active downloads.
        self.wait_idle()
        self.get_stats(reset=True, gate_tasks=2)
        status, _, body = self.request(
            "GET", "/fixture.bin?task=probe-first",
            {"Range": "bytes=0-255"},
        )
        self.assertEqual(206, status)
        self.assertEqual(self.payload[:256], body)
        before = self.get_stats()
        self.assertFalse(before["gate_released"])
        self.assertEqual(0, before["active_distinct_tasks"])
        self.assertEqual(1, before["task_probe_get_started"]["probe-first"])
        results, errors = {}, []

        def data_fetch(task, start, end):
            try:
                results[task] = self.request(
                    "GET", f"/fixture.bin?task={task}",
                    {"Range": f"bytes={start}-{end}"},
                )
            except Exception as exc:
                errors.append(str(exc))

        first = threading.Thread(target=data_fetch, args=("data-first", 0, 131071))
        second = threading.Thread(target=data_fetch, args=("data-second", 131072, 262143))
        first.start()
        for _ in range(100):
            waiting = self.get_stats()
            if waiting["active_distinct_tasks"] == 1:
                break
            time.sleep(0.02)
        else:
            self.fail("First long data GET did not wait at the barrier")
        self.assertFalse(waiting["gate_released"])
        status, _, body = self.request(
            "GET", "/fixture.bin?task=probe-second",
            {"Range": "bytes=0-511"},
        )
        self.assertEqual(206, status)
        self.assertEqual(self.payload[:512], body)
        waiting = self.get_stats()
        self.assertEqual(1, waiting["active_distinct_tasks"])
        self.assertFalse(waiting["gate_released"])
        second.start()
        for worker in (first, second):
            worker.join(timeout=8)
            self.assertFalse(worker.is_alive())
        self.assertEqual([], errors)
        self.assertEqual(self.payload[:131072], results["data-first"][2])
        self.assertEqual(self.payload[131072:], results["data-second"][2])
        self.wait_idle()
        after = self.get_stats()
        self.assertTrue(after["gate_released"])
        self.assertEqual(2, after["peak_distinct_tasks"])
        self.assertEqual(4, after["unique_tasks_seen"])
        self.assertEqual(1, after["task_probe_get_started"]["probe-second"])
        self.assertEqual(1, after["task_data_get_started"]["data-first"])
        self.assertEqual(1, after["task_data_get_started"]["data-second"])
        reset = self.get_stats(reset=True)
        self.assertEqual({}, reset["task_probe_get_started"])
        self.assertEqual({}, reset["task_data_get_started"])

    def test_gated_parallel_ranges_share_one_task_byte_budget(self):
        self.wait_idle()
        self.get_stats(reset=True, gate_tasks=2)
        results, errors = {}, []

        def fetch(label, task, range_header):
            try:
                headers = {"Range": range_header} if range_header else {}
                results[label] = self.request("GET", f"/fixture.bin?task={task}", headers)
            except Exception as exc:
                errors.append(str(exc))

        workers = [
            threading.Thread(target=fetch, args=("left", "split", "bytes=0-131071")),
            threading.Thread(target=fetch, args=("right", "split", "bytes=131072-262143")),
            threading.Thread(target=fetch, args=("peer", "peer", None)),
        ]
        for worker in workers:
            worker.start()
        for worker in workers:
            worker.join(timeout=12)
            self.assertFalse(worker.is_alive())
        self.assertEqual([], errors)
        self.assertEqual(self.payload[:131072], results["left"][2])
        self.assertEqual(self.payload[131072:], results["right"][2])
        self.assertEqual(self.payload, results["peer"][2])
        self.assertEqual(206, results["left"][0])
        self.assertEqual(206, results["right"][0])
        self.wait_idle()
        snapshot = self.get_stats()
        self.assertEqual(2, snapshot["peak_distinct_tasks"])
        self.assertEqual(3, snapshot["total_requests"])
        self.assertEqual(8, snapshot["paced_chunks"])
        # The aggregate gated pacer is the ONLY throttle for 16-way
        # workloads: do not stack the legacy per-connection sleep.
        self.assertEqual(0, snapshot["connection_delay_sleeps"])
        # The split task's two sockets must wait for the SAME budget.
        self.assertGreater(snapshot["paced_wait_seconds"], 0.1)
        self.assertEqual(len(self.payload) * 2, snapshot["bytes_sent"])
        self.assertEqual(len(self.payload), snapshot["task_bytes_sent"]["split"])
        self.assertEqual(len(self.payload), snapshot["task_bytes_sent"]["peer"])
        reset = self.get_stats(reset=True)
        self.assertEqual({}, reset["task_bytes_sent"])
        self.assertEqual(0, reset["paced_chunks"])
        self.assertEqual(0.0, reset["paced_wait_seconds"])

    def test_many_range_sockets_share_bounded_live_task_pacer(self):
        self.wait_idle()
        self.get_stats(reset=True, gate_tasks=1)
        results, errors = [], []
        # Twelve sockets share one task, with no speculative reservations
        # beyond the current send. This catches starvation/timeouts when a
        # segmented downloader opens many parallel Range connections.
        def fetch(part):
            try:
                start = (part % 4) * 65536
                status, _, body = self.request(
                    "GET", "/fixture.bin?task=multi-range",
                    {"Range": f"bytes={start}-{start + 65535}"}
                )
                results.append((status, body, start))
            except Exception as exc:
                errors.append(str(exc))
        workers = [threading.Thread(target=fetch, args=(i,)) for i in range(12)]
        for worker in workers:
            worker.start()
        for worker in workers:
            worker.join(timeout=8)
            self.assertFalse(worker.is_alive(), "A Range socket was starved")
        self.assertEqual([], errors)
        self.assertEqual(12, len(results))
        for status, body, start in results:
            self.assertEqual(206, status)
            self.assertEqual(self.payload[start:start + 65536], body)
        self.wait_idle()
        snapshot = self.get_stats()
        self.assertEqual(12, snapshot["total_requests"])
        self.assertEqual(12, snapshot["paced_chunks"])
        self.assertEqual(0, snapshot["connection_delay_sleeps"])
        self.assertEqual(12 * 65536, snapshot["task_bytes_sent"]["multi-range"])
        self.assertEqual(1, snapshot["peak_distinct_tasks"])
        self.get_stats(reset=True)

    def test_gate_rejects_invalid_configuration(self):
        for path in ("/stats?gate_tasks=2", "/stats?reset=1&gate_tasks=65",
                     "/stats?reset=1&gate_tasks=abc"):
            status, _, _ = self.request("GET", path)
            self.assertEqual(400, status)
        self.assertEqual(0, self.get_stats(reset=True)["gate_expected_tasks"])

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

    def test_short_range_probe_has_completed_response_evidence(self):
        # A 512-byte GET is a completed short probe, not proof that the
        # entire 32-MiB download task completed. Keep both counts visible.
        self.wait_idle()
        self.get_stats(reset=True)
        status, headers, body = self.request(
            "GET", "/fixture.bin?task=short-probe",
            {"Range": "bytes=0-511"},
        )
        self.assertEqual(206, status)
        self.assertEqual(self.payload[:512], body)
        self.wait_idle()
        snapshot = self.get_stats()
        self.assertEqual(1, snapshot["task_get_started"]["short-probe"])
        self.assertEqual(1, snapshot["task_get_completed"]["short-probe"])
        self.assertEqual(0, snapshot["task_get_interrupted"].get("short-probe", 0))
        self.assertEqual(512, snapshot["task_requested_bytes"]["short-probe"])
        self.assertEqual(512, snapshot["task_bytes_sent"]["short-probe"])
        self.assertEqual(0, snapshot["active_distinct_tasks"])
        reset = self.get_stats(reset=True)
        for field in ("task_get_started", "task_get_completed",
                      "task_get_interrupted", "task_requested_bytes"):
            self.assertEqual({}, reset[field])

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
        # Ungated 1/4-way workloads still exercise legacy socket pacing.
        self.assertGreater(snapshot["connection_delay_sleeps"], 0)
        zero = self.get_stats(reset=True)
        self.assertEqual(0, zero["connection_delay_sleeps"])
        self.assertEqual(0, zero["total_requests"])
        self.assertEqual(0, zero["peak_distinct_tasks"])
        self.assertEqual(0, zero["unique_tasks_seen"])


if __name__ == "__main__":
    unittest.main()
