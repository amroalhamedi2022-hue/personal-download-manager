from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ARIA2_MATRIX = (ROOT / "scripts/Run-WindowsAria2ActiveBenchmarkMatrix.ps1").read_text(encoding="utf-8")
FIXTURE = (ROOT / "scripts/LocalHttpRangeFixture.py").read_text(encoding="utf-8")
PAIRED = (ROOT / "scripts/Run-WindowsAria2PairedComparison.ps1").read_text(encoding="utf-8")
GATES = (ROOT / "scripts/Assert-WindowsAria2Stage2Gates.ps1").read_text(encoding="utf-8")
COMPARATOR = (ROOT / "scripts/Compare-WindowsAria2Stage2.ps1").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/hyperget-windows-exe-aria2-evaluation.yml").read_text(encoding="utf-8")
ARIA2_PLAN = (ROOT / "docs/ARIA2_CORE_MIGRATION_PLAN_2026-10-27.md").read_text(encoding="utf-8")


class Aria2Stage2SourceContract(unittest.TestCase):
    def test_matrix_uses_exact_http_fixture_hashes_and_real_concurrency(self):
        self.assertIn("LocalHttpRangeFixture.py", ARIA2_MATRIX)
        self.assertIn("Get-FileHash -LiteralPath $task.Path -Algorithm SHA256", ARIA2_MATRIX)
        self.assertIn("active_distinct_tasks", ARIA2_MATRIX)
        self.assertIn("peak_distinct_tasks", ARIA2_MATRIX)
        self.assertIn("gate_released", ARIA2_MATRIX)
        self.assertIn("Measure-WindowsDownloadManager.ps1", ARIA2_MATRIX)
        self.assertIn("$expectedTasks = $Rounds * ($Workloads | Measure-Object -Sum).Sum", ARIA2_MATRIX)

    def test_matrix_disables_rpc_and_never_invokes_torrent(self):
        self.assertIn("'--enable-rpc=false'", ARIA2_MATRIX)
        self.assertIn("'--no-conf=true'", ARIA2_MATRIX)
        self.assertIn("Torrent-related argument detected", ARIA2_MATRIX)
        self.assertIn("TorrentWorkloadInvoked = $false", ARIA2_MATRIX)
        self.assertIn("EngineArchiveSHA256", ARIA2_MATRIX)
        self.assertIn("evaluation-only", ARIA2_MATRIX)

    def test_paired_runner_uses_three_alternating_rounds_and_same_workload(self):
        self.assertIn("for ($round = 1; $round -le $Rounds; $round++)", PAIRED)
        self.assertIn("if ($round % 2 -eq 1)", PAIRED)
        self.assertIn("Run-HyperGetRound", PAIRED)
        self.assertIn("Run-Aria2Round", PAIRED)
        self.assertIn("Run-WindowsActiveBenchmarkMatrix.ps1", PAIRED)
        self.assertIn("Run-WindowsAria2ActiveBenchmarkMatrix.ps1", PAIRED)
        self.assertIn("Compare-WindowsAria2Stage2.ps1", PAIRED)
        self.assertIn("foreach ($segments in @(1, 4, 8))", PAIRED)
        self.assertIn("aria2-segmentation-summary.csv", PAIRED)

    def test_matrix_waits_for_http_handlers_and_proves_real_segment_completion(self):
        self.assertIn("function Wait-HttpIdle", ARIA2_MATRIX)
        self.assertIn("$after = Wait-HttpIdle -BaseUri $baseUri -TimeoutSeconds 20", ARIA2_MATRIX)
        self.assertIn("task_get_started", ARIA2_MATRIX)
        self.assertIn("task_get_completed", ARIA2_MATRIX)
        self.assertIn("task_get_interrupted", ARIA2_MATRIX)
        self.assertIn("task_bytes_sent", ARIA2_MATRIX)
        self.assertIn('"data_transfer": remaining >= MIN_DATA_TRANSFER_BYTES', FIXTURE)
        self.assertIn('request_record["completed"] = remaining == 0', FIXTURE)
        self.assertIn('request_record["socket_write_bytes"] = response_socket_write_bytes', FIXTURE)
        self.assertIn("$minimumCompletedGets = [Math]::Max(1, $Segments - 1)", ARIA2_MATRIX)
        self.assertIn("$dataRangeRequests = @($ranges | Where-Object { [bool]$_.range -and [bool]$_.data_transfer })", ARIA2_MATRIX)
        self.assertIn("$completedDataRanges = @($dataRangeRequests | Where-Object { [bool]$_.honored -and [bool]$_.completed })", ARIA2_MATRIX)
        self.assertIn("$incompleteDataRanges.Count -gt 0", ARIA2_MATRIX)
        self.assertIn("$honoredRangeCount -lt ($Segments - 1)", ARIA2_MATRIX)
        self.assertIn("$maxSocketOvercount = [long]$interrupted * [long]65536", ARIA2_MATRIX)
        self.assertIn("$bytesSent -lt $expectedBytes -or $bytesSent -gt $maxBytesSent", ARIA2_MATRIX)
        self.assertNotIn("$bytesSent -ne $expectedBytes", ARIA2_MATRIX)

    def test_stage2_thresholds_are_separate_and_no_go_keeps_current_engine(self):
        self.assertIn("MaxThroughputRegressionPercent = 10", GATES)
        self.assertIn("MaxMemoryRegressionPercent = 15", GATES)
        self.assertIn("MinMedianGainPercent = 5", GATES)
        self.assertIn("NO_GO_KEEP_HYPERGET_ENGINE", GATES)
        self.assertIn("STAGE2_PERFORMANCE_GO", GATES)
        self.assertIn("GPL-2.0-or-later", GATES)

    def test_comparator_requires_63_verified_outputs_and_three_paired_rounds(self):
        self.assertIn("if ($totalTransfers -ne 63)", COMPARATOR)
        self.assertIn("if ($b.Count -ne 3 -or $c.Count -ne 3)", COMPARATOR)
        self.assertIn("Expected three paired rounds", COMPARATOR)
        self.assertIn("aria2 1.37.0", COMPARATOR)

    def test_workflow_runs_stage2_only_after_stage1_and_current_app_build_pass(self):
        self.assertIn("aria2-stage2-paired-performance:", WORKFLOW)
        self.assertIn("needs: [aria2-standalone-spike, build-test-hyperget-windows]", WORKFLOW)
        self.assertIn("hyperget-windows-x64-installer", WORKFLOW)
        self.assertIn("actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093", WORKFLOW)
        self.assertIn("hyperget-aria2-stage2-comparison", WORKFLOW)
        self.assertIn("if: always()", WORKFLOW)

    def test_plan_keeps_stock_torrent_binary_evaluation_only_and_migration_gated(self):
        self.assertIn("Stage 2 — Controlled apples-to-apples performance comparison", ARIA2_PLAN)
        self.assertIn("no-BitTorrent", ARIA2_PLAN)
        self.assertIn("Torrent is prohibited", ARIA2_PLAN)
        self.assertIn("Stage 3", ARIA2_PLAN)
        self.assertIn("Stage 4", ARIA2_PLAN)


if __name__ == "__main__":
    unittest.main()
