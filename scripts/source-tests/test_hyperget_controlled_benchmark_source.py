import pathlib
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
        self.assertIn("Start-Transcript -Path", source)
        self.assertNotIn("Start-Transcript -LiteralPath", source)
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
        self.assertIn("Baseline and candidate did not use the same deterministic fixture bytes", source)
        self.assertIn("if ($record.SHA256 -notmatch '^[0-9a-fA-F]{64}$' -or $record.SHA256 -ne $record.ExpectedSHA256)", source)
        self.assertIn('if (-not $seenNames.Add("$round/$($record.OutputName)"))', source)
        self.assertIn('Where-Object { $_.Scenario -eq $scenario -and [int]$_.Round -eq $round }', source)
        self.assertIn("if ([int]$record.Round -ne $round)", source)
        self.assertIn("if ([int]$row.Round -ne $round)", source)
        self.assertNotIn('Where-Object { $_.Scenario -eq $scenario -and [int]$_.Round -eq 1 }', source)

    def test_workflow_builds_exact_pair_on_same_windows_runner_and_uploads_failure_evidence(self):
        workflow = read(".github/workflows/windows-installed-active-benchmark.yml")
        self.assertIn("controlled-baseline-candidate-comparison", workflow)
        self.assertIn("d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5", workflow)
        self.assertIn("c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1", workflow)
        self.assertIn("runs-on: windows-2022", workflow)
        self.assertIn("if: always()", workflow)
        self.assertNotIn("HG_E061_CONTROLLED_PERFORMANCE_COMPARISON.md", workflow)
        self.assertNotIn('      - "scripts/source-tests/test_hyperget_controlled_benchmark_source.py"', workflow.split("permissions:", 1)[0])
        self.assertNotIn("|scripts/source-tests/test_hyperget_controlled_benchmark_source.py", workflow)
        self.assertIn("comparison=$comparison", workflow)
        self.assertIn("outputs.comparison != 'true'", workflow)
        self.assertIn("[System.Management.Automation.Language.Parser]::ParseFile", workflow)
        self.assertIn('Write-Host "$($psFile):$($_.Extent.StartLineNumber):$($_.Message)"', workflow)


    def test_registry_residue_is_owned_audited_and_separate_from_performance_acceptance(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        self.assertIn("function Normalize-RegistryValuePath", source)
        self.assertIn("function Clear-OwnedRegistryResidue", source)
        self.assertIn("registry-residue.csv", source)
        self.assertIn("Refusing to remove registry key not proven to belong to the controlled install", source)
        self.assertIn("Clear-OwnedRegistryResidue -Variant $Variant -Round $Round", source)
        self.assertIn("Action = 'Removed test-owned residue after recording'", source)
        self.assertIn("Any registry, shortcut, or install-file residue is a separate uninstall defect diagnostic; cleanup is not evidence that application uninstall acceptance passed.", source)
        self.assertIn('Refusing to touch pre-existing registry key: $key', source)
        self.assertIn("RegistryKey = $key", source)

    def test_shortcut_residue_is_audited_and_removed_only_for_exact_installed_targets(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        self.assertIn("function Clear-OwnedShortcutResidue", source)
        self.assertIn("WScript.Shell", source)
        self.assertIn("CreateShortcut($shortcut)", source)
        self.assertIn("$script:ExpectedGuiPath", source)
        self.assertIn("$script:ExpectedCliPath", source)
        self.assertIn("shortcut-residue.csv", source)
        self.assertIn("Refusing to remove shortcut not proven to target the controlled install", source)
        self.assertIn("Action = 'Removed test-owned shortcut after recording'", source)
        self.assertIn("Remove-Item -LiteralPath $shortcut -Force", source)
        self.assertIn("Clear-OwnedShortcutResidue -Variant $Variant -Round $Round", source)
        self.assertIn("Shortcut cleanup was limited to preflight-absent known links whose absolute target exactly matched the installed GUI or CLI executable", source)
        self.assertIn("$children.Count -eq 0", source)


    def test_install_file_residue_is_hashed_and_cleared_only_from_owned_runner_temp(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        self.assertIn("$script:InstallDirExistedAtPreflight = Test-Path -LiteralPath $installDir -PathType Container", source)
        self.assertIn("function Get-ProcessesUnderInstall", source)
        self.assertIn("Processes remain under controlled install after stop request", source)
        self.assertIn("function Clear-OwnedInstallDirectoryResidue", source)
        self.assertIn("install-file-residue.csv", source)
        self.assertIn("Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256", source)
        self.assertIn("Refusing to clear an install directory outside the exact controlled runner-temp path.", source)
        self.assertIn("Refusing to clear install residue without the active test-owned data marker.", source)
        self.assertIn("Clear-OwnedInstallDirectoryResidue -Variant $Variant -Round $Round", source)
        self.assertLess(
            source.index("Clear-OwnedInstallDirectoryResidue -Variant $Variant -Round $Round"),
            source.index("foreach ($name in @('DownloadManager.exe','DownloadManagerCli.exe','uninstall.exe'))"),
        )
        self.assertIn("Install-file residue records: $installFileCount.", source)
        self.assertIn("not evidence that application uninstall acceptance passed.", source)
        self.assertIn("Preserving test install directory because process still active", source)
        self.assertNotIn("Remove-Item -LiteralPath $script:ActiveInstallDir -Recurse -Force -ErrorAction SilentlyContinue", source)


    def test_checkpoint_is_written_before_build_and_does_not_claim_results(self):
        checkpoint = read("docs/checkpoints/HG_E061_CONTROLLED_PERFORMANCE_COMPARISON.md")
        self.assertIn("Pre-build question", checkpoint)
        self.assertIn("Why source tests are insufficient", checkpoint)
        self.assertIn("d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5", checkpoint)
        self.assertIn("dd27731decbcd8cf1cb67feae42963be42d3bb7a", checkpoint)
        self.assertIn("c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1", checkpoint)
        self.assertIn("15%", checkpoint)
        self.assertRegex(checkpoint, r"Status: OPEN")


    def test_shortcut_parent_preflight_map_is_initialized_before_recording_and_not_reset(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        declaration = "$script:ShortcutParentExistedAtPreflight = @{}"
        self.assertEqual(source.count(declaration), 1)
        self.assertLess(source.index(declaration), source.index("foreach ($shortcut in $shortcuts) {"))
        self.assertIn("$script:ShortcutParentExistedAtPreflight[$parent] = Test-Path -LiteralPath $parent -PathType Container", source)
        self.assertIn("$script:ShortcutParentExistedAtPreflight.ContainsKey($parent)", source)


    def test_install_directory_disappearance_during_residue_inventory_is_race_safe(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        stop = source.index("Stop-ProcessesUnderInstall -Path $actualInstallDir")
        recheck = source.index(
            "if (-not (Test-Path -LiteralPath $actualInstallDir -PathType Container))",
            stop,
        )
        inventory = source.index(
            "$files = @(Get-ChildItem -LiteralPath $actualInstallDir -File -Recurse -Force -ErrorAction Stop)",
            stop,
        )
        self.assertLess(stop, recheck)
        self.assertLess(recheck, inventory)
        inventory_block = source[inventory:inventory + 900]
        self.assertIn("catch {", inventory_block)
        self.assertIn("if (-not (Test-Path -LiteralPath $actualInstallDir -PathType Container))", inventory_block)
        self.assertIn("controlled install directory disappeared during process quiescence; no install-file residue remains.", source)
        self.assertIn("controlled install directory disappeared during residue inventory; no install-file residue remains.", source)
        self.assertIn("throw", inventory_block)


    def test_controlled_round_metadata_is_normalized_before_uninstall(self):
        runner = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        block = runner[runner.index("function Set-ControlledRoundMetadata"):runner.index("function Invoke-VariantRound")]
        self.assertIn("if ([int]$record.Round -ne 1)", block)
        self.assertIn("$record.Round = [string]$Round", block)
        self.assertIn("$record.OutputName = $newName", block)
        self.assertIn("$sample.RunLabel = \"round-$Round\"", block)
        sampler = read("scripts/Measure-WindowsDownloadManager.ps1")
        self.assertIn("RunLabel = $RunLabel", sampler)
        self.assertNotIn("Round =", sampler)
        self.assertIn("$sample.PSObject.Properties['Round']", block)
        self.assertIn("Add-Member -MemberType NoteProperty -Name Round -Value $Round", block)
        self.assertNotIn("[int]$sample.Round -ne 1", block)
        self.assertIn("$row.Round = $Round", block)
        self.assertIn("Get-FileHash -LiteralPath $newPath -Algorithm SHA256", block)
        summary = runner.index("& $summarizeScript -Directory $roundDir")
        stamp = runner.index("Set-ControlledRoundMetadata -Directory $roundDir")
        uninstall = runner.index("$uninstaller = Join-Path $installDir")
        self.assertLess(summary, stamp)
        self.assertLess(stamp, uninstall)

    def test_path_ownership_normalization_fails_closed(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        block = source[source.index("function Normalize-RegistryValuePath"):source.index("function Clear-OwnedRegistryResidue")]
        self.assertIn("$separatorChars = [char[]]@([char]92, [char]47)", block)
        self.assertIn("throw 'Cannot normalize a non-empty path for ownership verification; refusing cleanup.'", block)
        self.assertNotIn("catch { return '' }", block)
        shortcut = source[source.index("function Clear-OwnedShortcutResidue"):source.index("function Clear-OwnedInstallDirectoryResidue")]
        self.assertIn("Shortcut target is empty; refusing to remove shortcut", shortcut)


    def test_round_metadata_regex_is_complete_and_not_spliced_with_function_body(self):
        source = read("scripts/Run-WindowsControlledBenchmarkComparison.ps1")
        expected = (
            "$match = [regex]::Match([string]$record.OutputName, '^(active-(?:1|4|16))-round-1-task-("
            + chr(92) + "d+)" + chr(92) + ".bin$')"
        )
        self.assertIn(expected, source)
        self.assertNotIn(".bin        param([ValidateSet('baseline','candidate')", source)
        self.assertEqual(source.count("function Set-ControlledRoundMetadata {"), 1)
        self.assertEqual(source.count("function Invoke-VariantRound {"), 1)
        self.assertIn("one controlled round (paired round $Round)", source)

if __name__ == "__main__":
    unittest.main()
