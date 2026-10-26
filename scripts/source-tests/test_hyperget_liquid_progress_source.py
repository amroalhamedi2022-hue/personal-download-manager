"""Source contracts for HyperGet's shared Liquid Progress and real Windows visual gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TABLE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/sections/TableDownloadItem.kt").read_text(encoding="utf-8")
LIST = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/sections/DownloadList.kt").read_text(encoding="utf-8")
SCRIPT = (ROOT / "scripts/Run-WindowsLiquidProgressVisualE2E.ps1").read_text(encoding="utf-8")
UI_WORKFLOW = (ROOT / ".github/workflows/windows-installed-ui-smoke.yml").read_text(encoding="utf-8")
SOURCE_WORKFLOW = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")


class LiquidProgressSourceContract(unittest.TestCase):
    def test_one_shared_clock_is_created_only_when_a_download_is_active(self):
        self.assertIn("hasActiveDownload = downloadList.any", LIST)
        self.assertIn("rememberInfiniteTransition()", LIST)
        self.assertIn("infiniteRepeatable(", LIST)
        self.assertIn("LocalLiquidProgressPhase provides liquidProgressPhase", LIST)
        self.assertNotIn("rememberInfiniteTransition", TABLE)

    def test_progress_phase_is_read_in_draw_not_by_each_row_composition(self):
        progress = TABLE.split("private fun ProgressStatus(", 1)[1]
        self.assertIn("Canvas(", progress)
        self.assertIn("liquidProgressPhase.value", progress)
        self.assertIn("drawRoundRect(", progress)
        self.assertIn("drawCircle(", progress)
        self.assertIn("Brush.horizontalGradient(", progress)
        self.assertIn("clip(CircleShape)", progress)
        self.assertIn("isLiquidActive = status == DownloadProgressStatus.Downloading", TABLE)

    def test_installed_windows_e2e_captures_only_a_proven_live_transfer(self):
        for marker in (
            "download add http",
            "download resume",
            "active_distinct_tasks",
            "statusAtCapture",
            "CopyFromScreen",
            "Get-FileHash",
            "liquid-progress-active.png",
            "liquid-progress-report.json",
            "FinalStatus = 'Finished'",
            "Verified = $true",
        ):
            self.assertIn(marker, SCRIPT)
        self.assertIn("StatusAtCapture = $statusAtCapture", SCRIPT)
        self.assertIn("$actualSha -eq $expectedSha", SCRIPT)

    def test_visual_fixture_is_paced_and_failure_diagnostics_are_preserved(self):
        self.assertIn("/stats?reset=1&gate_tasks=1", SCRIPT)
        self.assertIn("liquid-progress-diagnostics.json", SCRIPT)
        self.assertIn("LastFixtureStats = $captureStats", SCRIPT)
        self.assertIn("LastStatusLine = $lastStatusLine", SCRIPT)
        self.assertLess(
            SCRIPT.index("/stats?reset=1&gate_tasks=1"),
            SCRIPT.index("download resume"),
        )
        # Avoid launching a new CLI process on every fast stats poll. First
        # prove payload movement, then query the installed CLI status.
        active_gate = SCRIPT.index("if ([int]$captureStats.active_distinct_tasks -ge 1")
        status_launch = SCRIPT.index("$statusOutput = @(& $cliPath download show")
        self.assertLess(active_gate, status_launch)

    def test_status_cli_polling_is_rate_limited_and_capture_revalidates_live_bytes(self):
        self.assertIn("$lastStatusPollMilliseconds = -500", SCRIPT)
        self.assertIn("($elapsedMilliseconds - $lastStatusPollMilliseconds) -ge 500", SCRIPT)
        self.assertIn("BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot", SCRIPT)
        self.assertIn("StatusPollCountAtCapture = $statusPollCount", SCRIPT)
        self.assertIn("if ([int]$preScreenshotStats.active_distinct_tasks -lt 1 -or", SCRIPT)
        self.assertIn("cancel-in-progress: false", UI_WORKFLOW)

    def test_cli_status_parser_normalizes_terminal_prefixes_and_tab_columns(self):
        # Normalize ANSI/BOM/zero-width prefixes before parsing ID<TAB>Status.
        # Never persist raw table rows, names, paths, or arbitrary CLI output.
        self.assertIn("function Get-NormalizedCliLines", SCRIPT)
        self.assertIn("TrimStart([char[]]@", SCRIPT)
        self.assertIn("function Get-DownloadStatusFromOutput", SCRIPT)
        self.assertIn("$columns = $line.Split([char[]]@([char]9))", SCRIPT)
        self.assertIn("$columns[0].Trim() -cne [string]$ExpectedId", SCRIPT)
        self.assertEqual(
            SCRIPT.count("Get-DownloadStatusFromOutput -Lines $statusOutput -ExpectedId $taskId"),
            2,
        )
        self.assertIn("Get-DownloadStatusFromOutput -Lines $finalStatusOutput -ExpectedId $taskId", SCRIPT)
        self.assertIn("Task lookup message: $line", SCRIPT)
        self.assertNotIn("StartsWith($statusPrefix", SCRIPT)
        self.assertNotIn("Substring($statusPrefix.Length)", SCRIPT)

    def test_cli_status_probe_captures_sanitized_failure_diagnostics(self):
        self.assertEqual(SCRIPT.count("download show --status-lines $taskId"), 3)
        self.assertNotIn("download show $taskId --status-lines", SCRIPT)
        self.assertIn("$lastStatusCommandExitCode = $LASTEXITCODE", SCRIPT)
        self.assertIn("LastStatusCommandExitCode = $lastStatusCommandExitCode", SCRIPT)
        self.assertIn("LastStatusOutputPreview = $lastStatusOutputPreview", SCRIPT)
        self.assertIn("FinalStatusOutputPreview = $finalStatusOutputPreview", SCRIPT)
        self.assertIn("Get-SanitizedStatusPreview", SCRIPT)
        self.assertIn("No such download", SCRIPT)
        self.assertIn("Formatted table output detected", SCRIPT)
        self.assertIn("LastStatusOutputLineCount = @($statusOutput).Count", SCRIPT)
        self.assertIn("return $preview", SCRIPT)
        self.assertNotIn("return ,$preview", SCRIPT)

    def test_visual_workflow_runs_on_pr_ui_changes_and_uploads_evidence(self):
        self.assertIn("pull_request:", UI_WORKFLOW)
        self.assertIn("needs: change-scope", UI_WORKFLOW)
        self.assertIn("needs.change-scope.outputs.run", UI_WORKFLOW)
        self.assertIn("Run-WindowsLiquidProgressVisualE2E.ps1", UI_WORKFLOW)
        self.assertIn("windows-liquid-progress-visual", UI_WORKFLOW)
        scope = UI_WORKFLOW.split('case "$file" in', 1)[1].split("esac", 1)[0]
        self.assertNotIn("scripts/source-tests/test_hyperget_liquid_progress_source.py", scope)
        self.assertIn("liquid-progress-active.png", SCRIPT)

    def test_fast_source_gate_checks_the_contract_without_building(self):
        self.assertIn("scripts/source-tests/test_hyperget_liquid_progress_source.py", SOURCE_WORKFLOW)
        self.assertIn("test_hyperget_liquid_progress_source.py -v", SOURCE_WORKFLOW)
        self.assertIn("NO APP BUILD", SOURCE_WORKFLOW)


if __name__ == "__main__":
    unittest.main()
