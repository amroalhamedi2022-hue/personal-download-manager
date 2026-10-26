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
