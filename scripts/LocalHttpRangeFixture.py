#!/usr/bin/env python3
"""Deterministic loopback HTTP Range fixture for installed Windows tests.

Standard-library only. Supports real HEAD, GET, 206 and 416; serves exactly
one fixture. /stats is a loopback-only test endpoint used to PROVE how many
distinct tasks actually transferred concurrently (not merely queued).
"""
import argparse
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

RANGE = re.compile(r"^bytes=(\d+)-(\d*)$")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", type=Path, required=True)
    ap.add_argument("--port-file", type=Path, required=True)
    ap.add_argument("--chunk-delay-ms", type=float, default=15.0)
    args = ap.parse_args()
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
    }
    tasks_active = {}
    tasks_seen = set()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def stats_request(self, query):
            reset = parse_qs(query).get("reset") == ["1"]
            with guard:
                if reset and stats["active_requests"]:
                    self.send_error(409, "Transfers still active")
                    return
                if reset:
                    for key in stats:
                        stats[key] = 0
                    tasks_active.clear()
                    tasks_seen.clear()
                snapshot = dict(stats)
                snapshot["active_distinct_tasks"] = len(tasks_active)
                snapshot["unique_tasks_seen"] = len(tasks_seen)
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
                stats["active_requests"] += 1
                stats["total_requests"] += 1
                tasks_seen.add(task_id)
                tasks_active[task_id] = tasks_active.get(task_id, 0) + 1
                stats["peak_requests"] = max(stats["peak_requests"], stats["active_requests"])
                stats["peak_distinct_tasks"] = max(
                    stats["peak_distinct_tasks"], len(tasks_active)
                )
            try:
                with file_path.open("rb") as inp:
                    inp.seek(start)
                    try:
                        while remaining:
                            chunk = inp.read(min(64 * 1024, remaining))
                            if not chunk:
                                raise IOError("Source truncated mid-response")
                            self.wfile.write(chunk)
                            self.wfile.flush()
                            remaining -= len(chunk)
                            with guard:
                                stats["bytes_sent"] += len(chunk)
                            if args.chunk_delay_ms > 0:
                                time.sleep(args.chunk_delay_ms / 1000.0)
                    except (BrokenPipeError, ConnectionResetError):
                        pass
            finally:
                with guard:
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
