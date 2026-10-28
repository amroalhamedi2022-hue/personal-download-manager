#requires -Version 7.0
<#
.SYNOPSIS
Runs a paired, same-runner HyperGet-vs-aria2 Stage 2 performance evaluation.
.DESCRIPTION
Three paired rounds alternate engine order, verify every HTTP payload against the
same deterministic 32 MiB fixture, and capture CPU/Working Set/Private Bytes.
Also measures aria2 single-file segmentation at 1/4/8 connections. This is evaluation
only: the stock aria2 binary is never copied into HyperGet and no Torrent input is used.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $HyperGetGuiExecutable,
    [Parameter(Mandatory = $true)][string] $HyperGetCliExecutable,
    [Parameter(Mandatory = $true)][string] $Aria2Executable,
    [Parameter(Mandatory = $true)][string] $HyperGetSourceSha,
    [Parameter(Mandatory = $true)][string] $Aria2SourceSha,
    [Parameter(Mandatory = $true)][string] $Aria2ArchiveSha256,
    [Parameter(Mandatory = $true)][string] $HyperGetInstallerSha256,
    [Parameter(Mandatory = $true)][string] $HarnessSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [ValidateRange(3, 3)][int] $Rounds = 3,
    [ValidateRange(8, 128)][int] $FileSizeMiB = 32,
    [ValidateRange(5, 60)][int] $SampleSeconds = 8,
    [ValidateRange(10, 500)][int] $ServerChunkDelayMs = 500
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
foreach ($sha in @($HyperGetSourceSha, $Aria2SourceSha, $HarnessSha)) {
    if ($sha -notmatch '^[0-9a-fA-F]{40}$') { throw 'HyperGetSourceSha, Aria2SourceSha and HarnessSha must be full 40-character commits.' }
}
foreach ($sha in @($Aria2ArchiveSha256, $HyperGetInstallerSha256)) {
    if ($sha -notmatch '^[0-9a-fA-F]{64}$') { throw 'Installer and aria2 archive provenance must be full SHA-256 values.' }
}
$gui = [IO.Path]::GetFullPath($HyperGetGuiExecutable)
$cli = [IO.Path]::GetFullPath($HyperGetCliExecutable)
$aria2 = [IO.Path]::GetFullPath($Aria2Executable)
foreach ($path in @($gui, $cli, $aria2)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required executable missing: $path" }
}
$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) { throw 'Paired Stage 2 output directory must be absent or empty.' }
} else {
    New-Item -ItemType Directory -Force -Path $root | Out-Null
}
$hyperGetRoot = Join-Path $root 'hyperget'
$aria2Root = Join-Path $root 'aria2'
$segmentationRoot = Join-Path $root 'aria2-segmentation'
New-Item -ItemType Directory -Force -Path $hyperGetRoot,$aria2Root,$segmentationRoot | Out-Null
$matrixScript = Join-Path $PSScriptRoot 'Run-WindowsActiveBenchmarkMatrix.ps1'
$summarizeScript = Join-Path $PSScriptRoot 'Summarize-WindowsActiveBenchmark.ps1'
$aria2MatrixScript = Join-Path $PSScriptRoot 'Run-WindowsAria2ActiveBenchmarkMatrix.ps1'
$compareScript = Join-Path $PSScriptRoot 'Compare-WindowsAria2Stage2.ps1'
$gatesScript = Join-Path $PSScriptRoot 'Assert-WindowsAria2Stage2Gates.ps1'
foreach ($path in @($matrixScript,$summarizeScript,$aria2MatrixScript,$compareScript,$gatesScript)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required Stage 2 helper missing: $path" }
}
$referenceFixture = Join-Path $root 'reference-fixture.bin'
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $referenceFixture $FileSizeMiB
if ($LASTEXITCODE -ne 0) { throw 'Failed to create the reference fixture.' }
$expectedBytes = [long]$FileSizeMiB * 1MB
if ((Get-Item -LiteralPath $referenceFixture).Length -ne $expectedBytes) { throw 'Reference fixture byte count is invalid.' }
$referenceSha = (Get-FileHash -LiteralPath $referenceFixture -Algorithm SHA256).Hash.ToLowerInvariant()
Remove-Item -LiteralPath $referenceFixture -Force
$aria2Version = (& $aria2 --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $aria2Version -notmatch 'aria2 version 1\.37\.0') { throw 'Stage 2 requires pinned aria2 1.37.0.' }
$bitTorrentEnabled = $aria2Version -match '(?i)\bBitTorrent\b'
$machineOs = (Get-CimInstance Win32_OperatingSystem).Caption
$logicalCpu = [Environment]::ProcessorCount
$pairedOrder = [System.Collections.Generic.List[string]]::new()
$provenance = [ordered]@{
    StartedUtc = [DateTime]::UtcNow.ToString('o')
    HyperGetSourceSha = $HyperGetSourceSha
    HyperGetInstallerSHA256 = $HyperGetInstallerSha256
    Aria2SourceSha = $Aria2SourceSha
    Aria2ArchiveSHA256 = $Aria2ArchiveSha256
    HarnessSha = $HarnessSha
    Aria2Version = '1.37.0'
    Aria2BitTorrentEnabledInStockBinary = $bitTorrentEnabled
    TorrentWorkloadInvoked = $false
    Aria2RpcEnabled = $false
    License = 'GPL-2.0-or-later; evaluation-only, no binary bundling in this milestone'
    OS = $machineOs
    LogicalCpuCount = $logicalCpu
    FileSizeMiB = $FileSizeMiB
    ExpectedBytesPerTask = $expectedBytes
    ReferenceFixtureSHA256 = $referenceSha
    PairedRounds = $Rounds
    Workloads = @(1,4,16)
    Aria2SegmentsForConcurrencyMatrix = 8
    Aria2SingleFileSegmentationSweep = @(1,4,8)
    ServerChunkDelayMs = $ServerChunkDelayMs
    TaskChunkDelayMs = 180
}
$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'provenance.json') -Encoding utf8

function Set-OuterRoundMetadata {
    param([string] $CsvPath, [int] $Round)
    if (-not (Test-Path -LiteralPath $CsvPath -PathType Leaf)) { throw "Missing round metadata CSV: $CsvPath" }
    $rows = @(Import-Csv -LiteralPath $CsvPath)
    if ($rows.Count -eq 0) { throw "Empty round metadata CSV: $CsvPath" }
    foreach ($row in $rows) {
        if ([int]$row.Round -ne 1) { throw "Expected local round 1 before outer round normalization in $CsvPath." }
        $row.Round = [string]$Round
    }
    $rows | Export-Csv -LiteralPath $CsvPath -NoTypeInformation -Encoding utf8
}
function Remove-VerifiedPayloads {
    param([string] $Directory)
    $downloads = Join-Path $Directory 'downloads'
    $fixture = Join-Path $Directory 'source-fixture.bin'
    if (Test-Path -LiteralPath $downloads) { Remove-Item -LiteralPath $downloads -Recurse -Force }
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Force }
}
function Run-HyperGetRound {
    param([int] $Round)
    $roundDir = Join-Path $hyperGetRoot "round-$Round"
    if (Test-Path -LiteralPath $roundDir) { throw "HyperGet round output already exists: $roundDir" }
    Write-Host "PAIR_ORDER round=$Round engine=HyperGet"
    & $matrixScript -GuiExecutable $gui -CliExecutable $cli -SourceSha $HyperGetSourceSha -HarnessSha $HarnessSha -OutputDirectory $roundDir -Rounds 1 -SampleSeconds $SampleSeconds -ServerChunkDelayMs $ServerChunkDelayMs -FileSizeMiB $FileSizeMiB -CompletionTimeoutSeconds 300
    & $summarizeScript -Directory $roundDir -SourceSha $HyperGetSourceSha -HarnessSha $HarnessSha -Rounds 1 | Out-Null
    Set-OuterRoundMetadata -CsvPath (Join-Path $roundDir 'verified-transfers.csv') -Round $Round
    Set-OuterRoundMetadata -CsvPath (Join-Path $roundDir 'round-summary.csv') -Round $Round
    $manifest = @(Import-Csv -LiteralPath (Join-Path $roundDir 'verified-transfers.csv'))
    $summary = @(Import-Csv -LiteralPath (Join-Path $roundDir 'round-summary.csv'))
    if ($manifest.Count -ne 21 -or $summary.Count -ne 3) { throw "HyperGet round $Round has incomplete manifest/summary evidence." }
    foreach ($row in $manifest) {
        if ([int]$row.Round -ne $Round -or $row.ExpectedSHA256 -ne $referenceSha -or $row.SHA256 -ne $referenceSha -or $row.Verified -ne 'True') {
            throw "HyperGet round $Round has an unverified or wrong-fixture output."
        }
    }
    Remove-VerifiedPayloads -Directory $roundDir
    Write-Host "PASS: HyperGet paired round $Round; 21 outputs match fixture SHA256 $referenceSha."
}
function Run-Aria2Round {
    param([int] $Round)
    $roundDir = Join-Path $aria2Root "round-$Round"
    if (Test-Path -LiteralPath $roundDir) { throw "aria2 round output already exists: $roundDir" }
    Write-Host "PAIR_ORDER round=$Round engine=aria2"
    & $aria2MatrixScript -Aria2Executable $aria2 -SourceSha $Aria2SourceSha -HarnessSha $HarnessSha -EngineArchiveSha256 $Aria2ArchiveSha256 -OutputDirectory $roundDir -Rounds 1 -RoundStart $Round -Workloads @(1,4,16) -Segments 8 -SampleSeconds $SampleSeconds -ServerChunkDelayMs $ServerChunkDelayMs -FileSizeMiB $FileSizeMiB -CompletionTimeoutSeconds 300
    $manifest = @(Import-Csv -LiteralPath (Join-Path $roundDir 'verified-transfers.csv'))
    $summary = @(Import-Csv -LiteralPath (Join-Path $roundDir 'round-summary.csv'))
    if ($manifest.Count -ne 21 -or $summary.Count -ne 3) { throw "aria2 round $Round has incomplete manifest/summary evidence." }
    foreach ($row in $manifest) {
        if ([int]$row.Round -ne $Round -or $row.ExpectedSHA256 -ne $referenceSha -or $row.SHA256 -ne $referenceSha -or $row.Verified -ne 'True') {
            throw "aria2 round $Round has an unverified or wrong-fixture output."
        }
    }
    Write-Host "PASS: aria2 paired round $Round; 21 outputs match fixture SHA256 $referenceSha."
}
for ($round = 1; $round -le $Rounds; $round++) {
    if ($round % 2 -eq 1) {
        $pairedOrder.Add("round-$round:HyperGet-then-aria2")
        Run-HyperGetRound -Round $round
        Run-Aria2Round -Round $round
    } else {
        $pairedOrder.Add("round-$round:aria2-then-HyperGet")
        Run-Aria2Round -Round $round
        Run-HyperGetRound -Round $round
    }
}
$comparisonPath = Join-Path $root 'paired-comparison.md'
$comparisonCsv = [IO.Path]::ChangeExtension($comparisonPath, '.csv')
try {
    & $compareScript -BaselineDirectory $hyperGetRoot -CandidateDirectory $aria2Root -BaselineSourceSha $HyperGetSourceSha -CandidateSourceSha $Aria2SourceSha -HarnessSha $HarnessSha -OutputPath $comparisonPath -FileSizeMiB $FileSizeMiB -MaxRegressionPercent 15
} catch {
    if (-not (Test-Path -LiteralPath $comparisonCsv -PathType Leaf)) {
        throw "The comparison could not produce a validated result table: $($_.Exception.Message)"
    }
    Write-Warning "The broad comparator recorded a threshold failure; applying the stricter Stage 2 decision gate to its CSV: $($_.Exception.Message)"
}
if (-not (Test-Path -LiteralPath $comparisonCsv -PathType Leaf)) { throw 'The paired comparison CSV was not created.' }
$decisionPath = Join-Path $root 'stage2-performance-decision.md'
& $gatesScript -ComparisonCsv $comparisonCsv -OutputPath $decisionPath -MaxThroughputRegressionPercent 10 -MaxMemoryRegressionPercent 15 -MinMedianGainPercent 5

$segmentationRows = [System.Collections.Generic.List[object]]::new()
foreach ($segments in @(1, 4, 8)) {
    $segmentDir = Join-Path $segmentationRoot "segments-$segments"
    if (Test-Path -LiteralPath $segmentDir) { throw "aria2 segmentation output already exists: $segmentDir" }
    Write-Host "SEGMENTATION_SWEEP segments=$segments rounds=3 workload=1"
    & $aria2MatrixScript -Aria2Executable $aria2 -SourceSha $Aria2SourceSha -HarnessSha $HarnessSha -EngineArchiveSha256 $Aria2ArchiveSha256 -OutputDirectory $segmentDir -Rounds 3 -RoundStart 1 -Workloads @(1) -Segments $segments -SampleSeconds $SampleSeconds -ServerChunkDelayMs $ServerChunkDelayMs -FileSizeMiB $FileSizeMiB -CompletionTimeoutSeconds 300
    $manifest = @(Import-Csv -LiteralPath (Join-Path $segmentDir 'verified-transfers.csv'))
    $summary = @(Import-Csv -LiteralPath (Join-Path $segmentDir 'round-summary.csv'))
    if ($manifest.Count -ne 3 -or $summary.Count -ne 3) { throw "aria2 $segments-segment sweep did not produce three verified rounds." }
    foreach ($row in $manifest) {
        if ($row.ExpectedSHA256 -ne $referenceSha -or $row.SHA256 -ne $referenceSha -or $row.Verified -ne 'True') {
            throw "aria2 $segments-segment sweep has a bad output hash."
        }
    }
    $throughput = @($manifest | ForEach-Object { [double]$_.AggregateMiBPerSecond } | Sort-Object)
    $ws = @($summary | ForEach-Object { [double]$_.PeakWorkingSetMiB } | Sort-Object)
    $privateBytes = @($summary | ForEach-Object { [double]$_.PeakPrivateBytesMiB } | Sort-Object)
    $segmentationRows.Add([pscustomobject]@{
        Segments = $segments; Rounds = $manifest.Count; VerifiedOutputs = $manifest.Count
        MedianMiBPerSecond = [math]::Round($throughput[1], 3)
        MedianPeakWorkingSetMiB = [math]::Round($ws[1], 3)
        MedianPeakPrivateBytesMiB = [math]::Round($privateBytes[1], 3)
        FixtureSHA256 = $referenceSha; Result = 'PASS'
    }) | Out-Null
}
$segmentationCsv = Join-Path $root 'aria2-segmentation-summary.csv'
$segmentationRows | Export-Csv -LiteralPath $segmentationCsv -NoTypeInformation -Encoding utf8
$segmentationReport = [System.Collections.Generic.List[string]]::new()
$segmentationReport.Add('# aria2 single-file segmentation sweep (supplemental)')
$segmentationReport.Add('')
$segmentationReport.Add('- Three SHA-256-verified 32 MiB downloads per setting on the same Windows runner.')
$segmentationReport.Add('- This is an aria2-only sensitivity check; HyperGet segmentation is not assumed to be configurable through the current active-benchmark CLI.')
$segmentationReport.Add('- It is not a substitute for the paired 1/4/16 comparison and does not authorize migration.')
$segmentationReport.Add('')
$segmentationReport.Add('| Connections per file | Verified rounds | Median MiB/s | Median peak Working Set MiB | Median peak Private Bytes MiB | Result |')
$segmentationReport.Add('| ---: | ---: | ---: | ---: | ---: | --- |')
foreach ($row in $segmentationRows) {
    $segmentationReport.Add("| $($row.Segments) | $($row.Rounds) | $($row.MedianMiBPerSecond) | $($row.MedianPeakWorkingSetMiB) | $($row.MedianPeakPrivateBytesMiB) | $($row.Result) |")
}
$segmentationReport | Set-Content -LiteralPath (Join-Path $root 'aria2-segmentation-summary.md') -Encoding utf8
$decisionMatch = @(Select-String -Path $decisionPath -Pattern '^\*\*Result=([^*]+)\*\*$')
if ($decisionMatch.Count -ne 1) { throw 'Unable to extract the Stage 2 decision from its report.' }
$provenance['PairedOrder'] = @($pairedOrder)
$provenance['Stage2Decision'] = $decisionMatch[0].Matches[0].Groups[1].Value
$provenance['SegmentationSweep'] = @($segmentationRows)
$provenance['CompletedUtc'] = [DateTime]::UtcNow.ToString('o')
$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'provenance.json') -Encoding utf8
Write-Host "STAGE2_DECISION=$($provenance['Stage2Decision'])"
Write-Host 'STAGE2_SCOPE=performance-only; production migration remains gated by no-BitTorrent build, GPL/security review and adapter parity.'
