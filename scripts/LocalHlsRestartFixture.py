#!/usr/bin/env python3
"""Deterministic loopback-only HLS fixture for installed process-kill/relaunch acceptance."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time
from urllib.parse import urlsplit

SEGMENT_SIZES = {1: 128 * 1024, 2: 8 * 1024 * 1024, 3: 8 * 1024 * 1024, 4: 8 * 1024 * 1024}
CHUNK_SIZE = 32 * 1024


def payload_for(segment_id: int, size: int) -> bytes:
    block = bytes(((offset * 17 + segment_id * 29) % 251 for offset in range(65536)))
    return (block * ((size + len(block) - 1) // len(block)))[:size]


class FixtureState:
    def __init__(self, stats_path: Path) -> None:
        self.stats_path = stats_path
        self.lock = threading.RLock()
        self.playlist_requests = 0
        self.active_segment_requests = 0
        self.total_segment_requests = 0
        self.bytes_sent = 0
        self.chunk_count = 0
        self.segment_requests = {i: 0 for i in SEGMENT_SIZES}
        self.segment_completed = {i: 0 for i in SEGMENT_SIZES}
        self.segment_aborted = {i: 0 for i in SEGMENT_SIZES}
        self.persist()

    def snapshot_locked(self) -> dict[str, int]:
        result = {
            "playlist_requests": self.playlist_requests,
            "active_segment_requests": self.active_segment_requests,
            "total_segment_requests": self.total_segment_requests,
            "bytes_sent": self.bytes_sent,
        }
        for segment_id in SEGMENT_SIZES:
            result[f"segment{segment_id}_requests"] = self.segment_requests[segment_id]
            result[f"segment{segment_id}_completed"] = self.segment_completed[segment_id]
            result[f"segment{segment_id}_aborted"] = self.segment_aborted[segment_id]
        return result

    def snapshot(self) -> dict[str, int]:
        with self.lock:
            return self.snapshot_locked()

    def persist(self) -> None:
        with self.lock:
            data = json.dumps(self.snapshot_locked(), sort_keys=True, indent=2)
            temp = self.stats_path.with_name(
                self.stats_path.name + f".{threading.get_ident()}.tmp"
            )
            temp.write_text(data + "\n", encoding="utf-8")
            os.replace(temp, self.stats_path)

    def playlist_requested(self) -> None:
        with self.lock:
            self.playlist_requests += 1
        self.persist()

    def begin_segment(self, segment_id: int) -> None:
        with self.lock:
            self.total_segment_requests += 1
            self.segment_requests[segment_id] += 1
            self.active_segment_requests += 1
        self.persist()

    def sent(self, byte_count: int) -> None:
        with self.lock:
            self.bytes_sent += byte_count
            self.chunk_count += 1
            should_persist = self.chunk_count % 8 == 0
        if should_persist:
            self.persist()

    def complete_segment(self, segment_id: int) -> None:
        with self.lock:
            self.segment_completed[segment_id] += 1
        self.persist()

    def abort_segment(self, segment_id: int) -> None:
        with self.lock:
            self.segment_aborted[segment_id] += 1
        self.persist()

    def end_segment(self) -> None:
        with self.lock:
            self.active_segment_requests -= 1
        self.persist()


def make_handler(state: FixtureState, segment_paths: dict[int, Path], playlist: bytes, delay_ms: int):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, _format: str, *_args: object) -> None:
            return

        def _send_bytes(self, body: bytes, content_type: str) -> None:
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()
            self.close_connection = True

        def do_GET(self) -> None:
            path = urlsplit(self.path).path
            if path == "/stats":
                body = json.dumps(state.snapshot(), sort_keys=True).encode("utf-8")
                self._send_bytes(body, "application/json")
                return
            if path == "/playlist.m3u8":
                state.playlist_requested()
                self._send_bytes(playlist, "application/vnd.apple.mpegurl")
                return
            segment_id = None
            for candidate in segment_paths:
                if path == f"/segments/segment-{candidate}.ts":
                    segment_id = candidate
                    break
            if segment_id is None:
                self.send_error(404)
                self.close_connection = True
                return

            state.begin_segment(segment_id)
            try:
                source = segment_paths[segment_id]
                self.send_response(200)
                self.send_header("Content-Type", "video/mp2t")
                self.send_header("Content-Length", str(source.stat().st_size))
                self.send_header("Connection", "close")
                self.end_headers()
                with source.open("rb") as stream:
                    while True:
                        chunk = stream.read(CHUNK_SIZE)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        self.wfile.flush()
                        state.sent(len(chunk))
                        if delay_ms > 0:
                            time.sleep(delay_ms / 1000.0)
                state.complete_segment(segment_id)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                state.abort_segment(segment_id)
            finally:
                state.end_segment()
                self.close_connection = True

    return Handler


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--port-file", required=True)
    parser.add_argument("--stats-file", required=True)
    parser.add_argument("--chunk-delay-ms", type=int, default=40)
    args = parser.parse_args()

    work_dir = Path(args.work_dir).resolve()
    work_dir.mkdir(parents=True, exist_ok=True)
    segment_dir = work_dir / "segments"
    segment_dir.mkdir(parents=True, exist_ok=True)
    stats_path = Path(args.stats_file).resolve()
    stats_path.parent.mkdir(parents=True, exist_ok=True)

    segment_paths: dict[int, Path] = {}
    expected_hash = hashlib.sha256()
    expected_bytes = 0
    for segment_id, size in SEGMENT_SIZES.items():
        path = segment_dir / f"segment-{segment_id}.ts"
        payload = payload_for(segment_id, size)
        path.write_bytes(payload)
        segment_paths[segment_id] = path
        expected_hash.update(payload)
        expected_bytes += len(payload)

    expected = {
        "bytes": expected_bytes,
        "sha256": expected_hash.hexdigest(),
        "segment_sizes": {str(k): v for k, v in SEGMENT_SIZES.items()},
    }
    (work_dir / "expected.json").write_text(
        json.dumps(expected, sort_keys=True, indent=2) + "\n", encoding="utf-8"
    )

    playlist_lines = [
        "#EXTM3U",
        "#EXT-X-VERSION:3",
        "#EXT-X-TARGETDURATION:11",
        "#EXT-X-MEDIA-SEQUENCE:1",
    ]
    for segment_id in SEGMENT_SIZES:
        playlist_lines.extend(["#EXTINF:10.0,", f"/segments/segment-{segment_id}.ts"])
    playlist_lines.append("#EXT-X-ENDLIST")
    playlist = ("\n".join(playlist_lines) + "\n").encode("utf-8")

    state = FixtureState(stats_path)
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(
        state, segment_paths, playlist, max(0, args.chunk_delay_ms)
    ))
    server.daemon_threads = True
    server.allow_reuse_address = True
    port_file = Path(args.port_file).resolve()
    port_file.parent.mkdir(parents=True, exist_ok=True)
    temp_port = port_file.with_name(port_file.name + ".tmp")
    temp_port.write_text(str(server.server_address[1]), encoding="ascii")
    os.replace(temp_port, port_file)
    print(f"READY loopback HLS fixture port={server.server_address[1]}", flush=True)
    try:
        server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
