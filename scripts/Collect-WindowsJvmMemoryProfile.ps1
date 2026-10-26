<# 
.SYNOPSIS
Collect a post-transfer JVM heap/class/thread snapshot from the installed HyperGet GUI.
.DESCRIPTION
Run only after the benchmark's process-resource samples are complete. The profile is
diagnostic, not a performance gate. Evidence contains test-runner process diagnostics;
it must never be reused against a user's personal installation.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $GuiExecutable,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$root = [IO.Path]::GetFullPath($OutputDirectory)
if (-not (Test-Path -LiteralPath $guiPath -PathType Leaf)) {
    throw 'Installed HyperGet GUI executable is missing.'
}
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw 'JVM profile output directory must be empty.'
    }
}
New-Item -ItemType Directory -Path $root -Force | Out-Null
$jcmd = Join-Path $env:JAVA_HOME 'bin\jcmd.exe'
if (-not (Test-Path -LiteralPath $jcmd -PathType Leaf)) {
    throw 'JDK jcmd.exe is unavailable; JVM memory profile cannot be collected.'
}
$jcmdList = & $jcmd -l 2>&1
$jcmdList | Set-Content -LiteralPath (Join-Path $root 'jcmd-process-list.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) {
    throw "jcmd process enumeration failed with exit code $LASTEXITCODE."
}
# The NSIS launcher is a tiny native stub, not the JVM. jcmd -l identifies the
# actual HotSpot process by its main class; attach only to that PID.
$jvmMatch = [regex]::Match(
    ($jcmdList -join "`n"),
    '(?m)^\s*(\d+)\s+com\.abdownloadmanager\.desktop\.AppKt(?:\s|$)'
)
if (-not $jvmMatch.Success) {
    throw 'Installed HyperGet JVM main class was not found in jcmd -l output.'
}
$javaPid = [int]$jvmMatch.Groups[1].Value
$javaProcess = Get-Process -Id $javaPid -ErrorAction Stop
$processSnapshot = [pscustomobject]@{
    SourceSha = $SourceSha
    CapturedUtc = [DateTime]::UtcNow.ToString('o')
    JvmProcessId = $javaPid
    JvmProcessName = $javaProcess.ProcessName
    WorkingSetMiB = [Math]::Round($javaProcess.WorkingSet64 / 1MB, 3)
    PrivateBytesMiB = [Math]::Round($javaProcess.PrivateMemorySize64 / 1MB, 3)
    Handles = $javaProcess.HandleCount
    Threads = $javaProcess.Threads.Count
}
$processSnapshot | ConvertTo-Json -Depth 3 |
    Set-Content -LiteralPath (Join-Path $root 'process-snapshot.json') -Encoding utf8
$commands = @(
    @{ Name = 'vm-version'; Command = 'VM.version' },
    @{ Name = 'heap-info'; Command = 'GC.heap_info' },
    @{ Name = 'class-histogram'; Command = 'GC.class_histogram' },
    @{ Name = 'thread-dump'; Command = 'Thread.print' }
)
foreach ($entry in $commands) {
    $target = Join-Path $root ($entry.Name + '.txt')
    $output = & $jcmd $javaPid $entry.Command 2>&1
    $exitCode = $LASTEXITCODE
    $output | Set-Content -LiteralPath $target -Encoding utf8
    if ($exitCode -ne 0) {
        throw "JVM diagnostic command $($entry.Command) failed with exit code $exitCode; output retained."
    }
}
@(
    "SourceSHA=$SourceSha"
    "GuiProcessId=$($gui.Id)"
    "JcmdVersion=$((& $jcmd -J-version 2>&1 | Select-Object -First 1) -join ' ')"
    "ProfileStatus=PASS"
) | Set-Content -LiteralPath (Join-Path $root 'profile-status.txt') -Encoding utf8
Write-Host "JVM heap/class/thread profile captured for installed HyperGet JVM PID $javaPid."
