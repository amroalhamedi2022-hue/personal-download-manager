<#
.SYNOPSIS
Runs a repeatable 3x warm-idle and 3x minimized-idle process-tree benchmark.
.DESCRIPTION
The installed application must already be running. Captures real Working Set,
Private Bytes, CPU, handles and threads via the existing validated sampler.
Never simulates or guesses measurements, and never deletes app/user data.
#>
[CmdletBinding()]
param(
    [string] $RootProcessName = 'DownloadManager',
    [Parameter(Mandatory = $true)]
    [string] $SourceSha,
    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory,
    [ValidateRange(1, 12)]
    [int] $Rounds = 3,
    [ValidateRange(5, 180)]
    [int] $DurationSeconds = 20
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$target = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $target -Force | Out-Null
$sampler = Join-Path $PSScriptRoot 'Measure-WindowsDownloadManager.ps1'
if (-not (Test-Path -LiteralPath $sampler)) { throw "Missing Windows measurement tool: $sampler" }

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class IdmBenchmarkWindow {
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool ShowWindow(IntPtr handle, int mode);
}
'@ -ErrorAction Stop

$windows = @(Get-Process -ErrorAction SilentlyContinue |
    Where-Object { $_.ProcessName -ieq $RootProcessName -and $_.MainWindowHandle -ne [IntPtr]::Zero })
if ($windows.Count -eq 0) { throw "No visible window exists for process: $RootProcessName" }

try {
    foreach ($scenario in @('idle-warm', 'idle-minimized')) {
        if ($scenario -eq 'idle-minimized') {
            foreach ($window in $windows) {
                [void][IdmBenchmarkWindow]::ShowWindow($window.MainWindowHandle, 6)
            }
            Start-Sleep -Seconds 2
        }
        for ($round = 1; $round -le $Rounds; $round++) {
            $outputCsv = Join-Path $target "$scenario-round-$round.csv"
            Write-Host "Process-tree benchmark $scenario — round $round/$Rounds"
            & $sampler -RootProcessName $RootProcessName -Scenario $scenario -SourceSha $SourceSha -OutputCsv $outputCsv -DurationSeconds $DurationSeconds -SampleIntervalMs 1000 -WarmupSeconds 2 -RunLabel "round-$round"
            if (-not (Test-Path -LiteralPath $outputCsv -PathType Leaf)) {
                throw "Measurement CSV was not produced: $outputCsv"
            }
        }
    }
} finally {
    foreach ($window in $windows) {
        if (-not $window.HasExited) {
            [void][IdmBenchmarkWindow]::ShowWindow($window.MainWindowHandle, 9)
        }
    }
}

$files = @(Get-ChildItem -LiteralPath $target -Filter '*.csv' -File)
if ($files.Count -ne ($Rounds * 2)) {
    throw "Expected $($Rounds * 2) CSV files, found $($files.Count)"
}
Write-Host "Captured $($files.Count) real measurement files in $target"
