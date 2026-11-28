<#
.SYNOPSIS
Audits real Windows installed-app active-benchmark output and writes a report.
.DESCRIPTION
Summarizes 3 repeated, independent 1/4/16 concurrent real-network workloads
without comparing to IDM or claiming optimization. Rejects missing/inconsistent
CSV, failed hashes, incomplete overlap evidence and implausible measurements.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Directory,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [string] $HarnessSha = '',
    [ValidateRange(1, 5)][int] $Rounds = 3
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($Directory)
if ([string]::IsNullOrWhiteSpace($HarnessSha)) { $HarnessSha = $SourceSha }
if ($HarnessSha -notmatch '^[0-9a-fA-F]{40}$') { throw 'HarnessSha must be the exact 40-character checkout SHA.' }
$manifestPath = Join-Path $root 'verified-transfers.csv'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Verified download manifest is missing.' }
$manifest = @(Import-Csv -LiteralPath $manifestPath)
$expectedTransfers = $Rounds * (1 + 4 + 16)
if ($manifest.Count -ne $expectedTransfers) {
    throw "Expected $expectedTransfers SHA-verified transfers; found $($manifest.Count)."
}
$allNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($record in $manifest) {
    if ($record.SourceSha -ne $SourceSha -or $record.HarnessSha -ne $HarnessSha -or $record.Verified -ne 'True') {
        throw 'Manifest contains unverified or mismatched app/harness source.'
    }
    if ([long]$record.Bytes -ne [long]$record.ExpectedBytes -or $record.SHA256 -ne $record.ExpectedSHA256) {
        throw "Manifest size/SHA-256 does not match the expected fixture: $($record.OutputName)"
    }
    if ([double]$record.ElapsedSeconds -le 0 -or [double]$record.AggregateMiBPerSecond -le 0) {
        throw 'Manifest contains invalid elapsed-time or throughput measurements.'
    }
    if (-not $allNames.Add($record.OutputName)) {
        throw "Duplicate output in manifest: $($record.OutputName)"
    }
    $count = [int]($record.Scenario -replace '^active-', '')
    if ($count -notin @(1, 4, 16) -or [int]$record.PeakDistinctTasks -lt $count) {
        throw "Missing real task-overlap proof: $($record.OutputName)"
    }
    if ([long]$record.Bytes -le 0 -or $record.SHA256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Invalid verified output metadata for $($record.OutputName)"
    }
}
$roundSummaries = [System.Collections.Generic.List[object]]::new()
$logicalCpu = $null
$machineOs = $null
foreach ($jobs in @(1, 4, 16)) {
    for ($round = 1; $round -le $Rounds; $round++) {
        $scenario = "active-$jobs"
        $path = Join-Path $root "measurements/$scenario-round-$round.csv"
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing process-tree samples for $scenario round $round"
        }
        $rows = @(Import-Csv -LiteralPath $path)
        if ($rows.Count -lt 5) { throw "Too few process-tree samples in $path" }
        $set = [System.Collections.Generic.List[double]]::new()
        $private = [System.Collections.Generic.List[double]]::new()
        $cpu = [System.Collections.Generic.List[double]]::new()
        foreach ($sample in $rows) {
            if ($sample.SourceSha -ne $SourceSha -or $sample.HarnessSha -ne $HarnessSha -or $sample.Scenario -ne $scenario -or $sample.RunLabel -ne "round-$round") {
                throw "Mixed app/harness sources or scenarios in $path"
            }
            if ([int]$sample.ProcessCount -lt 1 -or [int]$sample.LogicalCpuCount -lt 1) {
                throw "Missing app process or CPU count in $path"
            }
            if ($null -eq $logicalCpu) { $logicalCpu = [int]$sample.LogicalCpuCount }
            if ($null -eq $machineOs) { $machineOs = $sample.OS }
            if ([int]$sample.LogicalCpuCount -ne $logicalCpu -or $sample.OS -ne $machineOs) {
                throw 'Benchmark observations came from different machine specifications.'
            }
            $working = [double]$sample.WorkingSetMiB
            $privateBytes = [double]$sample.PrivateBytesMiB
            $machineCpu = [double]$sample.CpuMachinePercent
            if (-not [double]::IsFinite($working) -or -not [double]::IsFinite($privateBytes) -or -not [double]::IsFinite($machineCpu)) {
                throw "Nonfinite metrics in $path"
            }
            if ($working -le 0 -or $privateBytes -le 0 -or $machineCpu -lt 0 -or $machineCpu -gt 100.01) {
                throw "Invalid resource metrics in $path"
            }
            $set.Add($working)
            $private.Add($privateBytes)
            $cpu.Add($machineCpu)
        }
        $workloadFiles = @($manifest | Where-Object { $_.Scenario -eq $scenario -and [int]$_.Round -eq $round })
        if ($workloadFiles.Count -ne $jobs) {
            throw "$scenario round $round is missing verified file(s)."
        }
        $cpuStat = $cpu | Measure-Object -Average -Maximum
        $memoryStat = $set | Measure-Object -Average -Maximum
        $privateStat = $private | Measure-Object -Average -Maximum
        $roundSummaries.Add([pscustomobject]@{
            SourceSha = $SourceSha; HarnessSha = $HarnessSha
            OS = $machineOs; LogicalCpuCount = $logicalCpu
            Scenario = $scenario; Round = $round; ConcurrentTasks = $jobs
            VerifiedFiles = $workloadFiles.Count; Samples = $rows.Count
            AverageCpuMachinePercent = [math]::Round($cpuStat.Average, 3)
            PeakCpuMachinePercent = [math]::Round($cpuStat.Maximum, 3)
            AverageWorkingSetMiB = [math]::Round($memoryStat.Average, 3)
            PeakWorkingSetMiB = [math]::Round($memoryStat.Maximum, 3)
            AveragePrivateBytesMiB = [math]::Round($privateStat.Average, 3)
            PeakPrivateBytesMiB = [math]::Round($privateStat.Maximum, 3)
            PeakDistinctTasks = ($workloadFiles | Measure-Object -Property PeakDistinctTasks -Maximum).Maximum
        })
    }
}
$roundSummaries | Export-Csv -LiteralPath (Join-Path $root 'round-summary.csv') -NoTypeInformation -Encoding utf8
$report = [System.Collections.Generic.List[string]]::new()
$report.Add('# Windows installed downloader: verified active transfer benchmark')
$report.Add('')
$report.Add("- App source SHA: $SourceSha")
$report.Add("- Harness source SHA: $HarnessSha")
$report.Add("- Host OS: $machineOs; logical CPUs: $logicalCpu")
$report.Add("- Verified transfers: $expectedTransfers, all SHA-256 identical to the local fixture")
$report.Add("- Workloads: 1/4/16 **distinct simultaneously transferring tasks**, $Rounds rounds each")
$report.Add('- These measure the app process tree on a GitHub-hosted Windows runner, not end-user hardware.')
$report.Add('- CPU is percent of total machine CPU across logical processors; Working Set and Private Bytes are separate metrics.')
$report.Add('- **No claim of speedup or superiority over IDM**: a comparable IDM baseline is not part of this run.')
$report.Add('')
$report.Add('| Workload | Round | Samples | Avg CPU % | Peak CPU % | Avg WS MiB | Peak WS MiB | Avg Private MiB | Peak Private MiB |')
$report.Add('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
foreach ($line in $roundSummaries) {
    $report.Add("| $($line.Scenario) | $($line.Round) | $($line.Samples) | $($line.AverageCpuMachinePercent) | $($line.PeakCpuMachinePercent) | $($line.AverageWorkingSetMiB) | $($line.PeakWorkingSetMiB) | $($line.AveragePrivateBytesMiB) | $($line.PeakPrivateBytesMiB) |")
}
$report | Set-Content -LiteralPath (Join-Path $root 'benchmark-report.md') -Encoding utf8
Write-Host "Validated $($roundSummaries.Count) Windows benchmark rounds and $expectedTransfers distinct SHA256-verified downloads."
