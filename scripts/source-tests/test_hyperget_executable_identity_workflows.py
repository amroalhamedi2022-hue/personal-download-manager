"""Source contracts for the Windows HyperGet.exe migration across installed acceptance harnesses.

These checks keep the renamed GUI launcher distinct from intentionally stable
DownloadManagerCli.exe and DownloadManagerNativeMessagingHost.exe identities.
Actual Windows install/launch/download jobs remain required.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ACTIVE = (ROOT / ".github/workflows/windows-installed-active-benchmark.yml").read_text(encoding="utf-8")
MEMORY = (ROOT / ".github/workflows/windows-installed-memory-profile.yml").read_text(encoding="utf-8")
HTTP = (ROOT / ".github/workflows/windows-installed-http-e2e.yml").read_text(encoding="utf-8")
FTP = (ROOT / ".github/workflows/windows-installed-ftp-e2e.yml").read_text(encoding="utf-8")
UI = (ROOT / ".github/workflows/windows-installed-ui-smoke.yml").read_text(encoding="utf-8")
NATIVE = (ROOT / ".github/workflows/windows-installed-native-host-e2e.yml").read_text(encoding="utf-8")
MATRIX = (ROOT / "scripts/Run-WindowsActiveBenchmarkMatrix.ps1").read_text(encoding="utf-8")
FTP_SCRIPT = (ROOT / "scripts/Run-WindowsInstalledFtpE2E.ps1").read_text(encoding="utf-8")


class HyperGetExecutableIdentityWorkflowContracts(unittest.TestCase):
    def test_all_current_candidate_installers_select_only_hyperget_gui(self):
        for name, workflow in (("active benchmark", ACTIVE), ("memory profile", MEMORY), ("HTTP E2E", HTTP), ("FTP E2E", FTP)):
            with self.subTest(workflow=name):
                self.assertIn("BaseName -eq 'HyperGet'", workflow)
                self.assertIn("BaseName -eq 'DownloadManager'", workflow)  # explicit legacy-GUI rejection
                self.assertIn("DownloadManagerCli$", workflow)  # CLI stays backward-compatible
        self.assertIn("Join-Path $installed 'HyperGet.exe'", HTTP)
        self.assertIn("HyperGet.exe", NATIVE)
        self.assertIn("DownloadManagerNativeMessagingHost.exe", NATIVE)

    def test_process_tree_sampler_uses_the_actual_gui_executable_name(self):
        self.assertIn("$rootProcessName = [IO.Path]::GetFileNameWithoutExtension($guiPath)", MATRIX)
        self.assertIn("$rootProcessName -notin @('DownloadManager', 'HyperGet')", MATRIX)
        self.assertIn("Get-Process -Name $rootProcessName", MATRIX)
        self.assertIn("-RootProcessName $rootProcessName", MATRIX)
        self.assertNotIn("Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue", MATRIX)
        self.assertIn("-RootProcessName HyperGet", UI)
        self.assertNotIn("-RootProcessName DownloadManager", UI)
        self.assertIn("Get-Process -Name 'DownloadManager','HyperGet'", FTP_SCRIPT)
        self.assertIn("'HyperGet.exe'", FTP_SCRIPT)

    def test_identity_changes_trigger_the_installed_acceptance_jobs(self):
        for name, workflow in (("active benchmark", ACTIVE), ("memory profile", MEMORY), ("HTTP E2E", HTTP), ("FTP E2E", FTP), ("GUI smoke", UI)):
            with self.subTest(workflow=name):
                self.assertIn("desktop/app/resources/installer/nsis-script-template.nsi", workflow)
                self.assertIn("desktop/app/build.gradle.kts", workflow)
                self.assertIn("scripts/source-tests/test_hyperget_executable_identity_workflows.py", workflow)

    def test_http_and_ftp_acceptance_use_exact_pr_head(self):
        self.assertIn("pull_request:", HTTP)
        self.assertIn("HYPERGET_TESTED_SHA: ${{ github.event.pull_request.head.sha || github.sha }}", HTTP)
        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", HTTP)
        self.assertIn("-SourceSha $env:HYPERGET_TESTED_SHA", HTTP)
        self.assertIn("HYPERGET_TESTED_SHA: ${{ github.event.pull_request.head.sha || github.sha }}", FTP)
        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", FTP)


if __name__ == "__main__":
    unittest.main()
