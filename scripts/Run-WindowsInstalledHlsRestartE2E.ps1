<#
.SYNOPSIS
Installed Windows HyperGet HLS process-kill/relaunch acceptance.
.DESCRIPTION
Uses a deterministic 127.0.0.1 HLS playlist and four local segments. Requires
one completed segment to be persisted before force-killing the GUI while other
segment sockets remain active, then verifies socket drain, restart/resume,
segment reuse, final assembly and exact SHA-256.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $GuiExecutable,
    [Parameter(Mandatory = $true)][string] $CliExecutable,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')

$root = [IO.Path]::GetFullPath($OutputDirectory)
$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$cliPath = [IO.Path]::GetFullPath($CliExecutable)
$fixtureScript = Join-Path $PSScriptRoot 'LocalHlsRestartFixture.py'
foreach ($required in @($guiPath, $cliPath, $fixtureScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required program or fixture missing: $required"
    }
}
if ([IO.Path]::GetFileName($guiPath) -cne 'HyperGet.exe') {
    throw 'Installed HLS restart E2E must use the branded HyperGet.exe GUI.'
}
if ([IO.Path]::GetFileName($cliPath) -cne 'DownloadManagerCli.exe') {
    throw 'Installed HLS restart E2E must use the stable legacy-compatible CLI identity.'
}
New-Item -ItemType Directory -Path $root -Force | Out-Null
$fixtureRoot = Join-Path $root 'fixture'
$downloads = Join-Path $root 'downloads'
New-Item -ItemType Directory -Path $fixtureRoot,$downloads -Force | Out-Null
$portFile = Join-Path $fixtureRoot 'port.txt'
$statsFile = Join-Path $fixtureRoot 'stats.json'
$expectedFile = Join-Path $fixtureRoot 'expected.json'
$statsUrl = $null
$server = $null
$gui = $null
$taskId = $null

function Get-HlsFixtureStats {
    return Invoke-RestMethod -Uri $script:statsUrl -TimeoutSec 5
}

function Start-HyperGetGui {
    $process = Start-Process -FilePath $script:guiPath -PassThru
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Milliseconds 500
        if (@(Get-Process -Name 'HyperGet','DownloadManager' -ErrorAction SilentlyContinue).Count -gt 0) {
            return $process
        }
        if ($process.HasExited -and $attempt -gt 4) {
            throw "Installed HyperGet GUI exited with code $($process.ExitCode)."
        }
    }
    throw 'Installed HyperGet GUI did not become available within 30 seconds.'
}

function Stop-HyperGetGui {
    param([System.Diagnostics.Process] $ProcessToStop)
    if ($ProcessToStop -and -not $ProcessToStop.HasExited) {
        Stop-Process -Id $ProcessToStop.Id -Force -ErrorAction SilentlyContinue
    }
    foreach ($remaining in @(Get-Process -Name 'HyperGet','DownloadManager' -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $remaining.Id -Force -ErrorAction SilentlyContinue
    }
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if (@(Get-Process -Name 'HyperGet','DownloadManager' -ErrorAction SilentlyContinue).Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'HyperGet GUI process remained alive after the forced-stop request.'
}

try {
    $server = Start-Process -FilePath 'python' -ArgumentList @(
        ('"' + $fixtureScript + '"'),
        '--work-dir', ('"' + $fixtureRoot + '"'),
        '--port-file', ('"' + $portFile + '"'),
        '--stats-file', ('"' + $statsFile + '"'),
        '--chunk-delay-ms', '40'
    ) -PassThru -RedirectStandardOutput (Join-Path $fixtureRoot 'server-stdout.txt') -RedirectStandardError (Join-Path $fixtureRoot 'server-stderr.txt')
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portFile -Server $server -FixtureName 'Installed HLS process-restart fixture' -Attempts 60 -ServerErrorPath (Join-Path $fixtureRoot 'server-stderr.txt')
    $script:statsUrl = "http://127.0.0.1:$port/stats"
    $playlistUrl = "http://127.0.0.1:$port/playlist.m3u8"
    $uri = [System.Uri]::new($playlistUrl, [System.UriKind]::Absolute)
    if ($uri.Scheme -ne 'http' -or -not $uri.IsLoopback -or $uri.Port -ne $port) {
        throw 'HLS fixture URL failed loopback preflight.'
    }

    $expected = Get-Content -LiteralPath $expectedFile -Raw | ConvertFrom-Json
    $expectedBytes = [long]$expected.bytes
    $expectedSha = ([string]$expected.sha256).ToLowerInvariant()
    if ($expectedBytes -le 0 -or $expectedSha -notmatch '^[0-9a-f]{64}$') {
        throw 'HLS fixture expected size/hash metadata is invalid.'
    }

    $gui = Start-HyperGetGui
    $cliOutput = @(& $cliPath download add hls --link $playlistUrl --folder $downloads --name 'hls-restarted.ts' --start 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Installed HLS CLI add failed: $($cliOutput -join ' ')"
    }
    $ids = @($cliOutput | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
    if ($ids.Count -ne 1) {
        throw "HLS CLI did not return exactly one task ID: $($cliOutput -join ' ')"
    }
    $taskId = [long]$ids[0]

    # Segment 1 is intentionally small; segments 2-4 remain slow and active.
    # Wait for the first segment to finish and be persisted while other sockets
    # are still open, then allow the periodic state saver to flush once more.
    $readyToKill = $false
    for ($attempt = 0; $attempt -lt 160; $attempt++) {
        $stats = Get-HlsFixtureStats
        if ([int]$stats.segment1_completed -ge 1 -and [int]$stats.active_segment_requests -ge 1) {
            $readyToKill = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $readyToKill) {
        throw "Did not reach the required HLS crash point: $(Get-HlsFixtureStats | ConvertTo-Json -Compress)"
    }
    Start-Sleep -Seconds 2
    $preKillStats = Get-HlsFixtureStats
    if ([int]$preKillStats.segment1_requests -ne 1 -or
        [int]$preKillStats.segment1_completed -ne 1 -or
        [int]$preKillStats.active_segment_requests -lt 1) {
        throw "Completed-segment persistence precondition changed before process kill: $($preKillStats | ConvertTo-Json -Compress)"
    }
    $bytesAtKill = [long]$preKillStats.bytes_sent
    Write-Host "Force-killing installed HyperGet with task $taskId after segment 1 completed; active segments=$($preKillStats.active_segment_requests)."
    Stop-HyperGetGui -ProcessToStop $gui
    $gui = $null

    $drained = $false
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        $stats = Get-HlsFixtureStats
        if ([int]$stats.active_segment_requests -eq 0) {
            $drained = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $drained) {
        throw "HLS segment sockets did not drain after GUI termination: $(Get-HlsFixtureStats | ConvertTo-Json -Compress)"
    }
    $stoppedBytes = [long]$stats.bytes_sent
    Start-Sleep -Seconds 1
    $stableStats = Get-HlsFixtureStats
    if ([int]$stableStats.active_segment_requests -ne 0 -or [long]$stableStats.bytes_sent -ne $stoppedBytes) {
        throw 'HLS fixture observed late segment bytes after the killed GUI had drained.'
    }

    $gui = Start-HyperGetGui
    Start-Sleep -Seconds 3
    $statusLines = @(& $cliPath download show --status-lines $taskId 2>&1)
    if ($LASTEXITCODE -ne 0 -or $statusLines.Count -ne 1 -or "$($statusLines[0])" -notmatch "^\s*$taskId\s+") {
        throw "Persisted HLS task was not visible after GUI relaunch: $($statusLines -join ' ')"
    }
    if ("$($statusLines[0])" -notmatch '\s+(Downloading|Resuming|Retrying|PreparingFile)$') {
        $resumeOutput = @(& $cliPath download resume $taskId 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "HLS resume after GUI relaunch failed: $($resumeOutput -join ' ')"
        }
    }

    $finalPath = Join-Path $downloads 'hls-restarted.ts'
    $completed = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(240)
    while ([DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
        if (-not (Test-Path -LiteralPath $finalPath -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $finalPath).Length -ne $expectedBytes) { continue }
        try {
            $actualSha = (Get-FileHash -LiteralPath $finalPath -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
            if ($actualSha -eq $expectedSha) {
                $completed = $true
                break
            }
        } catch {
            # The destination can remain locked briefly while the final segment
            # and assembly streams close; keep the wait bounded and retry.
        }
    }
    if (-not $completed) {
        throw "HLS output did not assemble to the exact expected $expectedBytes bytes / SHA-256 $expectedSha."
    }
    Start-Sleep -Seconds 1
    $finalHash = (Get-FileHash -LiteralPath $finalPath -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    if ($finalHash -ne $expectedSha) { throw 'HLS assembled output mutated after completion.' }

    $finalStats = Get-HlsFixtureStats
    if ([int]$finalStats.playlist_requests -lt 2) {
        throw 'HLS playlist was not re-fetched after GUI process relaunch.'
    }
    if ([int]$finalStats.segment1_requests -ne 1 -or [int]$finalStats.segment1_completed -ne 1) {
        throw "Persisted completed HLS segment 1 was re-requested or lost: $($finalStats | ConvertTo-Json -Compress)"
    }
    foreach ($segmentId in 2..4) {
        $completedField = "segment${segmentId}_completed"
        if ([int]$finalStats.$completedField -lt 1) {
            throw "HLS segment $segmentId did not complete after restart."
        }
    }
    if ([int]$finalStats.active_segment_requests -ne 0) {
        throw 'HLS fixture still has active segment requests after final assembly.'
    }

    @(
        "SourceSha=$SourceSha"
        "TaskId=$taskId"
        "PlaylistRequests=$($finalStats.playlist_requests)"
        "Segment1Requests=$($finalStats.segment1_requests)"
        "Segment1Completed=$($finalStats.segment1_completed)"
        "ActiveRequestsAtKill=$($preKillStats.active_segment_requests)"
        "BytesSentAtKill=$bytesAtKill"
        "BytesStableAfterKill=$stoppedBytes"
        "ExpectedBytes=$expectedBytes"
        "ActualBytes=$((Get-Item -LiteralPath $finalPath).Length)"
        "ExpectedSHA256=$expectedSha"
        "ActualSHA256=$finalHash"
        "Segment2Completed=$($finalStats.segment2_completed)"
        "Segment3Completed=$($finalStats.segment3_completed)"
        "Segment4Completed=$($finalStats.segment4_completed)"
        "TotalSegmentRequests=$($finalStats.total_segment_requests)"
        "Result=PASS"
    ) | Set-Content -LiteralPath (Join-Path $root 'hls-process-restart-evidence.txt') -Encoding utf8
    $finalStats | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'final-fixture-stats.json') -Encoding utf8
    Write-Host 'PASS: installed HLS task survived forced GUI process termination; sockets drained, persisted segment reused, and final SHA-256 matched.'
} finally {
    if ($gui) {
        Stop-HyperGetGui -ProcessToStop $gui
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
