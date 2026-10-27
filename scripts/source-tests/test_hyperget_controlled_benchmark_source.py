import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class ControlledBenchmarkSourceTests(unittest.TestCase):
    def test_matrix_records_payload_time_hash_and_harness_provenance(self):
        source = read("scripts/Run-WindowsActiveBenchmarkMatrix.ps1")
        self.assertIn("$transferStartedUtc = [DateTime]::UtcNow", source)
        self.assertIn("$roundFinishedUtc = [DateTime]::UtcNow", source)
        self.assertIn("ElapsedSeconds = $roundElapsedSeconds", source)
        self.assertIn("AggregateMiBPerSecond = $aggregateMiBPerSecond", source)
        self.assertIn("ExpectedSHA256 = $expectedSha", source)
        self.assertIn("HarnessSha = $HarnessSha", source)
        self.assertIn("-HarnessSha $HarnessSha", source)
        self.assertIn("foreach ($jobs in @(1, 4, 16))", source)
        self.assertIn("PeakDistinctTasks", source)

    def test_sampler_and_summary_keep_app_and_harness_shas_separate(self):
        sampler = read("scripts/Measure-WindowsDownloadManager.ps1")
        summary = read("scripts/Summarize-WindowsActiveBenchmark.ps1")
        self.assertIn("[string] $HarnessSha = ''", sampler)
        self.assertIn("HarnessSha = $HarnessSha", sampler)
        self.assertIn("sample.HarnessSha -ne $HarnessSha", summary)
        self.assertIn("record.HarnessSha -ne $HarnessSha", summary)
        self.assertIn("record.SHA256 -ne $record.ExpectedSHA256", summary)
        self.assertIn("Harness source SHA", summary)
        self.assertIn("OS = $machineOs; LogicalCpuCount = $logicalCpu", summary)

    def test_controlled_runner_alternates_variants_and_owns_cleanup(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        self.assertIn("BaselineInstaller", source)
        self.assertIn("CandidateInstaller", source)
        self.assertIn("BaselineSourceSha", source)
        self.assertIn("CandidateSourceSha", source)
        self.assertIn("HarnessSha", source)
        self.assertIn("@('candidate','baseline')", source)
        self.assertIn("@('baseline','candidate')", source)
        self.assertIn("Refusing to touch pre-existing user data", source)
        self.assertIn("Remove-OwnedData -ExpectedToken", source)
        self.assertIn("Assert-NoProductResidue", source)
        self.assertIn("Run-WindowsActiveBenchmarkMatrix.ps1", source)
        self.assertIn("Summarize-WindowsActiveBenchmark.ps1", source)

    def test_comparison_enforces_hashes_overlap_and_predeclared_regression_threshold(self):
        source = read("scripts/Compare-WindowsActiveBenchmarks.ps1")
        for token in (
            "63 verified downloads",
            "ExpectedSHA256",
            "PeakDistinctTasks",
            "ElapsedSeconds",
            "AggregateMiBPerSecond",
            "MaxRegressionPercent",
            "PeakWorkingSetDeltaPercent",
            "PeakPrivateDeltaPercent",
            "Result=$overall",
            "not a comparison against Internet Download Manager",
        ):
            self.assertIn(token, source)
        self.assertIn("-ge (0.0 - $MaxRegressionPercent)", source)
        self.assertIn("-le $MaxRegressionPercent", source)

    def test_workflow_builds_exact_pair_on_same_windows_runner_and_uploads_failure_evidence(self):
        workflow = read(".github/workflows/windows-installed-active-benchmark.yml")
        self.assertIn("controlled-baseline-candidate-comparison", workflow)
        self.assertIn("d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5", workflow)
        self.assertIn("dd27731decbcd8cf1cb67feae42963be42d3bb7a", workflow)
        self.assertIn("runs-on: windows-2022", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("HG_E061_CONTROLLED_PERFORMANCE_COMPARISON.md", workflow)
        self.assertIn("comparison=$comparison", workflow)
        self.assertIn("outputs.comparison != 'true'", workflow)

    def test_checkpoint_is_written_before_build_and_does_not_claim_results(self):
        checkpoint = read("docs/checkpoints/HG_E061_CONTROLLED_PERFORMANCE_COMPARISON.md")
        self.assertIn("Pre-build question", checkpoint)
        self.assertIn("Why source tests are insufficient", checkpoint)
        self.assertIn("d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5", checkpoint)
        self.assertIn("dd27731decbcd8cf1cb67feae42963be42d3bb7a", checkpoint)
        self.assertIn("15%", checkpoint)
        self.assertRegex(checkpoint, r"Status: OPEN")


if __name__ == "__main__":
    unittest.main()
