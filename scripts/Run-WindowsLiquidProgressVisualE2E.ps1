<#
.SYNOPSIS
Captures HyperGet's real Liquid Progress while a local HTTP download is active.
.DESCRIPTION
Requires the already-installed GUI and CLI. It starts a loopback-only fixture,
adds and resumes one real download through the installed CLI, captures the
visible Windows UI while the CLI reports Downloading, then verifies the final
file SHA-256. This is a test-runner-only workload; it never contacts the network.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$GuiExecutable,
    [Parameter(Mandatory=$true)][string]$CliExecutable,
    [Parameter(Mandatory=$true)][string]$SourceSha,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$cliPath = [IO.Path]::GetFullPath($CliExecutable)
$root = [IO.Path]::GetFullPath($OutputDirectory)
$fixtureScript = Join-Path $PSScriptRoot 'LocalHttpRangeFixture.py'
foreach ($required in @($guiPath, $cliPath, $fixtureScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required installed executable or fixture is missing: $required"
    }
}
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw 'Liquid Progress evidence directory must be empty.'
    }
}
New-Item -ItemType Directory -Path $root -Force | Out-Null

$fixture = Join-Path $root 'liquid-progress-fixture.bin'
$portPath = Join-Path $root 'fixture-port.txt'
$serverOut = Join-Path $root 'fixture-stdout.txt'
$serverErr = Join-Path $root 'fixture-stderr.txt'
$downloadFolder = Join-Path $root 'downloads'
New-Item -ItemType Directory -Path $downloadFolder -Force | Out-Null
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $fixture 16
if ($LASTEXITCODE -ne 0) { throw 'Python failed to create the 16 MiB progress fixture.' }
$expectedBytes = [long]16 * 1MB
if ((Get-Item -LiteralPath $fixture).Length -ne $expectedBytes) {
    throw 'Liquid Progress fixture size is not exactly 16 MiB.'
}
$expectedSha = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()

function Get-SanitizedStatusPreview {
    param([object[]]$Lines)
    $preview = @()
    foreach ($entry in @($Lines)) {
        $line = "$entry".Trim()
        if (-not $line) { continue }
        if ($line -match '^\d+\t[A-Za-z]+$') {
            $preview += $line
        } elseif ($line -match '(?i)no downloads found') {
            $preview += 'No downloads found.'
        } elseif ($line -match '(?i)(usage|missing|unknown|invalid|error|exception|argument|not found)') {
            $safe = $line -replace 'https?://\S+', '[url]' -replace '[A-Za-z]:\\\S+', '[path]'
            if ($safe.Length -gt 160) { $safe = $safe.Substring(0, 160) }
            $preview += $safe
        } else {
            $preview += '[non-status output omitted]'
        }
        if ($preview.Count -ge 3) { break }
    }
    return ,$preview
}

$server = $null
$started = $null
$visible = @()
try {
    $server = Start-Process -FilePath 'python' -ArgumentList @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $fixture + '"'),
        '--port-file', ('"' + $portPath + '"'),
        '--chunk-delay-ms', '80',
        '--task-chunk-delay-ms', '180'
    ) -PassThru -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    for ($attempt = 0; $attempt -lt 80 -and -not (Test-Path -LiteralPath $portPath); $attempt++) {
        if ($server.HasExited) { throw "Loopback HTTP fixture exited: $($server.ExitCode)" }
        Start-Sleep -Milliseconds 250
    }
    if (-not (Test-Path -LiteralPath $portPath)) { throw 'Loopback HTTP fixture did not publish a port.' }
    $port = [int](Get-Content -LiteralPath $portPath -Raw)
    if ($port -lt 1 -or $port -gt 65535) { throw 'Loopback HTTP fixture returned an invalid port.' }

    # Reset counters and enable the fixture's shared per-task pacing mode.
    # Without gate_tasks=1, task-chunk-delay-ms is intentionally inactive and
    # segmented downloads can finish between expensive CLI status polls.
    $baselineStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats?reset=1&gate_tasks=1" -TimeoutSec 5
    if ([int]$baselineStats.gate_expected_tasks -ne 1) {
        throw 'Loopback fixture did not enable the paced single-task visual workload.'
    }

    $started = Start-Process -FilePath $guiPath -PassThru
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 500
        $visible = @(Get-Process -ErrorAction SilentlyContinue | Where-Object {
            $_.ProcessName -match 'DownloadManager' -and $_.MainWindowHandle -ne [IntPtr]::Zero
        })
        if ($visible.Count -gt 0) { break }
        if ($started.HasExited) { throw 'Installed HyperGet GUI exited before showing its main window.' }
    }
    if ($visible.Count -eq 0) { throw 'Installed HyperGet GUI window was not detected.' }

    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class HyperGetLiquidProgressNative {
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr handle, int command);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr handle);
}
'@
    $window = $visible | Select-Object -First 1
    [HyperGetLiquidProgressNative]::ShowWindow($window.MainWindowHandle, 3) | Out-Null
    [HyperGetLiquidProgressNative]::SetForegroundWindow($window.MainWindowHandle) | Out-Null

    $fileName = 'hyperget-liquid-progress.bin'
    $outputPath = Join-Path $downloadFolder $fileName
    $url = "http://127.0.0.1:$port/fixture.bin?task=$fileName"
    $addOutput = @(& $cliPath download add http --link $url --folder $downloadFolder --name $fileName 2>&1)
    $addExit = $LASTEXITCODE
    if ($addExit -ne 0) { throw "Installed CLI add failed ($addExit): $($addOutput -join ' ')" }
    $ids = @($addOutput | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
    if ($ids.Count -ne 1) { throw "Installed CLI did not return one task ID: $($addOutput -join ' ')" }
    $taskId = [long]$ids[0]
    # CLI status rows are tab-delimited; use a literal tab prefix rather than a regex escape.
    $statusPrefix = ([string]$taskId) + [char]9
    $lastStatusCommandExitCode = $null
    $lastStatusOutputPreview = @()

    & $cliPath download resume $taskId
    if ($LASTEXITCODE -ne 0) { throw 'Installed CLI could not resume the visual test download.' }

    $ready = $false
    $captureStats = $null
    $statusAtCapture = $null
    $lastStatusLine = ''
    $statusPollClock = [System.Diagnostics.Stopwatch]::StartNew()
    $lastStatusPollMilliseconds = -500
    $statusPollCount = 0
    # Poll cheap loopback counters at 100 ms, but spawn the installed CLI at
    # most twice per second. The fixture's shared per-task pacer keeps the
    # transfer live long enough for a reliable status observation.
    for ($attempt = 0; $attempt -lt 300; $attempt++) {
        $captureStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats" -TimeoutSec 5
        if ([int]$captureStats.active_distinct_tasks -ge 1 -and
            [long]$captureStats.bytes_sent -gt 0) {
            $elapsedMilliseconds = $statusPollClock.ElapsedMilliseconds
            if (($elapsedMilliseconds - $lastStatusPollMilliseconds) -ge 500) {
                $lastStatusPollMilliseconds = $elapsedMilliseconds
                $statusPollCount++
                $statusOutput = @(& $cliPath download show $taskId --status-lines 2>&1)
                $lastStatusCommandExitCode = $LASTEXITCODE
                $lastStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $statusOutput)
                if ($lastStatusCommandExitCode -eq 0) {
                    $statusLine = @($statusOutput | ForEach-Object { "$_".Trim() } |
                        Where-Object { $_.StartsWith($statusPrefix, [StringComparison]::Ordinal) } |
                        Select-Object -First 1)
                    if ($statusLine.Count -gt 0) {
                        $lastStatusLine = [string]$statusLine[0]
                        $statusParts = $lastStatusLine.Split([char]9, 2)
                        if ($statusParts.Count -eq 2) {
                            $statusAtCapture = $statusParts[1].Trim()
                        }
                    }
                }
                if ($statusAtCapture -eq 'Downloading') {
                    $ready = $true
                    break
                }
            }
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) {
        $diagnostics = [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'Could not prove live Downloading state before screenshot.'
            LastStatusLine = $lastStatusLine
            LastStatus = $statusAtCapture
            StatusPollCount = $statusPollCount
            LastStatusCommandExitCode = $lastStatusCommandExitCode
            LastStatusOutputPreview = $lastStatusOutputPreview
            LastFixtureStats = $captureStats
        }
        $diagnostics | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Could not prove a live data transfer with Downloading status before screenshot; sanitized diagnostics were saved.'
    }

    $bytesBeforeScreenshot = [long]$captureStats.bytes_sent
    Start-Sleep -Milliseconds 900
    $preScreenshotStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats" -TimeoutSec 5
    $bytesAdvancedBeforeScreenshot = [long]$preScreenshotStats.bytes_sent -gt $bytesBeforeScreenshot
    $statusOutput = @(& $cliPath download show $taskId --status-lines 2>&1)
    $lastStatusCommandExitCode = $LASTEXITCODE
    $lastStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $statusOutput)
    $statusPollCount++
    $statusAtCapture = $null
    if ($lastStatusCommandExitCode -eq 0) {
        $statusLine = @($statusOutput | ForEach-Object { "$_".Trim() } |
            Where-Object { $_.StartsWith($statusPrefix, [StringComparison]::Ordinal) } |
            Select-Object -First 1)
        if ($statusLine.Count -gt 0) {
            $lastStatusLine = [string]$statusLine[0]
            $statusParts = $lastStatusLine.Split([char]9, 2)
            if ($statusParts.Count -eq 2) {
                $statusAtCapture = $statusParts[1].Trim()
            }
        }
    }
    if ([int]$preScreenshotStats.active_distinct_tasks -lt 1 -or
        -not $bytesAdvancedBeforeScreenshot -or
        $statusAtCapture -ne 'Downloading') {
        $diagnostics = [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'The transfer was not proven active at screenshot time.'
            LastStatusLine = $lastStatusLine
            LastStatus = $statusAtCapture
            StatusPollCount = $statusPollCount
            LastStatusCommandExitCode = $lastStatusCommandExitCode
            LastStatusOutputPreview = $lastStatusOutputPreview
            BytesBeforeScreenshot = $bytesBeforeScreenshot
            BytesAtScreenshot = [long]$preScreenshotStats.bytes_sent
            BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot
            LastFixtureStats = $preScreenshotStats
        }
        $diagnostics | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Live transfer ended or stopped advancing before screenshot; sanitized diagnostics were saved.'
    }
    $captureStats = $preScreenshotStats
    Add-Type -AssemblyName System.Drawing
    Add-Type -AssemblyName System.Windows.Forms
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bitmap = [System.Drawing.Bitmap]::new($bounds.Width, $bounds.Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $screenshot = Join-Path $root 'liquid-progress-active.png'
    try {
        $graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $bitmap.Save($screenshot, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }

    $completed = $false
    $finalStats = $null
    $actualSha = ''
    for ($attempt = 0; $attempt -lt 240; $attempt++) {
        $finalStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats" -TimeoutSec 5
        if ([long]$finalStats.active_requests -eq 0 -and
            [long]$finalStats.bytes_sent -ge $expectedBytes -and
            (Test-Path -LiteralPath $outputPath -PathType Leaf) -and
            (Get-Item -LiteralPath $outputPath).Length -eq $expectedBytes) {
            $actualSha = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualSha -eq $expectedSha) {
                $completed = $true
                break
            }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $completed) {
        $actualLength = if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
            (Get-Item -LiteralPath $outputPath).Length
        } else { 0 }
        throw "Liquid Progress download did not complete with expected SHA-256; bytes=$actualLength."
    }

    $finalStatusOutput = @(& $cliPath download show $taskId --status-lines 2>&1)
    $finalStatusExitCode = $LASTEXITCODE
    $finalStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $finalStatusOutput)
    $finalStatusLines = @($finalStatusOutput | ForEach-Object { "$_".Trim() } |
        Where-Object { $_.StartsWith($statusPrefix, [StringComparison]::Ordinal) } |
        Where-Object { $_.Substring($statusPrefix.Length) -ceq 'Finished' })
    if ($finalStatusExitCode -ne 0 -or $finalStatusLines.Count -ne 1) {
        [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'Final CLI status was not proven as Finished after the output hash matched.'
            FinalStatusCommandExitCode = $finalStatusExitCode
            FinalStatusOutputPreview = $finalStatusOutputPreview
            ExpectedBytes = $expectedBytes
            ActualBytes = (Get-Item -LiteralPath $outputPath).Length
            ExpectedSHA256 = $expectedSha
            ActualSHA256 = $actualSha
        } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw "Final installed CLI status was not Finished after SHA-256 verification (exit=$finalStatusExitCode; output=$($finalStatusOutputPreview -join ' | '))."
    }

    [pscustomobject]@{
        SourceSHA = $SourceSha
        TaskId = $taskId
        StatusAtCapture = $statusAtCapture
        ActiveDistinctTasksAtCapture = [int]$captureStats.active_distinct_tasks
        BytesSentAtCapture = [long]$captureStats.bytes_sent
        BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot
        StatusPollCountAtCapture = $statusPollCount
        ExpectedBytes = $expectedBytes
        ActualBytes = (Get-Item -LiteralPath $outputPath).Length
        ExpectedSHA256 = $expectedSha
        ActualSHA256 = $actualSha
        Screenshot = [IO.Path]::GetFileName($screenshot)
        FinalStatus = 'Finished'
        Verified = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-report.json') -Encoding utf8
    Write-Host 'PASS: active Liquid Progress screenshot captured from installed Windows GUI; 16 MiB HTTP download SHA-256 verified.'
} finally {
    $visible | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    if ($started -and -not $started.HasExited) {
        Stop-Process -Id $started.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
