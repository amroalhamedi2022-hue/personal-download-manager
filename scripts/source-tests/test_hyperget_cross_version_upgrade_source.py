#!/usr/bin/env python3
"""Source contracts for HyperGet's cross-version Windows upgrade/rollback gate."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
NSIS = ROOT / "desktop/app/resources/installer/nsis-script-template.nsi"
VERSION_UTIL = ROOT / "buildSrc/src/main/kotlin/buildlogic/versioning/VersionUtil.kt"
SHARED_BUILD = ROOT / "shared/app/build.gradle.kts"
DESKTOP_BUILD = ROOT / "desktop/app/build.gradle.kts"
HARNESS = ROOT / "scripts/Run-WindowsCrossVersionUpgradeRollbackE2E.ps1"
WORKFLOW = ROOT / ".github/workflows/windows-cross-version-upgrade-rollback.yml"


class HyperGetCrossVersionUpgradeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.nsis = NSIS.read_text(encoding="utf-8")
        cls.version_util = VERSION_UTIL.read_text(encoding="utf-8")
        cls.shared_build = SHARED_BUILD.read_text(encoding="utf-8")
        cls.desktop_build = DESKTOP_BUILD.read_text(encoding="utf-8")
        cls.harness = HARNESS.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")

    def test_internal_storage_and_package_identity_remain_stable(self):
        self.assertIn('return ".personal-download-manager"', self.version_util)
        self.assertIn('return "app.personaldownloadmanager"', self.version_util)
        self.assertIn('provider { getAppName() }', self.shared_build)
        self.assertIn('provider { getAppDataDirName() }', self.shared_build)
        self.assertIn('provider { "HyperGet" }', self.shared_build)
        # The packaged installer display name must follow HyperGet while technical IDs remain legacy-compatible.
        self.assertIn('return "HyperGet"', self.version_util)
        self.assertNotIn('return "Download Manager"', self.version_util)
        self.assertIn("appDisplayName = getPrettifiedAppName()", self.desktop_build)
        self.assertIn('!define APP_DISPLAY_NAME "{{ app_display_name }}"', self.nsis)
        self.assertIn('WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayName" "${APP_DISPLAY_NAME}"', self.nsis)

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

    def test_uninstaller_pins_per_user_context_and_cleans_registration_before_self_removal(self):
        init_start = self.nsis.index("Function .onInit")
        init_end = self.nsis.index("FunctionEnd", init_start)
        self.assertIn("SetShellVarContext current", self.nsis[init_start:init_end])

        uninstall_start = self.nsis.index('Section "Uninstall"')
        uninstall_section = self.nsis[uninstall_start:]
        self.assertIn("SetShellVarContext current", uninstall_section)
        commands = (
            "SetShellVarContext current",
            "!insertmacro RemoveStartMenu",
            "!insertmacro RemoveDesktopShortCut",
            "!insertmacro RemoveKnownBrandShortcuts",
            'DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"',
            'DeleteRegKey SHCTX "${REG_APP_KEY}"',
            'DeleteRegValue SHCTX "${REG_RUN_KEY}" "${APP_NAME}"',
            "!insertmacro clearFiles",
        )
        positions = [uninstall_section.index(command) for command in commands]
        self.assertEqual(positions, sorted(positions))
        self.assertEqual(uninstall_section.count("!insertmacro clearFiles"), 1)

    def test_harness_refuses_to_touch_existing_profile_and_proves_real_state(self):
        for expected in (
            "RUNNER_TEMP",
            "Refusing to touch pre-existing user data",
            "Refusing to use pre-existing test root",
            "$script:TestTouchedInstaller = $true",
            "if ($testCreatedRoot -and (Test-Path -LiteralPath $root))",
            "Refusing to modify pre-existing product registry key",
            "settings','max-concurrent-downloads','--set','4",
            "$script:CurrentCliPath download add http --link $url --folder $downloadDir --name $downloadName --start",
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

        self.assertIn("Loopback URL preflight PASS: host=127.0.0.1 port=", self.harness)
        self.assertIn("[System.Uri]::new($url, [System.UriKind]::Absolute)", self.harness)
        self.assertIn("$addExitCode = $LASTEXITCODE", self.harness)

        # The exact base snapshot predates --status-lines; poll through its default table.
        self.assertIn("Invoke-AppCli -Arguments @('download','show',([string]$Id))", self.harness)
        self.assertNotIn("Arguments @('download','show','--status-lines'", self.harness)
        self.assertNotIn("Arguments @('download','show',([string]$Id),'--status-lines')", self.harness)
        self.assertIn(r"\x1B\[[0-?]*[ -/]*[@-~]", self.harness)
        self.assertIn("[│┃|]", self.harness)
        self.assertIn(r"$statusPattern = '^(?<status>Downloading|Finished|Finish(?:[?…�])?|Error|Paused|PreparingFile|Resuming|Retrying)(?=$|[\s?])'", self.harness)
        self.assertIn("$statusText = [regex]::Replace($plain, $rowStartPattern, '')", self.harness)
        self.assertIn("$statusText = $statusText.TrimStart([char[]]@(' ', '?'))", self.harness)
        self.assertIn("$plain.Trim([char[]]@(' ', '?'))", self.harness)
        self.assertIn("if ($status -like 'Finish*') { $status = 'Finished' }", self.harness)

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

        self.assertIn(r"-split '\r\n|\n|\r'", self.harness)
        self.assertIn("no matching row; lineCount=", self.harness)
        self.assertIn("$normalizedLines | Select-Object -First 8", self.harness)
        self.assertIn("[redacted]", self.harness)
        self.assertIn("'[URL]'", self.harness)
        self.assertIn("'[PATH]'", self.harness)
        self.assertIn("$safeLine.Length -gt 120", self.harness)


    def test_status_parser_handles_wrapped_cells_without_crossing_into_another_download(self):
        for expected in (
            "$rowStartPattern = '^\s*' + [regex]::Escape([string]$Id) + '(?:\s|$)'",
            "$anyRowStartPattern = '^\s*\\d+(?:\s|$)'",
            "$insideTargetRow = $false",
            "if ($insideTargetRow) {",
            "if ($insideTargetRow -and $plain -match $anyRowStartPattern)",
            "$statusMatch = [regex]::Match($statusText, $statusPattern)",
            "$statusMatch = [regex]::Match($wrappedText, $statusPattern)",
        ):
            self.assertIn(expected, self.harness)

        # The parser must be row-scoped: it may accept a wrapped status cell, but must stop
        # at the next numeric ID so a neighboring task can never satisfy this download.
        parser = self.harness[
            self.harness.index("function Read-DownloadStatus"):
            self.harness.index("function Wait-ForFinishedDownload")
        ]
        self.assertLess(parser.index("$insideTargetRow = $false"), parser.index("foreach ($plain in $normalizedLines)"))
        self.assertLess(parser.index("if ($insideTargetRow -and $plain -match $anyRowStartPattern)"), parser.index("if ($insideTargetRow) {"))


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

    def test_unrelated_start_menu_content_survives_upgrade_uninstall_and_rollback(self):
        for expected in (
            "$brandedStartMenuFolder = Join-Path $startMenuRoot 'HyperGet'",
            "$startMenuSentinel = Join-Path $brandedStartMenuFolder 'unrelated-user-content.sentinel'",
            "if (Test-Path -LiteralPath $brandedStartMenuFolder)",
            "function Assert-StartMenuSentinel([string] $Label)",
            "Assert-StartMenuSentinel -Label 'upgrade to HyperGet'",
            "Assert-StartMenuSentinel -Label 'candidate uninstall before rollback'",
            "Assert-StartMenuSentinel -Label 'rollback to baseline'",
            "Assert-StartMenuSentinel -Label 'normal uninstall'",
            "'StartMenuUserContentSentinelPreserved=PASS'",
            "$script:StartMenuFolderCreated = $false",
            "Removed only the test-owned Start Menu sentinel after exact content and SHA-256 verification.",
        ):
            self.assertIn(expected, self.harness)
        self.assertNotIn("Remove-Item -LiteralPath $brandedStartMenuFolder -Recurse", self.harness)

        macro_start = self.nsis.index("!macro RemoveStartMenu")
        macro_end = self.nsis.index("!macroend", macro_start)
        macro = self.nsis[macro_start:macro_end]
        self.assertIn('Delete "$SMPROGRAMS\\${APP_DISPLAY_NAME}\\${APP_DISPLAY_NAME}.lnk"', macro)
        self.assertIn('RMDir "$SMPROGRAMS\\${APP_DISPLAY_NAME}"', macro)
        self.assertNotIn("RMDir /r", macro)

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

    def test_narrow_baseline_table_finish_truncation_is_normalized(self):
        # Reproduce the Windows transcript shape: "0 ? Finish? cross-version-upgrade...".
        pattern = re.compile(
            r"^(?P<status>Downloading|Finished|Finish(?:[?…�])?|Error|Paused|PreparingFile|Resuming|Retrying)(?=$|[\s?])"
        )
        cases = {
            "Finished cross-version-upgrade.bin": "Finished",
            "Finish? cross-version-upgrade.bin": "Finished",
            "Finish… cross-version-upgrade.bin": "Finished",
            "Downloading cross-version-upgrade.bin": "Downloading",
            "Error cross-version-upgrade.bin": "Error",
        }
        for cell, expected in cases.items():
            with self.subTest(cell=cell):
                match = pattern.match(cell)
                self.assertIsNotNone(match)
                status = match.group("status")
                if status.startswith("Finish"):
                    status = "Finished"
                self.assertEqual(status, expected)

        self.assertIsNone(pattern.match("FinishAgain cross-version-upgrade.bin"))

        # The Windows table's outer border was decoded as '?' before the numeric ID.
        row = "? 0 ? Finish? cross-version-upgrade? "
        normalized = row.strip(" ?")
        self.assertRegex(normalized, r"^\s*0(?:\s|$)")
        status_text = re.sub(r"^\s*0(?:\s|$)", "", normalized).lstrip(" ?")
        match = pattern.match(status_text)
        self.assertIsNotNone(match)
        self.assertEqual("Finished" if match.group("status").startswith("Finish") else match.group("status"), "Finished")
        self.assertIn("fixed-width table can truncate Finished to Finish", self.harness)

    def test_observed_wrapped_windows_border_row_and_neighbor_boundary(self):
        # Exact safe shape captured in the failed Windows transcript:
        # the outer border became '?' and the narrow table clipped Finished to Finish?.
        status_pattern = re.compile(
            r"^(?P<status>Downloading|Finished|Finish(?:[?…�])?|Error|Paused|PreparingFile|Resuming|Retrying)(?=$|[\s?])"
        )

        def parse_status(raw_lines, target_id):
            normalized = [line.strip(" ?") for line in raw_lines if line.strip()]
            row_start = re.compile(r"^\s*" + re.escape(str(target_id)) + r"(?:\s|$)")
            any_row_start = re.compile(r"^\s*\d+(?:\s|$)")
            inside_target = False
            for line in normalized:
                if row_start.match(line):
                    status_text = row_start.sub("", line, count=1).lstrip(" ?")
                    match = status_pattern.match(status_text)
                    if match:
                        status = match.group("status")
                        return "Finished" if status.startswith("Finish") else status
                    inside_target = True
                    continue
                if inside_target and any_row_start.match(line):
                    inside_target = False
                if inside_target:
                    match = status_pattern.match(line.lstrip(" ?"))
                    if match:
                        status = match.group("status")
                        return "Finished" if status.startswith("Finish") else status
            return None

        self.assertIn("$plain.Trim([char[]]@(' ', '?'))", self.harness)
        self.assertEqual(parse_status(["? 0 ? Finish?", "cross-version-upgrade? [PATH]"], 0), "Finished")
        # A neighboring task's Finished cell must not satisfy the requested ID.
        self.assertIsNone(parse_status(["? 0 ?", "????????", "? 1 ? Finished other.bin?"], 0))

        parser = self.harness[
            self.harness.index("function Read-DownloadStatus"):
            self.harness.index("function Wait-ForFinishedDownload")
        ]
        self.assertLess(
            parser.index("if ($insideTargetRow -and $plain -match $anyRowStartPattern)"),
            parser.index("if ($insideTargetRow) {"),
        )


    def test_uninstall_display_version_uses_full_semver_but_internal_version_stays_numeric(self):
        # The Windows installer receives both values: appVersion is numeric for PE/installer
        # constraints, while appDisplayVersion retains the branch-specific prerelease suffix.
        self.assertIn("appVersion = getAppVersionStringForPackaging(TargetFormat.Exe)", self.desktop_build)
        self.assertIn("appDisplayVersion = getAppVersionString()", self.desktop_build)
        self.assertIn('branch(".+") {', self.root_build)
        self.assertIn('"$defaultSemVersion-${it.refInfo.shortenName}-snapshot"', self.root_build)
        self.assertIn('!define APP_DISPLAY_VERSION "{{ app_display_version }}"', self.nsis)
        self.assertIn('WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayVersion" "${APP_DISPLAY_VERSION}"', self.nsis)
        self.assertNotIn('WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"', self.nsis)
        self.assertIn('WriteRegStr SHCTX "${REG_APP_KEY}" "Version" "${APP_VERSION}"', self.nsis)
        self.assertIn('VIProductVersion "${APP_VERSION_WITH_BUILD}"', self.nsis)
        self.assertIn("return simple()", self.version_util)

if __name__ == "__main__":
    unittest.main(verbosity=2)
