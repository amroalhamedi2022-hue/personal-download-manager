#!/usr/bin/env python3
"""Deterministic loopback HTTP Range fixture for installed Windows tests.

Standard-library only. Supports real HEAD, GET, 206 and 416; serves exactly
one fixture. /stats is a loopback-only test endpoint used to PROVE how many
distinct tasks actually transferred concurrently (not merely queued).
"""
import argparse
from contextlib import contextmanager
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

RANGE = re.compile(r"^bytes=(\d+)-(\d*)$")


class TaskChunkPacer:
    """Share one byte-rate budget across all Range sockets of a task.

    Reserving a chunk is serialized by the fixture's existing guard. The
    wait itself happens outside the guard, so different tasks stay parallel.
    """
    def __init__(self, interval_seconds):
        self.interval_seconds = interval_seconds
        self.next_send = {}
        self.task_locks = {}
        self.lock_guard = threading.Lock()

    @contextmanager
    def slot(self, task_id):
        # Serialize actual sends per task instead of reserving arbitrarily
        # distant future slots. Many Range sockets otherwise queue >10s of
        # future sends, hit the downloader's read timeout and drop live tasks.
        with self.lock_guard:
            task_lock = self.task_locks.setdefault(task_id, threading.Lock())
        with task_lock:
            now = time.monotonic()
            wait = max(0.0, self.next_send.get(task_id, now) - now)
            if wait:
                time.sleep(wait)
            try:
                yield wait
            finally:
                # Advance only after the actual write, not at reservation time.
                self.next_send[task_id] = time.monotonic() + self.interval_seconds

    def reset(self):
        with self.lock_guard:
            self.next_send.clear()
            self.task_locks.clear()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", type=Path, required=True)
    ap.add_argument("--port-file", type=Path, required=True)
    ap.add_argument("--chunk-delay-ms", type=float, default=15.0)
    ap.add_argument("--task-chunk-delay-ms", type=float, default=0.0)
    args = ap.parse_args()
    if args.task_chunk_delay_ms < 0:
        ap.error("--task-chunk-delay-ms must be nonnegative")
    if args.chunk_delay_ms < 0:
        ap.error("--chunk-delay-ms must be nonnegative")
    file_path = args.file.resolve(strict=True)
    if not file_path.is_file():
        raise ValueError("Fixture source must be a regular file")
    size = file_path.stat().st_size
    if size <= 0:
        raise ValueError("Fixture source must not be empty")

    guard = threading.Lock()
    stats = {
        "active_requests": 0,
        "peak_requests": 0,
        "total_requests": 0,
        "bytes_sent": 0,
        "peak_distinct_tasks": 0,
        "paced_chunks": 0,
        "paced_wait_seconds": 0.0,
    }
    tasks_active = {}
    tasks_seen = set()
    task_bytes_sent = {}
    # Per-task bounded HTTP response lifecycle counters distinguish a
    # short successful Range probe from a disconnected/stalled data socket.
    # Task names are synthetic fixture labels; no URL or credential is logged.
    task_get_started = {}
    task_get_completed = {}
    task_get_interrupted = {}
    task_requested_bytes = {}
    task_pacer = TaskChunkPacer(args.task_chunk_delay_ms / 1000.0)
    # Test-only bounded admission barrier: prove distinct live GET sockets
    # without allowing early fast tasks to finish before the last starts.
    gate_expected = [0]
    gate_open = threading.Event()
    gate_open.set()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def stats_request(self, query):
            params = parse_qs(query)
            reset = params.get("reset") == ["1"]
            gate_values = params.get("gate_tasks", [])
            if gate_values and (not reset or len(gate_values) != 1):
                self.send_error(400, "gate_tasks requires reset=1")
                return
            try:
                requested_gate = int(gate_values[0]) if gate_values else 0
            except ValueError:
                self.send_error(400, "Invalid gate_tasks")
                return
            if requested_gate < 0 or requested_gate > 64:
                self.send_error(400, "gate_tasks outside 0..64")
                return
            with guard:
                if reset and stats["active_requests"]:
                    self.send_error(409, "Transfers still active")
                    return
                if reset:
                    for key in stats:
                        stats[key] = 0
                    tasks_active.clear()
                    tasks_seen.clear()
                    task_bytes_sent.clear()
                    task_get_started.clear()
                    task_get_completed.clear()
                    task_get_interrupted.clear()
                    task_requested_bytes.clear()
                    task_pacer.reset()
                    gate_expected[0] = requested_gate
                    if requested_gate:
                        gate_open.clear()
                    else:
                        gate_open.set()
                snapshot = dict(stats)
                snapshot["gate_expected_tasks"] = gate_expected[0]
                snapshot["gate_released"] = gate_open.is_set()
                snapshot["active_distinct_tasks"] = len(tasks_active)
                # Only local synthetic task names, never request URLs, headers
                # or credentials. Helps distinguish late starts from early exits.
                snapshot["active_task_ids"] = sorted(tasks_active)
                snapshot["unique_tasks_seen"] = len(tasks_seen)
                snapshot["task_bytes_sent"] = dict(sorted(task_bytes_sent.items()))
                snapshot["task_get_started"] = dict(sorted(task_get_started.items()))
                snapshot["task_get_completed"] = dict(sorted(task_get_completed.items()))
                snapshot["task_get_interrupted"] = dict(sorted(task_get_interrupted.items()))
                snapshot["task_requested_bytes"] = dict(sorted(task_requested_bytes.items()))
            body = json.dumps(snapshot, sort_keys=True).encode("ascii")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)

        def serve_file(self, send_body):
            address = urlsplit(self.path)
            if address.path == "/stats":
                self.stats_request(address.query)
                return
            if address.path != "/fixture.bin":
                self.send_error(404)
                return
            start, end = 0, size - 1
            requested = self.headers.get("Range")
            if requested:
                m = RANGE.fullmatch(requested.strip())
                if m:
                    start = int(m.group(1))
                    end = min(size - 1, int(m.group(2))) if m.group(2) else size - 1
                if not m or start >= size or end < start:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
            remaining = end - start + 1
            self.send_response(206 if requested else 200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("ETag", f'"windows-e2e-{size}"')
            self.send_header("Content-Length", str(remaining))
            if requested:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            if not send_body:
                return

            task_id = parse_qs(address.query).get("task", ["unlabeled"])[0][:128]
            with guard:
                task_get_started[task_id] = task_get_started.get(task_id, 0) + 1
                task_requested_bytes[task_id] = task_requested_bytes.get(task_id, 0) + remaining
                stats["active_requests"] += 1
                stats["total_requests"] += 1
                tasks_seen.add(task_id)
                tasks_active[task_id] = tasks_active.get(task_id, 0) + 1
                stats["peak_requests"] = max(stats["peak_requests"], stats["active_requests"])
                stats["peak_distinct_tasks"] = max(
                    stats["peak_distinct_tasks"], len(tasks_active)
                )
                if gate_expected[0] and len(tasks_active) >= gate_expected[0]:
                    gate_open.set()
            try:
                # Timeout is a real failure (incomplete HTTP body), not a PASS.
                if not gate_open.wait(timeout=25):
                    return
                with file_path.open("rb") as inp:
                    inp.seek(start)
                    try:
                        while remaining:
                            chunk = inp.read(min(64 * 1024, remaining))
                            if not chunk:
                                raise IOError("Source truncated mid-response")
                            # Per-connection throttling alone is insufficient:
                            # 16 Range sockets can finish one task while the
                            # other 15 remain active. Pace aggregate bytes for
                            # each task only in the strict gated workload.
                            if gate_expected[0]:
                                with task_pacer.slot(task_id) as wait:
                                    with guard:
                                        stats["paced_chunks"] += 1
                                        stats["paced_wait_seconds"] += wait
                                    self.wfile.write(chunk)
                                    self.wfile.flush()
                            else:
                                self.wfile.write(chunk)
                                self.wfile.flush()
                            remaining -= len(chunk)
                            with guard:
                                stats["bytes_sent"] += len(chunk)
                                task_bytes_sent[task_id] = (
                                    task_bytes_sent.get(task_id, 0) + len(chunk)
                                )
                            if args.chunk_delay_ms > 0:
                                time.sleep(args.chunk_delay_ms / 1000.0)
                    except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                        pass
            finally:
                with guard:
                    if remaining == 0:
                        task_get_completed[task_id] = task_get_completed.get(task_id, 0) + 1
                    else:
                        task_get_interrupted[task_id] = task_get_interrupted.get(task_id, 0) + 1
                    stats["active_requests"] -= 1
                    count = tasks_active[task_id] - 1
                    if count:
                        tasks_active[task_id] = count
                    else:
                        del tasks_active[task_id]

        def do_GET(self):
            self.serve_file(True)

        def do_HEAD(self):
            self.serve_file(False)

        def log_message(self, fmt, *values):
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    server.daemon_threads = True
    args.port_file.write_text(str(server.server_port), encoding="ascii")
    try:
        server.serve_forever(poll_interval=0.15)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
