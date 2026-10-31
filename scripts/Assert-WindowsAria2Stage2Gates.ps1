#requires -Version 7.0
<#
.SYNOPSIS
Applies HyperGet's predeclared aria2 Stage 2 performance decision gates.
.DESCRIPTION
A NO-GO is a valid engineering result and keeps the current HyperGet engine. Missing,
corrupt, inconsistent or incomplete evidence is a test failure and exits nonzero.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ComparisonCsv,
    [Parameter(Mandatory = $true)][string] $OutputPath,
    [ValidateRange(1, 50)][double] $MaxThroughputRegressionPercent = 10,
    [ValidateRange(1, 50)][double] $MaxMemoryRegressionPercent = 15,
    [ValidateRange(0, 100)][double] $MinMedianGainPercent = 5
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ComparisonCsv -PathType Leaf)) { throw 'Stage 2 comparison CSV is missing.' }
$rows = @(Import-Csv -LiteralPath $ComparisonCsv)
if ($rows.Count -ne 3) { throw "Expected exactly three workload comparison rows; found $($rows.Count)." }
$expected = @(1, 4, 16)
$observed = @($rows | ForEach-Object { [int]$_.Workload } | Sort-Object)
if (($observed -join ',') -ne ($expected -join ',')) { throw "Expected workloads 1,4,16; observed $($observed -join ',')." }
$deltas = [System.Collections.Generic.List[double]]::new()
$gateRows = [System.Collections.Generic.List[object]]::new()
$allMandatoryGatesPass = $true
foreach ($row in $rows) {
    $throughput = [double]$row.ThroughputDeltaPercent
    $workingSet = [double]$row.PeakWorkingSetDeltaPercent
    $privateBytes = [double]$row.PeakPrivateDeltaPercent
    foreach ($value in @($throughput, $workingSet, $privateBytes)) {
        if (-not [double]::IsFinite($value)) { throw 'Comparison contains a non-finite metric.' }
    }
    $throughputPass = $throughput -ge (0 - $MaxThroughputRegressionPercent)
    $memoryPass = $workingSet -le $MaxMemoryRegressionPercent -and $privateBytes -le $MaxMemoryRegressionPercent
    $validOutputsPass = [string]$row.Gate -eq 'PASS'
    $pass = $throughputPass -and $memoryPass -and $validOutputsPass
    if (-not $pass) { $allMandatoryGatesPass = $false }
    $deltas.Add($throughput)
    $gateRows.Add([pscustomobject]@{
        Workload = [int]$row.Workload
        ThroughputDeltaPercent = $throughput
        PeakWorkingSetDeltaPercent = $workingSet
        PeakPrivateDeltaPercent = $privateBytes
        ExistingIntegrityAndBroadGate = [string]$row.Gate
        ThroughputGate = if ($throughputPass) { 'PASS' } else { 'FAIL' }
        MemoryGate = if ($memoryPass) { 'PASS' } else { 'FAIL' }
        Result = if ($pass) { 'PASS' } else { 'FAIL' }
    }) | Out-Null
}
$sortedDeltas = @($deltas | Sort-Object)
$medianGain = $sortedDeltas[1]
$meaningfulGain = $medianGain -ge $MinMedianGainPercent
$performanceGo = $allMandatoryGatesPass -and $meaningfulGain
$decision = if ($performanceGo) { 'STAGE2_PERFORMANCE_GO' } else { 'NO_GO_KEEP_HYPERGET_ENGINE' }
$report = [System.Collections.Generic.List[string]]::new()
$report.Add('# HyperGet vs aria2 — Stage 2 performance decision')
$report.Add('')
$report.Add("- Decision: **$decision**")
$report.Add("- Throughput regression limit per workload: $MaxThroughputRegressionPercent%.")
$report.Add("- Peak Working Set and Private Bytes regression limits per workload: $MaxMemoryRegressionPercent%.")
$report.Add("- Meaningful benefit gate: median of the three workload throughput deltas must be at least +$MinMedianGainPercent%.")
$report.Add("- Median throughput delta across workloads: $([math]::Round($medianGain, 2))%.")
$report.Add('- Correctness is a hard gate: all 63 outputs per engine must match the same 32 MiB fixture SHA-256, with real 1/4/16 task overlap and three paired rounds.')
$report.Add('- The memory comparison is process-tree footprint (HyperGet GUI/worker tree vs aria2 transfer-process tree); treat it as a product-footprint signal, not an isolated native-core microbenchmark.')
$report.Add('- A performance GO is **not** production migration approval. A custom no-BitTorrent build, GPL-2.0-or-later distribution/compliance review, security review and adapter parity/failure-injection tests remain mandatory.')
$report.Add('')
$report.Add('| Tasks | Throughput Δ | Peak Working Set Δ | Peak Private Bytes Δ | Broad integrity/resource gate | Throughput gate | Memory gate | Result |')
$report.Add('| ---: | ---: | ---: | ---: | --- | --- | --- | --- |')
foreach ($row in $gateRows) {
    $report.Add("| $($row.Workload) | $($row.ThroughputDeltaPercent)% | $($row.PeakWorkingSetDeltaPercent)% | $($row.PeakPrivateDeltaPercent)% | $($row.ExistingIntegrityAndBroadGate) | $($row.ThroughputGate) | $($row.MemoryGate) | $($row.Result) |")
}
$report.Add('')
$report.Add("**Result=$decision**")
$report | Set-Content -LiteralPath $OutputPath -Encoding utf8
$gateRows | Export-Csv -LiteralPath ([IO.Path]::ChangeExtension($OutputPath, '.csv')) -NoTypeInformation -Encoding utf8
Get-Content -LiteralPath $OutputPath
