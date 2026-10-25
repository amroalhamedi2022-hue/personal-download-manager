"""Regression contract for the installed Windows 1/4/16 acceptance harness.

The actual Windows job is still required: source assertions cannot prove
that an installed HyperGet app admits live sockets or completes SHA-256.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
BENCH = (ROOT / "scripts/Run-WindowsActiveBenchmarkMatrix.ps1").read_text(encoding="utf-8")
FIXTURE = (ROOT / "scripts/LocalHttpRangeFixture.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-active-benchmark.yml").read_text(encoding="utf-8")


class ActiveBenchmarkContract(unittest.TestCase):
    def test_each_scenario_sets_its_own_persisted_cap(self):
        self.assertRegex(
            BENCH,
            r"foreach \(\$jobs in @\(1, 4, 16\)\)\s*\{\s*"
            r"\$appliedMaxConcurrentDownloads = Invoke-MaxConcurrentDownloadsSetting -SetValue \$jobs",
        )
        self.assertIn("if ($verifiedMaxConcurrentDownloads -ne $jobs)", BENCH)
        self.assertNotIn("Invoke-MaxConcurrentDownloadsSetting -SetValue 16", BENCH)

    def test_batch_resume_measures_real_distinct_sockets(self):
        self.assertIn("& $cliPath download resume @startIds", BENCH)
        self.assertIn("active_distinct_tasks", BENCH)
        self.assertIn("peak_distinct_tasks", BENCH)
        self.assertIn("Only $($ready.active_distinct_tasks) of $jobs tasks were active", BENCH)

    def test_every_download_must_pass_sha_and_process_samples(self):
        self.assertIn("Get-FileHash -LiteralPath $path -Algorithm SHA256", BENCH)
        self.assertIn("Get-FileHash -LiteralPath $task.Path -Algorithm SHA256", BENCH)
        self.assertIn("$expectedTaskCount = $Rounds * (1 + 4 + 16)", BENCH)
        self.assertIn("$metricFiles.Count -ne $Rounds * 3", BENCH)
        self.assertIn("Measure-WindowsDownloadManager.ps1", BENCH)

    def test_fixture_exposes_only_synthetic_active_task_ids(self):
        self.assertIn('snapshot["active_task_ids"] = sorted(tasks_active)', FIXTURE)
        self.assertIn('snapshot["active_distinct_tasks"] = len(tasks_active)', FIXTURE)
        self.assertIn('BurstReadyHTTPServer(("127.0.0.1", 0), Handler)', FIXTURE)
        self.assertIn("class BurstReadyHTTPServer(ThreadingHTTPServer):", FIXTURE)
        self.assertIn("request_queue_size = 256", FIXTURE)
        self.assertIn("test_burst_32_simultaneous_range_probes_do_not_refuse_connections", (ROOT / "scripts/test_local_http_range_fixture.py").read_text(encoding="utf-8"))

    def test_16_task_gate_proves_live_sockets_not_merely_seen_tasks(self):
        self.assertIn("gate_tasks=16", BENCH)
        self.assertIn("gate_released", BENCH)
        self.assertNotIn("gate_open.wait(", FIXTURE)
        self.assertIn("gate_open.set()", FIXTURE)
        self.assertIn("len(tasks_active) >= gate_expected[0]", FIXTURE)
        self.assertIn("Get-FileHash -LiteralPath $task.Path -Algorithm SHA256", BENCH)

    def test_short_probes_cannot_admit_a_strict_data_transfer(self):
        self.assertIn("MIN_DATA_TRANSFER_BYTES = 64 * 1024", FIXTURE)
        self.assertIn("gate_expected[0] == 0 or remaining >= MIN_DATA_TRANSFER_BYTES", FIXTURE)
        self.assertNotIn("gate_open.wait(", FIXTURE)
        self.assertIn("if is_counted_transfer:", FIXTURE)
        self.assertIn('snapshot["task_probe_get_started"]', FIXTURE)
        self.assertIn('snapshot["task_data_get_started"]', FIXTURE)
        self.assertIn("if is_counted_transfer:", FIXTURE)
        self.assertIn("if gate_expected[0] and is_counted_transfer:", FIXTURE)

    def test_gated_workload_paces_aggregate_task_bytes_not_individual_sockets(self):
        self.assertIn('class TaskChunkPacer:', FIXTURE)
        self.assertIn('with task_pacer.slot(task_id) as wait:', FIXTURE)
        self.assertIn('with task_lock:', FIXTURE)
        self.assertIn('self.next_send[task_id] = time.monotonic() + self.interval_seconds', FIXTURE)
        self.assertNotIn('task_pacer.reserve(', FIXTURE)
        self.assertIn('stats["paced_chunks"] += 1', FIXTURE)
        self.assertIn('stats["paced_wait_seconds"] += wait', FIXTURE)
        self.assertIn("'--task-chunk-delay-ms', \"180\"", BENCH)
        self.assertIn('if gate_expected[0] and is_counted_transfer:', FIXTURE)
        self.assertIn('if wait:', FIXTURE)
        self.assertIn('time.sleep(wait)', FIXTURE)
        self.assertIn('snapshot["task_bytes_sent"] = dict(sorted(task_bytes_sent.items()))', FIXTURE)

    def test_strict_fixture_has_one_throttle_per_transfer(self):
        self.assertIn('if args.chunk_delay_ms > 0 and not gate_expected[0]:', FIXTURE)
        self.assertIn('stats["connection_delay_sleeps"] += 1', FIXTURE)
        self.assertIn('with task_pacer.slot(task_id) as wait:', FIXTURE)
        # Neither real-socket admission nor the final SHA gate may be relaxed.
        self.assertIn('active_distinct_tasks', BENCH)
        self.assertIn('Get-FileHash -LiteralPath $task.Path -Algorithm SHA256', BENCH)

    def test_admission_window_does_not_deadlock_host_socket_pool(self):
        self.assertIn("for ($attempt = 0; $attempt -lt 300; $attempt++)", BENCH)
        self.assertNotIn("gate_open.wait(", FIXTURE)
        self.assertIn("if ([int]$during.peak_distinct_tasks -lt $jobs -or [int]$during.active_distinct_tasks -lt $jobs)", BENCH)

    def test_windows_summary_uses_exact_pr_head(self):
        self.assertIn("-SourceSha $env:HYPERGET_TESTED_SHA -Rounds 3", WORKFLOW)
        self.assertNotIn("-SourceSha $env:GITHUB_SHA -Rounds 3", WORKFLOW)

    def test_original_persisted_cap_is_restored_on_failure(self):
        self.assertIn("} finally {", BENCH)
        self.assertIn("-SetValue $originalMaxConcurrentDownloads", BENCH)

    def test_windows_milestone_checks_exact_pr_head_not_merge_sha(self):
        self.assertIn("pull_request:", WORKFLOW)
        self.assertIn("branches: [codex/repair-rf00-rf01]", WORKFLOW)
        self.assertIn("github.event.pull_request.head.sha || github.sha", WORKFLOW)
        self.assertIn("-SourceSha $env:HYPERGET_TESTED_SHA", WORKFLOW)
        self.assertIn("-Rounds 3", WORKFLOW)
        self.assertIn("createReleaseFolderForCi", WORKFLOW)
        self.assertIn("Run-WindowsActiveBenchmarkMatrix.ps1", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
