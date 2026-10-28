#requires -Version 7.0
<#
.SYNOPSIS
Audits and compares paired Windows active-transfer benchmark evidence.
.DESCRIPTION
Requires 63 verified 32-MiB transfers and nine resource rounds for each exact app SHA.
Compares median throughput and peak memory per workload with predeclared 15% regression
limits. This is a version-to-version regression check, not an IDM comparison.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BaselineDirectory,
    [Parameter(Mandatory = $true)][string] $CandidateDirectory,
    [Parameter(Mandatory = $true)][string] $BaselineSourceSha,
    [Parameter(Mandatory = $true)][string] $CandidateSourceSha,
    [Parameter(Mandatory = $true)][string] $HarnessSha,
    [Parameter(Mandatory = $true)][string] $OutputPath,
    [ValidateRange(8, 128)][int] $FileSizeMiB = 32,
    [ValidateRange(1, 50)][double] $MaxRegressionPercent = 15
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
foreach ($sha in @($BaselineSourceSha,$CandidateSourceSha,$HarnessSha)) {
    if ($sha -notmatch '^[0-9a-fA-F]{40}$') { throw 'All app and harness SHAs must be full 40-character commits.' }
}
if ($BaselineSourceSha -eq $CandidateSourceSha) { throw 'The HyperGet and aria2 engine source SHAs must differ.' }
$expectedBytes = [long]$FileSizeMiB * 1MB
function Get-Median {
    param([double[]] $Values)
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { throw 'Cannot compute median from no observations.' }
    $middle = [int][math]::Floor($sorted.Count / 2)
    if (($sorted.Count % 2) -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}
function Read-VariantEvidence {
    param([string] $Directory,[string] $SourceSha,[string] $Variant)
    $rounds = [System.Collections.Generic.List[object]]::new()
    $seenNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $fixtureHash = $null
    $totalTransfers = 0
    for ($round = 1; $round -le 3; $round++) {
        $roundDir = Join-Path $Directory "round-$round"
        $manifestPath = Join-Path $roundDir 'verified-transfers.csv'
        $summaryPath = Join-Path $roundDir 'round-summary.csv'
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or -not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
            throw "$Variant round $round is missing verified transfer or resource evidence."
        }
        $manifest = @(Import-Csv -LiteralPath $manifestPath)
        if ($manifest.Count -ne 21) { throw "$Variant round $round expected 21 verified downloads; found $($manifest.Count)." }
        foreach ($record in $manifest) {
            if ([int]$record.Round -ne $round) {
                throw "$Variant round $round manifest has incorrect round metadata."
            }
            if ($record.SourceSha -ne $SourceSha -or $record.HarnessSha -ne $HarnessSha -or $record.Verified -ne 'True') {
                throw "$Variant manifest mixes source/harness SHAs or contains unverified transfer."
            }
            if ([long]$record.Bytes -ne $expectedBytes -or [long]$record.ExpectedBytes -ne $expectedBytes) {
                throw "$Variant output byte count differs from expected fixture size."
            }
            if ($record.SHA256 -notmatch '^[0-9a-fA-F]{64}$' -or $record.SHA256 -ne $record.ExpectedSHA256) {
                throw "$Variant output SHA-256 differs from expected fixture."
            }
            if ($null -eq $fixtureHash) { $fixtureHash = [string]$record.ExpectedSHA256 }
            elseif ($record.ExpectedSHA256 -ne $fixtureHash) { throw "$Variant rounds did not use the same deterministic fixture hash." }
            if (-not $seenNames.Add("$round/$($record.OutputName)")) { throw "$Variant has duplicate output names." }
            $jobs = [int]($record.Scenario -replace '^active-','')
            if ($jobs -notin @(1,4,16) -or [int]$record.PeakDistinctTasks -lt $jobs) {
                throw "$Variant lacks live overlap evidence for $($record.Scenario)."
            }
        }
        $summary = @(Import-Csv -LiteralPath $summaryPath)
        if ($summary.Count -ne 3) { throw "$Variant round $round expected 3 resource summaries." }
        foreach ($row in $summary) {
            if ([int]$row.Round -ne $round) {
                throw "$Variant round $round resource summary has incorrect round metadata."
            }
            if ($row.SourceSha -ne $SourceSha -or $row.HarnessSha -ne $HarnessSha) {
                throw "$Variant resource summary has mismatched source provenance."
            }
            if ([int]$row.Samples -lt 5 -or [int]$row.VerifiedFiles -ne [int]$row.ConcurrentTasks) {
                throw "$Variant resource summary has incomplete samples/files."
            }
        }
        foreach ($jobs in @(1,4,16)) {
            $scenario = "active-$jobs"
            $files = @($manifest | Where-Object { $_.Scenario -eq $scenario -and [int]$_.Round -eq $round })
            if ($files.Count -ne $jobs) { throw "$Variant $scenario has $($files.Count) files, expected $jobs." }
            $elapsed = @($files | ForEach-Object { [double]$_.ElapsedSeconds } | Sort-Object -Unique)
            $throughput = @($files | ForEach-Object { [double]$_.AggregateMiBPerSecond } | Sort-Object -Unique)
            if ($elapsed.Count -ne 1 -or $throughput.Count -ne 1 -or $elapsed[0] -le 0 -or $throughput[0] -le 0) {
                throw "$Variant $scenario duration/throughput values are missing or inconsistent."
            }
            $calculated = [math]::Round(($FileSizeMiB * $jobs) / $elapsed[0], 3)
            if ([math]::Abs($calculated - $throughput[0]) -gt 0.02) {
                throw "$Variant $scenario throughput does not match payload bytes / elapsed seconds."
            }
            $resource = @($summary | Where-Object { $_.Scenario -eq $scenario -and [int]$_.Round -eq $round })
            if ($resource.Count -ne 1) { throw "$Variant $scenario resource summary missing or duplicated." }
            $rounds.Add([pscustomobject]@{
                Variant=$Variant; SourceSha=$SourceSha; HarnessSha=$HarnessSha; Round=$round; Workload=$jobs
                ElapsedSeconds=[double]$elapsed[0]; AggregateMiBPerSecond=[double]$throughput[0]
                AverageCpuMachinePercent=[double]$resource[0].AverageCpuMachinePercent
                PeakWorkingSetMiB=[double]$resource[0].PeakWorkingSetMiB
                PeakPrivateBytesMiB=[double]$resource[0].PeakPrivateBytesMiB
                OS=[string](@($summary | Select-Object -First 1)[0].OS)
                LogicalCpuCount=[int](@($summary | Select-Object -First 1)[0].LogicalCpuCount)
            }) | Out-Null
        }
        $metricFiles = @(Get-ChildItem -LiteralPath (Join-Path $roundDir 'measurements') -File -Filter '*.csv')
        if ($metricFiles.Count -ne 3) { throw "$Variant round $round expected 3 resource CSVs; found $($metricFiles.Count)." }
        $totalTransfers += $manifest.Count
    }
    if ($totalTransfers -ne 63) { throw "$Variant total expected 63 verified downloads; found $totalTransfers." }
    return @($rounds)
}
$baseline = @(Read-VariantEvidence -Directory $BaselineDirectory -SourceSha $BaselineSourceSha -Variant 'baseline')
$candidate = @(Read-VariantEvidence -Directory $CandidateDirectory -SourceSha $CandidateSourceSha -Variant 'candidate')
$baselineFixture = @(Import-Csv -LiteralPath (Join-Path $BaselineDirectory 'round-1/verified-transfers.csv'))[0]
$candidateFixture = @(Import-Csv -LiteralPath (Join-Path $CandidateDirectory 'round-1/verified-transfers.csv'))[0]
if ($baselineFixture.ExpectedSHA256 -ne $candidateFixture.ExpectedSHA256) {
    throw 'Baseline and candidate did not use the same deterministic fixture bytes.'
}
$environment = @($baseline + $candidate | Select-Object -First 1)[0]
foreach ($row in @($baseline + $candidate)) {
    if ($row.OS -ne $environment.OS -or $row.LogicalCpuCount -ne $environment.LogicalCpuCount) {
        throw 'Baseline/candidate measurements came from different OS or logical CPU count.'
    }
}
$comparisons = [System.Collections.Generic.List[object]]::new()
$failed = $false
foreach ($jobs in @(1,4,16)) {
    $b = @($baseline | Where-Object { $_.Workload -eq $jobs })
    $c = @($candidate | Where-Object { $_.Workload -eq $jobs })
    if ($b.Count -ne 3 -or $c.Count -ne 3) { throw "Expected three paired rounds for workload $jobs." }
    $bThroughput = Get-Median -Values @($b | ForEach-Object { [double]$_.AggregateMiBPerSecond })
    $cThroughput = Get-Median -Values @($c | ForEach-Object { [double]$_.AggregateMiBPerSecond })
    $bElapsed = Get-Median -Values @($b | ForEach-Object { [double]$_.ElapsedSeconds })
    $cElapsed = Get-Median -Values @($c | ForEach-Object { [double]$_.ElapsedSeconds })
    $bWs = Get-Median -Values @($b | ForEach-Object { [double]$_.PeakWorkingSetMiB })
    $cWs = Get-Median -Values @($c | ForEach-Object { [double]$_.PeakWorkingSetMiB })
    $bPrivate = Get-Median -Values @($b | ForEach-Object { [double]$_.PeakPrivateBytesMiB })
    $cPrivate = Get-Median -Values @($c | ForEach-Object { [double]$_.PeakPrivateBytesMiB })
    $bCpu = Get-Median -Values @($b | ForEach-Object { [double]$_.AverageCpuMachinePercent })
    $cCpu = Get-Median -Values @($c | ForEach-Object { [double]$_.AverageCpuMachinePercent })
    $throughputDelta = [math]::Round((($cThroughput / $bThroughput) - 1.0) * 100.0, 2)
    $wsDelta = [math]::Round((($cWs / $bWs) - 1.0) * 100.0, 2)
    $privateDelta = [math]::Round((($cPrivate / $bPrivate) - 1.0) * 100.0, 2)
    $pass = $throughputDelta -ge (0.0 - $MaxRegressionPercent) -and $wsDelta -le $MaxRegressionPercent -and $privateDelta -le $MaxRegressionPercent
    if (-not $pass) { $failed = $true }
    $comparisons.Add([pscustomobject]@{
        Workload=$jobs; BaselineMedianSeconds=[math]::Round($bElapsed,3); CandidateMedianSeconds=[math]::Round($cElapsed,3)
        BaselineMedianMiBPerSecond=[math]::Round($bThroughput,3); CandidateMedianMiBPerSecond=[math]::Round($cThroughput,3)
        ThroughputDeltaPercent=$throughputDelta
        BaselineMedianPeakWorkingSetMiB=[math]::Round($bWs,3); CandidateMedianPeakWorkingSetMiB=[math]::Round($cWs,3); PeakWorkingSetDeltaPercent=$wsDelta
        BaselineMedianPeakPrivateMiB=[math]::Round($bPrivate,3); CandidateMedianPeakPrivateMiB=[math]::Round($cPrivate,3); PeakPrivateDeltaPercent=$privateDelta
        BaselineMedianCpuPercent=[math]::Round($bCpu,3); CandidateMedianCpuPercent=[math]::Round($cCpu,3)
        Gate = $(if ($pass) { 'PASS' } else { 'FAIL' })
    }) | Out-Null
}
$report = [System.Collections.Generic.List[string]]::new()
$report.Add('# HyperGet vs aria2 1.37.0 Stage 2 controlled Windows performance comparison')
$report.Add('')
$report.Add("- Baseline engine: HyperGet current engine; source SHA: $BaselineSourceSha")
$report.Add("- Candidate engine: aria2 1.37.0; source commit SHA: $CandidateSourceSha; official archive SHA-256 is recorded in the paired-run provenance.")
$report.Add("- Shared measurement harness SHA: $HarnessSha")
$report.Add("- Environment: $($environment.OS); logical CPUs: $($environment.LogicalCpuCount)")
$report.Add("- Workload: 32 MiB deterministic loopback HTTP fixture; 1/4/16 distinct active tasks; three paired rounds on the same Windows runner.")
$report.Add("- Transfers: 63 SHA-256-verified outputs per engine; engine order alternates each round.")
$report.Add("- Broad pre-gate: throughput regression must not exceed $MaxRegressionPercent%; peak Working Set and Private Bytes regression must not exceed $MaxRegressionPercent% per workload. A separate Stage 2 gate applies the stricter 10% throughput limit, 15% memory limit and at least +5% median throughput benefit.")
$report.Add("- This compares HyperGet's current implementation with aria2, not with Internet Download Manager (IDM).")
$report.Add('')
$report.Add('| Tasks | Baseline median s | Candidate median s | Baseline MiB/s | Candidate MiB/s | Throughput Δ | Baseline peak WS | Candidate peak WS | WS Δ | Baseline peak Private | Candidate peak Private | Private Δ | Gate |')
$report.Add('| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |')
foreach ($row in $comparisons) {
    $report.Add("| $($row.Workload) | $($row.BaselineMedianSeconds) | $($row.CandidateMedianSeconds) | $($row.BaselineMedianMiBPerSecond) | $($row.CandidateMedianMiBPerSecond) | $($row.ThroughputDeltaPercent)% | $($row.BaselineMedianPeakWorkingSetMiB) | $($row.CandidateMedianPeakWorkingSetMiB) | $($row.PeakWorkingSetDeltaPercent)% | $($row.BaselineMedianPeakPrivateMiB) | $($row.CandidateMedianPeakPrivateMiB) | $($row.PeakPrivateDeltaPercent)% | $($row.Gate) |")
}
$overall = if ($failed) { 'FAIL' } else { 'PASS' }
$report.Add('')
$report.Add("**Result=$overall**")
$report | Set-Content -LiteralPath $OutputPath -Encoding utf8
$comparisons | Export-Csv -LiteralPath ([IO.Path]::ChangeExtension($OutputPath,'.csv')) -NoTypeInformation -Encoding utf8
Get-Content -LiteralPath $OutputPath
if ($failed) { throw 'Controlled baseline/candidate performance regression gate failed; inspect comparison report and raw evidence.' }
