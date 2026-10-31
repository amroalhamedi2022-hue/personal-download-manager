"""Source contracts for non-truncated, non-sensitive HyperGet CLI diagnostics.

Fast checks only; Kotlin compilation and installed Windows IPC remain separate gates.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHOW = ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/cli/download/show/ShowDownload.kt"
BENCH = ROOT / "scripts/Run-WindowsActiveBenchmarkMatrix.ps1"


class HyperGetStatusLinesSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.show = SHOW.read_text(encoding="utf-8")
        cls.benchmark = BENCH.read_text(encoding="utf-8")

    def test_opt_in_machine_status_flag(self):
        self.assertIn('"--status-lines"', self.show)
        self.assertIn('private val statusLines by option(', self.show)

    def test_untruncated_status_excludes_names_paths_and_secrets(self):
        self.assertIn('echo("${download.id}\\t${download.status.displayName}")', self.show)
        self.assertIn('if (statusLines)', self.show)
        self.assertNotIn('echo("${download.id}\\t${download.name}', self.show)

    def test_watch_and_machine_status_are_exclusive(self):
        self.assertIn('require(!watch || !statusLines)', self.show)

    def test_three_windows_failure_paths_use_stable_output(self):
        self.assertEqual(self.benchmark.count("download show --status-lines @startIds"), 3)
        self.assertNotIn("download show @startIds", self.benchmark)
        self.assertIn("ID<TAB>Status", self.benchmark)

    def test_strict_acceptance_still_required(self):
        for marker in (
            "active_distinct_tasks -ge $jobs",
            "active_distinct_tasks -lt $jobs",
            "$expectedTaskCount = $Rounds * (1 + 4 + 16)",
            "Get-FileHash -LiteralPath $task.Path -Algorithm SHA256",
        ):
            self.assertIn(marker, self.benchmark)


    def test_opt_in_error_class_diagnostics_without_secrets(self):
        ipc = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/IDefaultAppIPCService.kt").read_text(encoding="utf-8")
        impl = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/DefaultAppIPCServiceImpl.kt").read_text(encoding="utf-8")
        guard = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/FailureDiagnosticType.kt").read_text(encoding="utf-8")
        self.assertIn('val failureType: String? = null', ipc)
        self.assertIn('downloadSystem.downloadErrorsFlow.value[it.id]?.throwableName', impl)
        self.assertIn('it.status == DownloadStatus.Error', impl)
        self.assertIn('safeFailureType(', impl)
        self.assertIn('Regex("[A-Za-z_$][A-Za-z0-9_.$]{0,95}")', guard)
        self.assertIn('?: "Unclassified"', guard)
        self.assertNotIn('throwableMessage', impl)
        self.assertNotIn('throwableMessage', self.show)
        self.assertIn('"--diagnostic-lines"', self.show)
        self.assertIn('download.failureType ?: "Unclassified"', self.show)
        self.assertIn('echo("${download.id}\\t${download.status.displayName}\\t$type")', self.show)

    def test_failure_diagnostics_cover_all_three_windows_failure_paths(self):
        self.assertEqual(self.benchmark.count('download show --diagnostic-lines @startIds'), 3)
        self.assertEqual(self.benchmark.count('ID<TAB>Status<TAB>Class'), 3)
        self.assertIn('require(!statusLines || !diagnosticLines)', self.show)
        self.assertIn('require(!watch || !diagnosticLines)', self.show)

if __name__ == "__main__":
    unittest.main()
