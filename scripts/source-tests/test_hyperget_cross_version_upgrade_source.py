#!/usr/bin/env python3
"""Source contracts for HyperGet's cross-version Windows upgrade/rollback gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
NSIS = ROOT / "desktop/app/resources/installer/nsis-script-template.nsi"
VERSION_UTIL = ROOT / "buildSrc/src/main/kotlin/buildlogic/versioning/VersionUtil.kt"
SHARED_BUILD = ROOT / "shared/app/build.gradle.kts"
HARNESS = ROOT / "scripts/Run-WindowsCrossVersionUpgradeRollbackE2E.ps1"
WORKFLOW = ROOT / ".github/workflows/windows-cross-version-upgrade-rollback.yml"


class HyperGetCrossVersionUpgradeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.nsis = NSIS.read_text(encoding="utf-8")
        cls.version_util = VERSION_UTIL.read_text(encoding="utf-8")
        cls.shared_build = SHARED_BUILD.read_text(encoding="utf-8")
        cls.harness = HARNESS.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_internal_storage_and_package_identity_remain_stable(self):
        self.assertIn('return ".personal-download-manager"', self.version_util)
        self.assertIn('return "app.personaldownloadmanager"', self.version_util)
        self.assertIn('provider { getAppName() }', self.shared_build)
        self.assertIn('provider { getAppDataDirName() }', self.shared_build)
        self.assertIn('provider { "HyperGet" }', self.shared_build)

    def test_known_brand_shortcuts_are_cleaned_before_replacement(self):
        start = self.nsis.index("!macro RemoveKnownBrandShortcuts")
        end = self.nsis.index("!macroend", start)
        macro = self.nsis[start:end]
        for expected in (
            'Download Manager\\Download Manager.lnk',
            'HyperGet\\HyperGet.lnk',
            '$DESKTOP\\Download Manager.lnk',
            '$DESKTOP\\HyperGet.lnk',
        ):
            self.assertIn(expected, macro)
        install_section = self.nsis.index('Section "' + "$" + '{APP_DISPLAY_NAME}"')
        install_cleanup = self.nsis.index("!insertmacro RemoveKnownBrandShortcuts", install_section)
        install_clear = self.nsis.index("!insertmacro clearFiles", install_section)
        self.assertLess(install_cleanup, install_clear)
        uninstall_section = self.nsis.index('Section "Uninstall"')
        self.assertGreater(
            self.nsis.index("!insertmacro RemoveKnownBrandShortcuts", uninstall_section),
            uninstall_section,
        )

    def test_harness_refuses_to_touch_existing_profile_and_proves_real_state(self):
        for expected in (
            "RUNNER_TEMP",
            "Refusing to touch pre-existing user data",
            "Refusing to use pre-existing test root",
            "$script:TestTouchedInstaller = $true",
            "if ($testCreatedRoot -and (Test-Path -LiteralPath $root))",
            "Refusing to modify pre-existing product registry key",
            "settings','max-concurrent-downloads','--set','4",
            "download','add','http",
            "download','show',",
            "Finished",
            "UpgradeDataAndHistory=PASS",
            "RollbackDataAndHistory=PASS",
            "CandidateUninstallBeforeRollback=PASS",
            "NormalUninstallPreservesUserData=PASS",
            "BaselineSourceSHA=",
            "CandidateSourceSHA=",
            "BaselineInstallerSHA256=",
            "CandidateInstallerSHA256=",
        ):
            self.assertIn(expected, self.harness)

        # The exact base snapshot predates --status-lines; poll through its default table.
        self.assertIn("Invoke-AppCli -Arguments @('download','show',([string]$Id))", self.harness)
        self.assertNotIn("Arguments @('download','show','--status-lines'", self.harness)
        self.assertNotIn("Arguments @('download','show',([string]$Id),'--status-lines')", self.harness)
        self.assertIn(r"\x1B\[[0-?]*[ -/]*[@-~]", self.harness)
        self.assertIn("[│┃|]", self.harness)
        self.assertIn("Downloading|Finished|Error|Paused|PreparingFile|Resuming|Retrying", self.harness)

    def test_baseline_transfer_gate_reports_status_and_loopback_progress_on_failure(self):
        for expected in (
            "$script:FixtureStatsUrl = $null",
            "function Get-FixtureStatsSummary",
            "bytesSent={1}",
            "Baseline HTTP download created: id={0}",
            "Baseline download status: id={0}, state={1}",
            "Baseline download status was not observable for 30 seconds",
            "remained {0} after --start with zero fixture bytes",
            "lastStatus={1}",
            "lastProbe={2}",
            "file={3}",
            "fixture={4}",
        ):
            self.assertIn(expected, self.harness)

        self.assertIn(r"-split '\\r\\n|\\n|\\r'", self.harness)


    def test_status_parser_handles_wrapped_cells_without_crossing_into_another_download(self):
        for expected in (
            "$rowStartPattern = '^\\s*' + [regex]::Escape([string]$Id) + '(?:\\s|$)'",
            "$anyRowStartPattern = '^\\s*\\d+(?:\\s|$)'",
            "$insideTargetRow = $false",
            "if ($insideTargetRow -and $statusMatch.Success)",
            "if ($insideTargetRow -and $plain -match $anyRowStartPattern)",
            "$statusMatch = [regex]::Match($plain, $statusPattern)",
        ):
            self.assertIn(expected, self.harness)

        # The parser must be row-scoped: it may accept a wrapped status cell, but must stop
        # at the next numeric ID so a neighboring task can never satisfy this download.
        parser = self.harness[
            self.harness.index("function Read-DownloadStatus"):
            self.harness.index("function Wait-ForFinishedDownload")
        ]
        self.assertLess(parser.index("$insideTargetRow = $false"), parser.index("foreach ($plain in $normalizedLines)"))
        self.assertLess(parser.index("if ($insideTargetRow -and $statusMatch.Success)"), parser.index("if ($insideTargetRow -and $plain -match $anyRowStartPattern)"))


    def test_synthetic_wincred_survives_upgrade_rollback_and_uninstall_without_logging_secrets(self):
        for expected in (
            'PersonalDownloadManager/FTP/',
            'CredWriteW',
            'CredReadW',
            'CredDeleteW',
            'CredFree',
            'CredPersistLocalMachine = 2',
            "Assert-SyntheticWinCred -Stage 'baseline before upgrade'",
            "Assert-SyntheticWinCred -Stage 'upgrade to HyperGet'",
            "Assert-SyntheticWinCred -Stage 'candidate uninstall before rollback'",
            "Assert-SyntheticWinCred -Stage 'rollback to baseline'",
            "Assert-SyntheticWinCred -Stage 'normal uninstall'",
            'SyntheticWinCredPreservedAcrossUpgradeRollbackAndUninstall=PASS',
            'SyntheticWinCredCleanup=PASS',
        ):
            self.assertIn(expected, self.harness)

        self.assertIn('$url = "http://127.0.0.1:$port/fixture.bin"', self.harness)
        self.assertNotIn('fixture.bin?task=upgrade-rollback', self.harness)
        for line in self.harness.splitlines():
            if 'Write-Host' in line:
                self.assertNotIn('$script:WinCredPassword', line)
                self.assertNotIn('$script:WinCredReference', line)
                self.assertNotIn('$target', line.lower())

    def test_workflow_builds_both_exact_snapshots_without_publishing_exes(self):
        for expected in (
            "runs-on: windows-2022",
            "github.event.pull_request.base.sha",
            "github.event.pull_request.head.sha",
            "createReleaseFolderForCi",
            "Run-WindowsCrossVersionUpgradeRollbackE2E.ps1",
            "cross-version-upgrade-rollback.txt",
            "actions/upload-artifact",
        ):
            self.assertIn(expected, self.workflow)
        self.assertGreaterEqual(self.workflow.count("createReleaseFolderForCi"), 2)
        self.assertNotIn("gh release create", self.workflow.lower())
        upload_step = self.workflow[self.workflow.index("name: Upload cross-version acceptance proof"):]
        self.assertIn("cross-version-upgrade-rollback.txt", upload_step)
        self.assertNotIn("*.exe", upload_step.lower())
        self.assertNotIn("installer.exe", upload_step.lower())



    def test_windows_path_literals_use_single_backslashes(self):
        # PowerShell single-quoted strings do not use backslash escaping.
        # A doubled separator here is two characters and breaks [char] casts.
        self.assertIn("TrimEnd([char[]]@('\\','/'))", self.harness)
        self.assertIn("$uninstallKey = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\DownloadManager'", self.harness)
        self.assertIn("$appKey = 'HKCU:\\Software\\DownloadManager'", self.harness)
        self.assertIn("$startMenuRoot = Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs'", self.harness)
        self.assertNotIn("\\\\", self.harness)

if __name__ == "__main__":
    unittest.main(verbosity=2)
