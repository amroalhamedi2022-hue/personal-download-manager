<#
.SYNOPSIS
Samples Windows process-tree memory/CPU for reproducible downloader benchmarks.
.DESCRIPTION
Run this separately for our app and IDM with the same hardware, workload and duration.
Never conflates Working Set with Private Bytes; does not claim "Private Working Set".
Example:
  pwsh -NoProfile -File scripts/Measure-WindowsDownloadManager.ps1 -RootProcessName DownloadManager -Scenario idle-warm -SourceSha abc123 -OutputCsv results/idle.csv
  pwsh -NoProfile -File scripts/Measure-WindowsDownloadManager.ps1 -RootProcessName IDMan -Scenario idle-warm -SourceSha idm-version -OutputCsv results/idm-idle.csv
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $RootProcessName,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Scenario,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $SourceSha,
    [string] $HarnessSha = '',
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $OutputCsv,
    [ValidateRange(5, 7200)]
    [int] $DurationSeconds = 60,
    [ValidateRange(250, 10000)]
    [int] $SampleIntervalMs = 1000,
    [ValidateRange(0, 600)]
    [int] $WarmupSeconds = 5,
    [string] $RunLabel = "run-1"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$logicalProcessors = [Environment]::ProcessorCount
$rootName = [IO.Path]::GetFileNameWithoutExtension($RootProcessName)
if ([string]::IsNullOrWhiteSpace($HarnessSha)) { $HarnessSha = $SourceSha }
if ($HarnessSha -notmatch '^[0-9a-fA-F]{40}$') { throw 'HarnessSha must be the exact 40-character checkout SHA.' }
$csvPath = [IO.Path]::GetFullPath($OutputCsv)
$csvFolder = Split-Path $csvPath -Parent
if (-not (Test-Path -LiteralPath $csvFolder)) {
    New-Item -ItemType Directory -Force -Path $csvFolder | Out-Null
}

function Get-Snapshot {
    param([string] $Name)
    $processRecords = @(Get-CimInstance -ClassName Win32_Process |
        Select-Object ProcessId, ParentProcessId, Name, ExecutablePath)
    $roots = @($processRecords | Where-Object {
        [IO.Path]::GetFileNameWithoutExtension([string]$_.Name) -ieq $Name
    })
    if ($roots.Count -eq 0) {
        throw "No root process found: $Name. Start the app before running measurements."
    }
    $selectedIds = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($root in $roots) { [void]$selectedIds.Add([int]$root.ProcessId) }
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($entry in $processRecords) {
            if ($selectedIds.Contains([int]$entry.ParentProcessId)) {
                if ($selectedIds.Add([int]$entry.ProcessId)) { $changed = $true }
            }
        }
    }
    $procs = @()
    foreach ($pidValue in $selectedIds) {
        try {
            $p = Get-Process -Id $pidValue -ErrorAction Stop
            if (-not $p.HasExited) { $procs += $p }
        } catch [System.Exception] {
            # Process ended between CIM enumeration and Get-Process.
        }
    }
    return $procs
}

$os = (Get-CimInstance Win32_OperatingSystem).Caption
$start = (Get-Date).ToUniversalTime()
if ($WarmupSeconds -gt 0) { Start-Sleep -Seconds $WarmupSeconds }
$previousCpu = @{}
$previousAt = [DateTime]::UtcNow
$rows = [System.Collections.Generic.List[object]]::new()
$timer = [Diagnostics.Stopwatch]::StartNew()
while ($timer.Elapsed.TotalSeconds -lt $DurationSeconds) {
    $procs = @(Get-Snapshot -Name $rootName)
    $at = [DateTime]::UtcNow
    $elapsedSeconds = [math]::Max(0.001, ($at - $previousAt).TotalSeconds)
    $cpuSeconds = 0.0
    $currentCpu = @{}
    $workingSetBytes = [long]0
    $privateBytes = [long]0
    $handles = 0
    $threads = 0
    foreach ($p in $procs) {
        try {
            $procStartTicks = $p.StartTime.ToUniversalTime().Ticks
            $identity = "$($p.Id):$procStartTicks"
            $totalSeconds = $p.TotalProcessorTime.TotalSeconds
            $currentCpu[$identity] = $totalSeconds
            if ($previousCpu.ContainsKey($identity)) {
                $cpuSeconds += [math]::Max(0.0, $totalSeconds - [double]$previousCpu[$identity])
            }
            $workingSetBytes += [long]$p.WorkingSet64
            $privateBytes += [long]$p.PrivateMemorySize64
            $handles += [int]$p.HandleCount
            $threads += [int]$p.Threads.Count
        } catch [System.Exception] {
            # Process exited mid-sample.
        }
    }
    $percentMachine = [math]::Round((100.0 * $cpuSeconds / $elapsedSeconds / $logicalProcessors), 3)
    $rows.Add([pscustomobject]@{
        TimestampUtc = $at.ToString('o')
        SourceSha = $SourceSha
        HarnessSha = $HarnessSha
        Scenario = $Scenario
        RunLabel = $RunLabel
        OS = $os
        LogicalCpuCount = $logicalProcessors
        RootProcessName = $rootName
        ProcessCount = $procs.Count
        WorkingSetMiB = [math]::Round($workingSetBytes / 1MB, 3)
        PrivateBytesMiB = [math]::Round($privateBytes / 1MB, 3)
        CpuMachinePercent = $percentMachine
        HandleCount = $handles
        ThreadCount = $threads
    }) | Out-Null
    $previousCpu = $currentCpu
    $previousAt = $at
    Start-Sleep -Milliseconds $SampleIntervalMs
}
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8
$ram = @($rows | Measure-Object -Property WorkingSetMiB -Average -Maximum)
$private = @($rows | Measure-Object -Property PrivateBytesMiB -Average -Maximum)
$cpu = @($rows | Measure-Object -Property CpuMachinePercent -Average -Maximum)
[pscustomobject]@{
    SampleCount = $rows.Count
    OutputCsv = $csvPath
    StartedUtc = $start.ToString('o')
    Scenario = $Scenario
    SourceSha = $SourceSha
    HarnessSha = $HarnessSha
    AverageWorkingSetMiB = [math]::Round($ram[0].Average, 3)
    PeakWorkingSetMiB = [math]::Round($ram[0].Maximum, 3)
    AveragePrivateBytesMiB = [math]::Round($private[0].Average, 3)
    PeakPrivateBytesMiB = [math]::Round($private[0].Maximum, 3)
    AverageMachineCpuPercent = [math]::Round($cpu[0].Average, 3)
    PeakMachineCpuPercent = [math]::Round($cpu[0].Maximum, 3)
} | Format-List
