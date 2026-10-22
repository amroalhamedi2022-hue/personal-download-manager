#!/usr/bin/env python3
"""Loopback-only real HTTP Range fixture for installed Windows e2e tests.

Uses the Python standard library; no external network, credentials or user files.
"""
import argparse
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

RANGE = re.compile(r"^bytes=(\d+)-(\d*)$")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", type=Path, required=True)
    ap.add_argument("--port-file", type=Path, required=True)
    ap.add_argument("--chunk-delay-ms", type=float, default=15.0)
    args = ap.parse_args()
    file_path = args.file.resolve(strict=True)
    if not file_path.is_file():
        raise ValueError("Fixture source must be a regular file")
    size = file_path.stat().st_size
    if size <= 0:
        raise ValueError("Fixture source must not be empty")

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        def serve_file(self, send_body):
            if self.path.split("?", 1)[0] != "/fixture.bin":
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
                        if args.chunk_delay_ms > 0:
                            time.sleep(args.chunk_delay_ms / 1000.0)
                except (BrokenPipeError, ConnectionResetError):
                    pass

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
