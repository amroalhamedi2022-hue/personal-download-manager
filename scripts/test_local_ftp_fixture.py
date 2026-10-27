import ftplib
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from io import BytesIO
from pathlib import Path
from unittest import mock

from scripts.LocalFtpFixture import replace_with_retry


class LocalFtpFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.source = root / "fixture [safe].bin"
        self.source.write_bytes(bytes(range(256)) * 4096)
        self.port_file = root / "port.txt"
        self.stats_file = root / "stats.json"
        environment = os.environ.copy()
        environment["FIXTURE_TEST_PASSWORD"] = "test-password"
        self.server = subprocess.Popen(
            [
                sys.executable,
                str(Path(__file__).with_name("LocalFtpFixture.py")),
                "--file", str(self.source),
                "--port-file", str(self.port_file),
                "--stats-file", str(self.stats_file),
                "--username", "test-user",
                "--password-env", "FIXTURE_TEST_PASSWORD",
            ],
            env=environment,
        )
        for _ in range(100):
            if self.port_file.exists():
                break
            time.sleep(0.02)
        self.assertTrue(self.port_file.exists())
        self.port = int(self.port_file.read_text(encoding="ascii"))

    def tearDown(self):
        self.server.terminate()
        self.server.wait(timeout=5)
        self.temp.cleanup()

    def connect(self, password="test-password"):
        ftp = ftplib.FTP()
        ftp.connect("127.0.0.1", self.port, timeout=5)
        ftp.login("test-user", password)
        ftp.voidcmd("TYPE I")
        return ftp

    def test_atomic_stats_replace_retries_transient_windows_sharing_violation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            temporary = root / "stats.json.tmp"
            destination = root / "stats.json"
            temporary.write_text('{"active_retrievals": 0}', encoding="utf-8")
            destination.write_text('{"active_retrievals": 1}', encoding="utf-8")
            real_replace = os.replace
            attempts = 0

            def fail_twice_then_replace(source, target):
                nonlocal attempts
                attempts += 1
                if attempts < 3:
                    raise PermissionError("synthetic Windows sharing violation")
                real_replace(source, target)

            with mock.patch("scripts.LocalFtpFixture.os.replace", side_effect=fail_twice_then_replace):
                replace_with_retry(temporary, destination, max_attempts=4, retry_delay_seconds=0)

            self.assertEqual(3, attempts)
            self.assertEqual('{"active_retrievals": 0}', destination.read_text(encoding="utf-8"))
            self.assertFalse(temporary.exists())

    def test_authenticated_size_mdtm_and_exact_binary_retrieval(self):
        with self.connect() as ftp:
            self.assertEqual(self.source.stat().st_size, ftp.size("/" + self.source.name))
            self.assertEqual("213 20261023000000", ftp.sendcmd("MDTM /" + self.source.name))
            received = BytesIO()
            ftp.retrbinary("RETR /" + self.source.name, received.write)
        self.assertEqual(self.source.read_bytes(), received.getvalue())

    def test_wrong_password_is_rejected_without_retrieval(self):
        ftp = ftplib.FTP()
        ftp.connect("127.0.0.1", self.port, timeout=5)
        with self.assertRaises(ftplib.error_perm):
            ftp.login("test-user", "wrong")
        ftp.close()
        stats = json.loads(self.stats_file.read_text(encoding="utf-8"))
        self.assertEqual(1, stats["login_failures"])
        self.assertEqual(0, stats["retrievals"])

    def test_rest_resumes_at_the_requested_offset(self):
        offset = 131072
        with self.connect() as ftp:
            received = BytesIO()
            ftp.retrbinary("RETR /" + self.source.name, received.write, rest=offset)
        self.assertEqual(self.source.read_bytes()[offset:], received.getvalue())
        stats = json.loads(self.stats_file.read_text(encoding="utf-8"))
        self.assertIn(offset, stats["rest_offsets"])
        self.assertEqual(1, stats["completed_retrievals"])
        self.assertEqual(0, stats["active_retrievals"])
        self.assertEqual(1, stats["peak_active_retrievals"])


if __name__ == "__main__":
    unittest.main()
