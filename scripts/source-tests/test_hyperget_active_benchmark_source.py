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

    def test_workflow_change_itself_cannot_silently_skip_the_required_matrix(self):
        # pull_request.paths already includes the workflow. The runtime scope gate
        # must also recognize it, otherwise a workflow-only acceptance-gate fix
        # is reported as green while the 63-download/9-metric job is skipped.
        scope = WORKFLOW.split('case "$file" in', 1)[1].split(")", 1)[0]
        self.assertIn(".github/workflows/windows-installed-active-benchmark.yml", scope)
        self.assertIn("scripts/Run-WindowsActiveBenchmarkMatrix.ps1", scope)
        scope_job = WORKFLOW.split("  change-scope:", 1)[1].split("  real-windows-active-transfers:", 1)[0]
        self.assertIn("fetch-depth: 0", scope_job)
        self.assertIn('changed="$(git diff --name-only "origin/$GITHUB_BASE_REF...HEAD")"', scope_job)
        self.assertNotIn("git diff --name-only HEAD^ HEAD", scope_job)
        self.assertIn("if: needs.change-scope.outputs.run == 'true'", WORKFLOW)



class Aria2Stage1ViabilityContract(unittest.TestCase):
    ROOT = Path(__file__).resolve().parents[2]
    ARIA2 = (ROOT / "scripts/Run-WindowsAria2Viability.ps1").read_text(encoding="utf-8")
    ARIA2_WORKFLOW = (ROOT / ".github/workflows/hyperget-windows-exe-aria2-evaluation.yml").read_text(encoding="utf-8")
    HTTP_FIXTURE_TESTS = (ROOT / "scripts/test_local_http_range_fixture.py").read_text(encoding="utf-8")

    def test_fault_fixture_routes_and_resume_range_telemetry_are_locked(self):
        self.assertIn('address.path == "/redirect"', FIXTURE)
        self.assertIn('address.path.startswith("/status/")', FIXTURE)
        self.assertIn('"$baseUri/status/$($status)?task=status-$status"', self.ARIA2)
        self.assertNotIn('"$baseUri/status/$status?task=status-$status"', self.ARIA2)
        self.assertIn('address.path == "/ignore-range"', FIXTURE)
        self.assertIn('snapshot["task_ranges"]', FIXTURE)
        self.assertIn('task_ranges.clear()', FIXTURE)
        for case in (
            "test_redirect_route_preserves_task_query",
            "test_http_error_routes_return_the_requested_status_without_a_body",
            "test_ignore_range_route_returns_full_200_and_records_the_ignored_header",
            "test_range_telemetry_records_nonzero_resume_offsets_and_reset_clears_it",
        ):
            self.assertIn(case, self.HTTP_FIXTURE_TESTS)

    def test_resume_requires_a_real_partial_file_and_nonzero_restart_offset(self):
        self.assertIn("--continue=true", self.ARIA2)
        self.assertIn("--always-resume=true", self.ARIA2)
        self.assertIn("--auto-save-interval=1", self.ARIA2)
        self.assertIn("$partial -ge 2MB", self.ARIA2)
        self.assertIn("$controlAfter -le $controlBefore", self.ARIA2)
        self.assertIn("http-resume-first-stats.json", self.ARIA2)
        self.assertIn("http-resume-restart-stats.json", self.ARIA2)
        self.assertIn("task_bytes_sent", self.ARIA2)
        self.assertIn("servedAfterRestart -ge $expectedBytes", self.ARIA2)
        self.assertIn("requestedAfterRestart", self.ARIA2)
        self.assertIn("resume.bin.aria2", self.ARIA2)
        self.assertIn("partialBytes -ge $expectedBytes", self.ARIA2)
        self.assertIn("$_.start -gt 0", self.ARIA2)
        # Declared request lengths include an aborted probe GET; only actual socket bytes may gate resume efficiency.
        self.assertIn("servedAfterRestart -ge $expectedBytes", self.ARIA2)
        self.assertNotIn("$requestedAfterRestart -ge $expectedBytes", self.ARIA2)
        self.assertIn("Assert-ExactFile", self.ARIA2)

    def test_ftp_is_authenticated_and_transfer_bytes_are_verified(self):
        self.assertIn("LocalFtpFixture.py", self.ARIA2)
        self.assertIn("--ftp-user=hypergettest", self.ARIA2)
        self.assertIn("--ftp-passwd=$ftpPassword", self.ARIA2)
        self.assertIn("login_successes", self.ARIA2)
        self.assertIn("completed_retrievals", self.ARIA2)
        self.assertIn("SHA256=$ftpHash", self.ARIA2)
        self.assertIn("[REDACTED]", self.ARIA2)
        self.assertIn("SecretLeakDetected", self.ARIA2)

    def test_rpc_is_local_authenticated_and_rejects_stale_secrets(self):
        for required in (
            "--rpc-listen-all=false",
            "--rpc-secret=$Secret",
            "Get-NetTCPConnection -State Listen",
            "token:invalid",
            "aria2.shutdown",
            "$staleToken",
            "-SkipHttpErrorCheck",
        ):
            self.assertIn(required, self.ARIA2)
        self.assertIn("LocalAddress -notin @('127.0.0.1', '::1')", self.ARIA2)

    def test_rpc_auth_gate_preserves_http_rejection_without_swallowing_transport_failures(self):
        invoke_rpc = self.ARIA2.split("function Invoke-Rpc", 1)[1].split("function Test-RpcSucceeded", 1)[0]
        self.assertIn("Invoke-WebRequest -Method Post", invoke_rpc)
        self.assertIn("-SkipHttpErrorCheck", invoke_rpc)
        self.assertIn("StatusCode = [int]$response.StatusCode", invoke_rpc)
        self.assertIn("TransportError = $_.Exception.GetType().Name", invoke_rpc)
        self.assertNotIn("return $null", invoke_rpc)
        self.assertIn("function Test-RpcRejected", self.ARIA2)
        self.assertIn("$noTokenRejected = Test-RpcRejected", self.ARIA2)
        self.assertIn("$wrongTokenRejected = Test-RpcRejected", self.ARIA2)
        self.assertIn("Get-RpcOutcome -Response $noToken", self.ARIA2)
        self.assertIn("Test-RpcSucceeded -Response $freshToken", self.ARIA2)

    def test_stock_bitTorrent_binary_is_evaluation_only(self):
        self.assertIn("$bitTorrentEnabled", self.ARIA2)
        self.assertIn("never bundle this stock binary", self.ARIA2.lower())
        self.assertIn("no SFTP server E2E was run", self.ARIA2)
        self.assertIn("no production engine switch", self.ARIA2)

    def test_workflow_pins_archive_before_viability_and_uploads_sanitized_evidence(self):
        self.assertIn("67d015301eef0b612191212d564c5bb0a14b5b9c4796b76454276a4d28d9b288", self.ARIA2_WORKFLOW)
        self.assertIn("Run-WindowsAria2Viability.ps1", self.ARIA2_WORKFLOW)
        self.assertIn("aria2-viability-results.csv", self.ARIA2)
        self.assertIn("aria2-viability-report.md", self.ARIA2)
        self.assertIn("path: ${{ runner.temp }}/aria2-viability", self.ARIA2_WORKFLOW)
        self.assertIn("if: always()", self.ARIA2_WORKFLOW)

    def test_workflow_executes_viability_gate_after_pinned_archive_and_preflight(self):
        preflight = self.ARIA2_WORKFLOW.index("Verify exact source SHA and preflight source/fixture tests")
        archive = self.ARIA2_WORKFLOW.index("Download and verify pinned upstream aria2 archive")
        invocation = self.ARIA2_WORKFLOW.index("& ./scripts/Run-WindowsAria2Viability.ps1")
        upload = self.ARIA2_WORKFLOW.index("name: Upload sanitized Stage 1 viability evidence")
        self.assertLess(preflight, archive)
        self.assertLess(archive, invocation)
        self.assertLess(invocation, upload)
        self.assertIn("-Aria2Executable $env:ARIA2_EXE", self.ARIA2_WORKFLOW)
        self.assertIn("-SourceSha $env:HYPERGET_TESTED_SHA", self.ARIA2_WORKFLOW)
        self.assertIn("HYPERGET_TESTED_SHA: ${{ github.event.pull_request.head.sha || github.sha }}", self.ARIA2_WORKFLOW)


if __name__ == "__main__":
    unittest.main()
