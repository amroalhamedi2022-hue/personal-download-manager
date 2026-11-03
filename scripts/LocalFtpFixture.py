#!/usr/bin/env python3
"""Loopback-only deterministic FTP fixture for installed Windows acceptance.

Standard-library only. Implements the small passive/binary FTP subset used by
Apache Commons Net: authenticated USER/PASS, SIZE, MDTM, PASV/EPSV, REST and
RETR. It is deliberately not a general-purpose or internet-facing FTP server.
"""
import argparse
import json
import os
import socket
import socketserver
import threading
import time
from pathlib import Path


def replace_with_retry(source: Path, destination: Path, *, max_attempts: int = 40, retry_delay_seconds: float = 0.025) -> None:
    """Atomically publish fixture counters despite transient Windows readers.

    PowerShell acceptance polls can briefly hold the previous stats file open.
    Windows then rejects os.replace with PermissionError; retry only that
    transient sharing violation and surface a persistent failure unchanged.
    """
    if max_attempts < 1:
        raise ValueError("max_attempts must be >= 1")
    for attempt in range(max_attempts):
        try:
            os.replace(source, destination)
            return
        except PermissionError:
            if attempt + 1 >= max_attempts:
                raise
            time.sleep(retry_delay_seconds)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", type=Path, required=True)
    parser.add_argument("--port-file", type=Path, required=True)
    parser.add_argument("--stats-file", type=Path, required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password-env", required=True)
    parser.add_argument("--chunk-delay-ms", type=float, default=0.0)
    args = parser.parse_args()
    source = args.file.resolve(strict=True)
    if not source.is_file() or source.stat().st_size <= 0:
        parser.error("--file must be a non-empty regular file")
    if args.chunk_delay_ms < 0:
        parser.error("--chunk-delay-ms must be nonnegative")
    password = os.environ.get(args.password_env)
    if password is None:
        parser.error(f"environment variable {args.password_env!r} is missing")
    if not args.username or any(c in args.username for c in "\r\n\0"):
        parser.error("invalid fixture username")
    if any(c in password for c in "\r\n\0"):
        parser.error("invalid fixture password")

    expected_path = "/" + source.name
    # FTP clients differ on whether a URL path's leading slash is preserved in
    # SIZE/MDTM/RETR. Accept only the exact fixture name in either form; never
    # normalize arbitrary paths or permit traversal outside this one file.
    expected_paths = {source.name, expected_path}
    def is_fixture_path(value):
        return value in expected_paths
    stats_lock = threading.Lock()
    stats = {
        "control_connections": 0,
        "login_successes": 0,
        "login_failures": 0,
        "retrievals": 0,
        "active_retrievals": 0,
        "peak_active_retrievals": 0,
        "completed_retrievals": 0,
        "bytes_sent": 0,
        "rest_offsets": [],
    }

    def save_stats():
        temporary = args.stats_file.with_name(
            f".{args.stats_file.name}.{threading.get_ident()}.tmp"
        )
        temporary.write_text(json.dumps(stats, sort_keys=True), encoding="utf-8")
        try:
            replace_with_retry(temporary, args.stats_file)
        finally:
            temporary.unlink(missing_ok=True)

    def update_stats(update):
        with stats_lock:
            update(stats)
            save_stats()

    class FtpHandler(socketserver.StreamRequestHandler):
        authenticated = False
        username_seen = None
        restart_offset = 0
        passive = None

        def reply(self, line):
            self.wfile.write((line + "\r\n").encode("utf-8"))
            self.wfile.flush()

        def require_login(self):
            if self.authenticated:
                return True
            self.reply("530 Please login with USER and PASS")
            return False

        def close_passive(self):
            if self.passive is not None:
                try:
                    self.passive.close()
                finally:
                    self.passive = None

        def open_passive(self):
            self.close_passive()
            listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(("127.0.0.1", 0))
            listener.listen(1)
            listener.settimeout(15)
            self.passive = listener
            return listener.getsockname()[1]

        def handle(self):
            update_stats(lambda data: data.__setitem__(
                "control_connections", data["control_connections"] + 1
            ))
            self.reply("220 Personal Download Manager loopback FTP fixture")
            try:
                while True:
                    raw = self.rfile.readline(8193)
                    if not raw or len(raw) > 8192:
                        return
                    line = raw.decode("utf-8", errors="strict").rstrip("\r\n")
                    command, _, argument = line.partition(" ")
                    command = command.upper()
                    if command == "USER":
                        self.username_seen = argument
                        self.authenticated = False
                        self.reply("331 Password required")
                    elif command == "PASS":
                        if self.username_seen == args.username and argument == password:
                            self.authenticated = True
                            update_stats(lambda data: data.__setitem__(
                                "login_successes", data["login_successes"] + 1
                            ))
                            self.reply("230 Login successful")
                        else:
                            update_stats(lambda data: data.__setitem__(
                                "login_failures", data["login_failures"] + 1
                            ))
                            self.reply("530 Login incorrect")
                    elif command == "SYST":
                        self.reply("215 UNIX Type: L8")
                    elif command == "FEAT":
                        self.wfile.write(b"211-Features\r\n UTF8\r\n SIZE\r\n MDTM\r\n REST STREAM\r\n EPSV\r\n211 End\r\n")
                        self.wfile.flush()
                    elif command == "OPTS" and argument.upper() == "UTF8 ON":
                        self.reply("200 UTF8 enabled")
                    elif command == "TYPE" and argument.upper() == "I":
                        self.reply("200 Binary mode")
                    elif command == "PWD":
                        self.reply('257 "/" is the current directory')
                    elif command == "CWD":
                        self.reply("250 Directory changed")
                    elif command == "NOOP":
                        self.reply("200 NOOP")
                    elif command == "PASV":
                        if not self.require_login():
                            continue
                        port = self.open_passive()
                        self.reply(f"227 Entering Passive Mode (127,0,0,1,{port // 256},{port % 256})")
                    elif command == "EPSV":
                        if not self.require_login():
                            continue
                        port = self.open_passive()
                        self.reply(f"229 Entering Extended Passive Mode (|||{port}|)")
                    elif command == "SIZE":
                        if self.require_login():
                            self.reply(f"213 {source.stat().st_size}" if is_fixture_path(argument) else "550 File unavailable")
                    elif command == "MDTM":
                        if self.require_login():
                            self.reply("213 20261023000000" if is_fixture_path(argument) else "550 File unavailable")
                    elif command == "REST":
                        if not self.require_login():
                            continue
                        try:
                            offset = int(argument)
                            if offset < 0 or offset > source.stat().st_size:
                                raise ValueError
                        except ValueError:
                            self.reply("501 Invalid restart offset")
                        else:
                            self.restart_offset = offset
                            update_stats(lambda data: data["rest_offsets"].append(offset))
                            self.reply(f"350 Restarting at {offset}")
                    elif command == "RETR":
                        if not self.require_login():
                            continue
                        if not is_fixture_path(argument):
                            self.reply("550 File unavailable")
                            continue
                        if self.passive is None:
                            self.reply("425 Use PASV or EPSV first")
                            continue
                        listener, self.passive = self.passive, None
                        offset = self.restart_offset
                        self.restart_offset = 0
                        self.reply(f"150 Opening binary data connection ({source.stat().st_size - offset} bytes)")
                        completed = False
                        def retrieval_started(data):
                            data["retrievals"] += 1
                            data["active_retrievals"] += 1
                            data["peak_active_retrievals"] = max(
                                data["peak_active_retrievals"], data["active_retrievals"]
                            )
                        update_stats(retrieval_started)
                        try:
                            data_socket, _ = listener.accept()
                            with data_socket, source.open("rb") as stream:
                                stream.seek(offset)
                                while True:
                                    chunk = stream.read(64 * 1024)
                                    if not chunk:
                                        completed = True
                                        break
                                    data_socket.sendall(chunk)
                                    update_stats(lambda data, size=len(chunk): data.__setitem__(
                                        "bytes_sent", data["bytes_sent"] + size
                                    ))
                                    if args.chunk_delay_ms:
                                        time.sleep(args.chunk_delay_ms / 1000.0)
                        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.timeout):
                            pass
                        finally:
                            listener.close()
                            update_stats(lambda data: data.__setitem__(
                                "active_retrievals", data["active_retrievals"] - 1
                            ))
                        if completed:
                            update_stats(lambda data: data.__setitem__(
                                "completed_retrievals", data["completed_retrievals"] + 1
                            ))
                            self.reply("226 Transfer complete")
                        else:
                            self.reply("426 Connection closed; transfer aborted")
                    elif command == "QUIT":
                        self.reply("221 Goodbye")
                        return
                    else:
                        self.reply("502 Command not implemented")
            finally:
                self.close_passive()

    class LoopbackFtpServer(socketserver.ThreadingTCPServer):
        allow_reuse_address = True
        daemon_threads = True

    save_stats()
    with LoopbackFtpServer(("127.0.0.1", 0), FtpHandler) as server:
        args.port_file.write_text(str(server.server_address[1]), encoding="ascii")
        server.serve_forever(poll_interval=0.1)


if __name__ == "__main__":
    main()
