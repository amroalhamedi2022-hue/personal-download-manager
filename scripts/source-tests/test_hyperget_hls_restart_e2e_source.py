"""Source guard for the installed HyperGet HLS process-kill/relaunch acceptance gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = (ROOT / "scripts/LocalHlsRestartFixture.py").read_text(encoding="utf-8")
SCRIPT = (ROOT / "scripts/Run-WindowsInstalledHlsRestartE2E.ps1").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/hyperget-hls-redirect-milestone.yml").read_text(encoding="utf-8")


class HyperGetHlsRestartSourceContract(unittest.TestCase):
    def test_fixture_is_deterministic_and_loopback_only(self):
        self.assertIn('ThreadingHTTPServer(("127.0.0.1", 0)', FIXTURE)
        self.assertIn("#EXTM3U", FIXTURE)
        self.assertIn("/playlist.m3u8", FIXTURE)
        self.assertIn("/segments/segment-", FIXTURE)
        self.assertIn("segment1_completed", FIXTURE)
        self.assertIn("active_segment_requests", FIXTURE)
        self.assertIn("expected_hash.hexdigest()", FIXTURE)
        self.assertNotIn("https://", FIXTURE)

    def test_installed_scenario_kills_and_relaunches_real_gui(self):
        self.assertIn("download add hls", SCRIPT)
        self.assertIn("Stop-HyperGetGui -ProcessToStop $gui", SCRIPT)
        self.assertIn("download show --status-lines", SCRIPT)
        self.assertIn("download resume $taskId", SCRIPT)
        self.assertIn("playlist_requests -lt 2", SCRIPT)
        self.assertIn("segment1_requests -ne 1", SCRIPT)
        self.assertIn("active_segment_requests", SCRIPT)
        self.assertIn("Get-FileHash -LiteralPath $finalPath -Algorithm SHA256", SCRIPT)
        self.assertIn("Result=PASS", SCRIPT)

    def test_runtime_gate_builds_only_after_source_and_junit_gates(self):
        self.assertIn("runs-on: windows-2022", WORKFLOW)
        self.assertIn("needs: guarded-redirect-unit", WORKFLOW)
        self.assertIn("test_hyperget_hls_restart_e2e_source.py", WORKFLOW)
        self.assertIn("Run-WindowsInstalledHlsRestartE2E.ps1", WORKFLOW)
        self.assertIn("createReleaseFolderForCi", WORKFLOW)
        self.assertLess(
            WORKFLOW.index("Validate HLS restart source contract and script syntax before any build"),
            WORKFLOW.index("Build one Windows installer for process-restart acceptance"),
        )
        self.assertIn("workflow_dispatch:", WORKFLOW)
        self.assertIn("retention-days: 7", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
