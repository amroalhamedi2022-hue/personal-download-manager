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


def reconcile_fixture_response_ranges(responses, expected_bytes):
    """Mirror the benchmark's strict interval/overlap reconciliation."""
    socket_write_sum = 0
    metadata_probe_bytes = 0
    metadata_probe_count = 0
    intervals = []
    for response in responses:
        written = int(response["socket_write_bytes"])
        if written < 0:
            raise AssertionError("negative socket write")
        socket_write_sum += written
        honored_range = bool(response["range"] and response["honored"])
        start = int(response["start"]) if honored_range else 0
        response_length = int(response["end"]) - int(response["start"]) + 1 if honored_range else expected_bytes
        if written > response_length or (response["completed"] and written != response_length):
            raise AssertionError("impossible response byte count")
        if not response["data_transfer"]:
            if written:
                metadata_probe_count += 1
                metadata_probe_bytes += written
            continue
        if written:
            intervals.append({
                "start": start,
                "end": start + written - 1,
                "is_range": honored_range,
                "is_unranged_interrupted": (not response["range"] and not response["completed"]),
                "requested_start": start,
                "requested_end": int(response["end"]) if honored_range else expected_bytes - 1,
            })
    if metadata_probe_count > 2:
        raise AssertionError("too many metadata probes")

    incomplete_data_range_count = sum(
        bool(r["range"] and r["honored"] and r["data_transfer"] and not r["completed"])
        for r in responses
    )
    tail_retry_count = 0
    for response in responses:
        if (response['range'] and response['honored'] and response['data_transfer']
                and not response['completed'] and 0 < int(response['socket_write_bytes']) <= 65536):
            if any(p['range'] and p['honored'] and p['completed'] and p['data_transfer']
                   and int(p['start']) < int(response['start']) and int(p['end']) == int(response['end'])
                   and int(p['socket_write_bytes']) == int(p['end']) - int(p['start']) + 1
                   for p in responses):
                tail_retry_count += 1
    for index, retry in enumerate(responses):
        if not (retry["range"] and retry["honored"] and retry["completed"] and retry["data_transfer"]):
            continue
        # Fixture records concurrent handlers by arrival time, not byte offset.
        for prior_index, prior in enumerate(responses):
            if prior_index == index:
                continue
            if not (prior["range"] and prior["honored"] and prior["data_transfer"]):
                continue
            prior_served_end = int(prior["start"]) + int(prior["socket_write_bytes"]) - 1
            if int(prior["end"]) == int(retry["end"]) and int(prior["start"]) < int(retry["start"]) <= prior_served_end:
                retry_overlap = prior_served_end - int(retry["start"]) + 1
                if 0 < retry_overlap <= 65536:
                    tail_retry_count += 1
                    break
    if tail_retry_count > incomplete_data_range_count + 1:
        raise AssertionError("too many tail retries")

    ordered = sorted(intervals, key=lambda x: (x["start"], x["end"]))
    covered_end = -1
    covered_bytes = 0
    duplicate_bytes = 0
    overlap_count = 0
    unranged_overlap_count = 0
    tail_retry_overlap_count = 0
    for interval in ordered:
        start, end = interval["start"], interval["end"]
        if start > covered_end + 1:
            raise AssertionError("coverage gap")
        if end >= expected_bytes:
            raise AssertionError("coverage past expected payload")
        overlap = min(end, covered_end) - start + 1 if start <= covered_end else 0
        if overlap > 65536:
            raise AssertionError("overlap exceeds one 64-KiB chunk")
        if overlap:
            prior_unranged = any(
                p["is_unranged_interrupted"] and p["start"] < start and p["end"] >= start
                for p in ordered
            )
            prior_tail = any(
                p["is_range"] and p["requested_end"] == interval["requested_end"]
                and p["requested_start"] < interval["requested_start"] and p["end"] >= start
                for p in ordered
            )
            if prior_unranged == prior_tail:
                raise AssertionError("unclassified overlap")
            overlap_count += 1
            duplicate_bytes += overlap
            if prior_unranged:
                unranged_overlap_count += 1
            else:
                tail_retry_overlap_count += 1
        if end > covered_end:
            new_start = max(start, covered_end + 1)
            covered_bytes += end - new_start + 1
            covered_end = end

    if covered_bytes != expected_bytes or covered_end != expected_bytes - 1:
        raise AssertionError("incomplete unique coverage")
    unranged_interrupted_count = sum(
        (not r["range"] and not r["completed"] and r["data_transfer"] and int(r["socket_write_bytes"]) > 0)
        for r in responses
    )
    max_duplicate_bytes = 65536 * (unranged_interrupted_count + tail_retry_count)
    if (
        unranged_overlap_count > unranged_interrupted_count
        or tail_retry_overlap_count > tail_retry_count
        or overlap_count > unranged_interrupted_count + tail_retry_count
        or duplicate_bytes > max_duplicate_bytes
    ):
        raise AssertionError("unexplained duplicate bytes")
    if socket_write_sum != covered_bytes + duplicate_bytes + metadata_probe_bytes:
        raise AssertionError("socket writes do not reconcile")
    return socket_write_sum, duplicate_bytes, covered_bytes


class Aria2Stage2SourceContract(unittest.TestCase):
    def test_cancelled_speculative_tail_with_complete_cover(self):
        # Run 40745555279, task 10: the 8-12 MiB response completed;
        # a speculative 11-12 MiB response was cancelled after one chunk.
        mib = 1024 * 1024
        responses = [dict(start=0, end=32*mib-1, range=False, honored=False,
                          data_transfer=True, completed=False, socket_write_bytes=4*mib+65536)]
        responses += [dict(start=i*mib, end=(i+4)*mib-1, range=True, honored=True,
                           data_transfer=True, completed=True, socket_write_bytes=4*mib)
                      for i in range(4, 32, 4)]
        responses.append(dict(start=11*mib, end=12*mib-1, range=True, honored=True,
                              data_transfer=True, completed=False, socket_write_bytes=65536))
        for ordered in (responses, list(reversed(responses))):
            self.assertEqual(reconcile_fixture_response_ranges(ordered, 32*mib),
                             (32*mib+131072, 131072, 32*mib))
        responses[-1]['socket_write_bytes'] = 131072
        with self.assertRaises(AssertionError):
            reconcile_fixture_response_ranges(responses, 32*mib)
        responses[-1]['socket_write_bytes'] = 65536
        responses[2]['socket_write_bytes'] -= 1
        responses[2]['completed'] = False
        with self.assertRaises(AssertionError):
            reconcile_fixture_response_ranges(responses, 32*mib)

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
        self.assertIn("$honoredRangeCount -lt ($Segments - 1)", ARIA2_MATRIX)
        self.assertIn("$unrecoveredDataRanges = [System.Collections.Generic.List[object]]::new()", ARIA2_MATRIX)
        self.assertIn("for ($retryIndex = $rangeIndex + 1; $retryIndex -lt $ranges.Count; $retryIndex++)", ARIA2_MATRIX)
        self.assertIn("[long]$retry.start -ge [long]$range.start", ARIA2_MATRIX)
        self.assertIn("[long]$retry.start -le [long]$range.end", ARIA2_MATRIX)
        self.assertIn("[long]$retry.end -eq [long]$range.end", ARIA2_MATRIX)
        self.assertIn("if (-not $recoveredByTailRetry)", ARIA2_MATRIX)
        self.assertIn("$unrecoveredDataRanges.Count -gt 0", ARIA2_MATRIX)
        self.assertNotIn("if ($incompleteDataRanges.Count -gt 0)", ARIA2_MATRIX)
        self.assertIn("$socketWriteSum = [long]0", ARIA2_MATRIX)
        self.assertIn("$servedIntervals = [System.Collections.Generic.List[object]]::new()", ARIA2_MATRIX)
        self.assertIn("$metadataProbeCount -gt 2", ARIA2_MATRIX)
        self.assertIn("$tailRetryCount -gt ($incompleteDataRangeCount + 1)", ARIA2_MATRIX)
        self.assertIn("$priorServedEnd = [long]$prior.start + [long]$prior.socket_write_bytes - 1", ARIA2_MATRIX)
        self.assertIn("$overlap -gt 65536", ARIA2_MATRIX)
        self.assertIn("$coveredBytes -ne $expectedBytes", ARIA2_MATRIX)
        self.assertIn("$duplicateBytes -gt $maxDuplicateBytes", ARIA2_MATRIX)
        self.assertIn("$socketWriteSum -ne $reconciledBytes", ARIA2_MATRIX)
        self.assertNotIn("$maxSocketOvercount = [long]$interrupted * [long]65536", ARIA2_MATRIX)
        self.assertNotIn("$bytesSent -ne $expectedBytes", ARIA2_MATRIX)

    def test_socket_write_accounting_accepts_bounded_tail_overlap_and_rejects_gaps(self):
        mib = 1024 * 1024
        expected = 32 * mib
        responses = [
            {"start": 0, "end": expected - 1, "range": False, "honored": False,
             "data_transfer": True, "completed": False, "socket_write_bytes": 4 * mib + 65536},
            {"start": 16 * mib, "end": 20 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            {"start": 8 * mib, "end": 12 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            {"start": 24 * mib, "end": 28 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            # Reproduces the Windows failure: the 4–8 MiB Range stops at 7.75 MiB,
            # then a completed same-end retry overlaps the actual served tail by 64 KiB.
            {"start": 4 * mib, "end": 8 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": False, "socket_write_bytes": 4 * mib - 4 * 65536},
            {"start": 12 * mib, "end": 16 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            {"start": 20 * mib, "end": 24 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            {"start": 28 * mib, "end": expected - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 4 * mib},
            {"start": 8 * mib - 5 * 65536, "end": 8 * mib - 1, "range": True, "honored": True,
             "data_transfer": True, "completed": True, "socket_write_bytes": 5 * 65536},
        ]
        total, duplicate, covered = reconcile_fixture_response_ranges(responses, expected)
        self.assertEqual(total, expected + 2 * 65536)
        self.assertEqual(duplicate, 2 * 65536)
        self.assertEqual(covered, expected)

        # Parallel HTTP handlers may record the short same-end tail range
        # before the larger range it overlaps. Accounting must not depend on
        # request-arrival order; byte-offset sorting still determines coverage.
        out_of_order = [dict(r) for r in responses]
        out_of_order[2], out_of_order[8] = out_of_order[8], out_of_order[2]
        total, duplicate, covered = reconcile_fixture_response_ranges(out_of_order, expected)
        self.assertEqual(total, expected + 2 * 65536)
        self.assertEqual(duplicate, 2 * 65536)
        self.assertEqual(covered, expected)
        self.assertIn("for ($priorIndex = 0; $priorIndex -lt $ranges.Count; $priorIndex++)", ARIA2_MATRIX)
        self.assertIn("if ($priorIndex -eq $retryIndex) { continue }", ARIA2_MATRIX)
        self.assertNotIn("for ($priorIndex = 0; $priorIndex -lt $retryIndex; $priorIndex++)", ARIA2_MATRIX)

        oversized_overlap = [dict(r) for r in responses]
        oversized_overlap[0]["socket_write_bytes"] = 4 * mib + 2 * 65536
        with self.assertRaises(AssertionError):
            reconcile_fixture_response_ranges(oversized_overlap, expected)

        oversized_tail_retry = [dict(r) for r in responses]
        oversized_tail_retry[-1]["start"] = 8 * mib - 6 * 65536
        oversized_tail_retry[-1]["socket_write_bytes"] = 6 * 65536
        with self.assertRaises(AssertionError):
            reconcile_fixture_response_ranges(oversized_tail_retry, expected)

        missing_byte = [dict(r) for r in responses]
        missing_byte[0]["socket_write_bytes"] = 4 * mib - 1
        with self.assertRaises(AssertionError):
            reconcile_fixture_response_ranges(missing_byte, expected)

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
