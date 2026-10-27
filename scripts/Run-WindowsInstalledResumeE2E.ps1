<#
.SYNOPSIS
Real installed Windows HTTP pause/resume acceptance via GUI IPC and CLI.
.DESCRIPTION
Verifies sockets were really open, then fully stopped after Pause, stay
stopped, and that Resume yields the exact original SHA256 bytes.
Uses only a 127.0.0.1 deterministic fixture and temporary runner directory.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $GuiExecutable,
    [Parameter(Mandatory = $true)][string] $CliExecutable,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [Parameter(Mandatory = $true)][string] $SourceSha
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')
$folder = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$source = Join-Path $folder 'source-fixture.bin'
$portFile = Join-Path $folder 'port.txt'
$downloads = Join-Path $folder 'downloaded'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*131072)' $source
if ($LASTEXITCODE -ne 0) { throw 'Cannot create deterministic 32 MiB fixture.' }
$expectedSize = [long]32 * 1MB
$expectedHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$fixtureScript = Join-Path $PSScriptRoot 'LocalHttpRangeFixture.py'
$server = $null
$app = $null
try {
    $server = Start-Process -FilePath 'python' -ArgumentList @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $source + '"'),
        '--port-file', ('"' + $portFile + '"'), '--chunk-delay-ms', '400'
    ) -PassThru -RedirectStandardOutput (Join-Path $folder 'server-stdout.txt') -RedirectStandardError (Join-Path $folder 'server-stderr.txt')
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portFile -Server $server -FixtureName 'HTTP pause/resume fixture' -Attempts 60 -ServerErrorPath (Join-Path $folder 'server-stderr.txt')
    $statsUrl = "http://127.0.0.1:$port/stats"
    $url = "http://127.0.0.1:$port/fixture.bin?task=real-pause-resume"
    $app = Start-Process -FilePath ([IO.Path]::GetFullPath($GuiExecutable)) -PassThru
    Start-Sleep -Seconds 5
    if ($app.HasExited) { throw 'Installed desktop GUI exited before pause/resume test.' }

    $name = 'windows-paused-and-resumed.bin'
    $cli = [IO.Path]::GetFullPath($CliExecutable)
    $output = @(& $cli download add http --link $url --folder $downloads --name $name --start 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Failed to start actual download: $($output -join ' ')" }
    $ids = @($output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
    if ($ids.Count -ne 1) { throw "Expected one download task ID: $($output -join ' ')" }
    $id = [long]$ids[0]
    $wasTransferring = $false
    for ($i = 0; $i -lt 80; $i++) {
        $stats = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
        if ([int]$stats.active_requests -gt 0 -and [long]$stats.bytes_sent -gt 0) {
            $wasTransferring = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $wasTransferring) { throw 'No actual HTTP bytes were transferred before Pause.' }

    & $cli download pause $id
    if ($LASTEXITCODE -ne 0) { throw 'Installed CLI Pause command failed.' }
    $stopped = $false
    for ($i = 0; $i -lt 60; $i++) {
        $stats = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
        if ([int]$stats.active_requests -eq 0) {
            $stopped = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $stopped) {
        $diagnostic = $stats | ConvertTo-Json -Compress -Depth 8
        Write-Host "HTTP pause socket diagnostics: $diagnostic"
        $diagnostic | Set-Content -LiteralPath (Join-Path $folder 'pause-failure-stats.json') -Encoding utf8
        throw "Pause did not close all active HTTP response bodies. Stats=$diagnostic"
    }
    $atPause = [long]$stats.bytes_sent
    Start-Sleep -Seconds 2
    $idleStats = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
    if ([int]$idleStats.active_requests -ne 0 -or [long]$idleStats.bytes_sent -ne $atPause) {
        throw 'HTTP bytes continued transferring after Pause returned.'
    }
    Write-Host "Verified stopped network sockets after pause of download ID $id."
    & $cli download resume $id
    if ($LASTEXITCODE -ne 0) { throw 'Installed CLI Resume command failed.' }

    $finished = Join-Path $downloads $name
    $success = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(150)
    while ([DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
        if (-not (Test-Path -LiteralPath $finished -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $finished).Length -ne $expectedSize) { continue }
        try {
            $hash = (Get-FileHash -LiteralPath $finished -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
            if ($hash -eq $expectedHash) { $success = $true; break }
        } catch {
            # Windows can hold the preallocated destination until its
            # download streams finish closing; continue the bounded wait.
        }
    }
    if (-not $success) { throw 'Resume did not produce exact 32 MiB fixture SHA256.' }
    Start-Sleep -Seconds 2
    $finalHash = (Get-FileHash -LiteralPath $finished -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    if ($finalHash -ne $expectedHash) { throw 'Completed output mutated after Resume.' }
    $finalStats = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
    if ([int]$finalStats.active_requests -ne 0 -or [int]$finalStats.unique_tasks_seen -ne 1 -or [int]$finalStats.total_requests -lt 2) {
        throw 'Pause/resume socket evidence is incomplete.'
    }
    @(
        "SourceSha=$SourceSha"
        "TaskId=$id"
        "PausedBytesSent=$atPause"
        "TotalRequests=$($finalStats.total_requests)"
        "Bytes=$expectedSize"
        "ExpectedSHA256=$expectedHash"
        "ActualSHA256=$finalHash"
        "Result=PASS"
    ) | Set-Content -LiteralPath (Join-Path $folder 'resume-evidence.txt') -Encoding utf8
    Write-Host 'Verified installed Windows HTTP Pause, socket closure, stable stopped bytes, Resume and SHA256.'
} finally {
    if ($app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
