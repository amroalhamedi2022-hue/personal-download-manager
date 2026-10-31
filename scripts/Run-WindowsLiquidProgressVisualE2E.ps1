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
. (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')

$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$guiProcessName = [IO.Path]::GetFileNameWithoutExtension($guiPath)
if ($guiProcessName -ne 'HyperGet') {
    throw "Liquid Progress visual acceptance requires HyperGet.exe; got '$([IO.Path]::GetFileName($guiPath))'."
}
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
$addStdout = Join-Path $env:RUNNER_TEMP ("hyperget-liquid-progress-add-$SourceSha.stdout.txt")
$addStderr = Join-Path $env:RUNNER_TEMP ("hyperget-liquid-progress-add-$SourceSha.stderr.txt")
$resumeStdout = Join-Path $env:RUNNER_TEMP ("hyperget-liquid-progress-resume-$SourceSha.stdout.txt")
$resumeStderr = Join-Path $env:RUNNER_TEMP ("hyperget-liquid-progress-resume-$SourceSha.stderr.txt")
foreach ($redirectPath in @($addStdout, $addStderr, $resumeStdout, $resumeStderr)) {
    if ([string]::IsNullOrWhiteSpace($redirectPath)) {
        throw 'RUNNER_TEMP did not produce valid CLI redirection paths.'
    }
}
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $fixture 16
if ($LASTEXITCODE -ne 0) { throw 'Python failed to create the 16 MiB progress fixture.' }
$expectedBytes = [long]16 * 1MB
if ((Get-Item -LiteralPath $fixture).Length -ne $expectedBytes) {
    throw 'Liquid Progress fixture size is not exactly 16 MiB.'
}
$expectedSha = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
# Capture far enough into the fill to make the active shimmer and bubbles visibly testable.
$minimumVisualProgressBytes = [long][Math]::Ceiling($expectedBytes * 0.25)

function Get-NormalizedCliLines {
    param([object[]]$Lines)
    foreach ($entry in @($Lines)) {
        $line = [string]$entry
        # Normalize terminal decoration and invisible prefixes before parsing.
        $line = [regex]::Replace($line, "\x1B\[[0-?]*[ -/]*[@-~]", "")
        $line = [regex]::Replace($line, "\x1B\][^\x07]*(?:\x07|\x1B\\\\)", "")
        $invisible = [char[]]@([char]0xFEFF, [char]0x200B, [char]0x200C, [char]0x200D, [char]0x200E, [char]0x200F, [char]0x2060)
        foreach ($character in $invisible) {
            $line = $line.Replace([string]$character, '')
        }
        $line = $line.Trim()
        if ($line.Length -gt 0) { $line }
    }
}

function Get-DownloadStatusFromOutput {
    param([object[]]$Lines, [long]$ExpectedId)
    $validStatuses = @('Paused', 'Error', 'Downloading', 'Finished', 'PreparingFile', 'Resuming', 'Retrying')
    foreach ($line in @(Get-NormalizedCliLines -Lines $Lines)) {
        $columns = [regex]::Split($line.Trim(), '\s+')
        if ($columns.Count -lt 2 -or $columns[0].Trim() -cne [string]$ExpectedId) { continue }
        $candidate = $columns[1].Trim()
        if ($validStatuses -ccontains $candidate) { return $candidate }
    }
    return $null
}

function Get-ReportedTaskIds {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return @() }
    return @(
        Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue |
            ForEach-Object { ([string]$_).Trim() } |
            Where-Object { $_ -match '^\d+$' } |
            ForEach-Object { [long]$_ }
    )
}

function Get-SanitizedStatusPreview {
    param([object[]]$Lines)
    $preview = @()
    foreach ($line in @(Get-NormalizedCliLines -Lines $Lines)) {
        # Never persist raw tables, names, folders, URLs, or arbitrary CLI output.
        if ($line -match '^\d+\s+(Paused|Error|Downloading|Finished|PreparingFile|Resuming|Retrying)(\s+[A-Za-z0-9_.-]+)?(\s+.*)?$') {
            $preview += "Status row: $($Matches[1])"
        } elseif ($line -match '(?i)^(No such download|No downloads found\.?|No download found\.?)$') {
            $preview += "Task lookup message: $line"
        } elseif ($line -match '(?i)\b(ID\s+Status|ID\s+Status\s+Name|Name\s+Folder)\b') {
            $preview += 'Formatted table output detected; status-lines option may not have been parsed.'
        } elseif ($line -match '(?i)\b(usage|unknown option|invalid|missing|error|exception|argument|not found)\b') {
            $preview += 'CLI usage/error output detected (content redacted).'
        } elseif ($line -match '^[A-Za-z][A-Za-z ]{0,39}$') {
            # Safe in the isolated runner: plain ASCII words only, no paths, URLs or filenames.
            $preview += "Plain-text CLI message: $line"
        } else {
            $codes = @($line.ToCharArray() | Select-Object -First 32 | ForEach-Object { 'U+{0:X4}' -f [int][char]$_ }) -join ' '
            $preview += "Unclassified CLI output (characters=$($line.Length); codepoints=$codes)."
        }
        if ($preview.Count -ge 3) { break }
    }
    return $preview
}

$server = $null
$started = $null
$addProcess = $null
$resumeProcess = $null
$visible = @()
try {
    $server = Start-Process -FilePath 'python' -ArgumentList @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $fixture + '"'),
        '--port-file', ('"' + $portPath + '"'),
        '--chunk-delay-ms', '80',
        '--task-chunk-delay-ms', '180'
    ) -PassThru -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portPath -Server $server -FixtureName 'Liquid Progress HTTP fixture' -Attempts 80 -ServerErrorPath $serverErr

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
            $_.ProcessName -ieq $guiProcessName -and $_.MainWindowHandle -ne [IntPtr]::Zero
        })
        if ($visible.Count -gt 0) { break }
        if ($started.HasExited) { throw 'Installed HyperGet GUI exited before showing its main window.' }
    }
    if ($visible.Count -eq 0) { throw 'Installed HyperGet GUI window was not detected.' }

    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class HyperGetLiquidProgressNative {
    public delegate bool EnumWindowsProc(IntPtr handle, IntPtr parameter);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr handle, int command);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr handle);
    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr handle);
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr handle, out RECT rect);
    // GetWindowRect includes the invisible resize border of a maximized
    // window. DWM exposes the actual visible frame for screenshot cropping.
    [DllImport("dwmapi.dll")]
    public static extern int DwmGetWindowAttribute(IntPtr handle, int attribute, out RECT rect, int rectSize);
    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(IntPtr handle, IntPtr insertAfter, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr parameter);
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr handle, out uint processId);
    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr handle);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowTextLength(IntPtr handle);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr handle, StringBuilder text, int count);

    public static IntPtr FindVisibleWindow(uint processId, string titleFragment) {
        IntPtr match = IntPtr.Zero;
        EnumWindows(delegate(IntPtr handle, IntPtr parameter) {
            uint owner;
            GetWindowThreadProcessId(handle, out owner);
            if (owner != processId || !IsWindowVisible(handle)) return true;
            int length = GetWindowTextLength(handle);
            if (length <= 0) return true;
            StringBuilder title = new StringBuilder(length + 1);
            GetWindowText(handle, title, title.Capacity);
            if (title.ToString().IndexOf(titleFragment, StringComparison.OrdinalIgnoreCase) < 0) return true;
            match = handle;
            return false;
        }, IntPtr.Zero);
        return match;
    }

    public static IntPtr FindVisibleWindowExcluding(uint processId, IntPtr excludedHandle) {
        IntPtr match = IntPtr.Zero;
        EnumWindows(delegate(IntPtr handle, IntPtr parameter) {
            uint owner;
            GetWindowThreadProcessId(handle, out owner);
            if (owner != processId || handle == excludedHandle || !IsWindowVisible(handle)) return true;
            int length = GetWindowTextLength(handle);
            if (length <= 0) return true;
            match = handle;
            return false;
        }, IntPtr.Zero);
        return match;
    }
}
'@
    $window = $visible | Select-Object -First 1
    $windowHandle = $window.MainWindowHandle
    [HyperGetLiquidProgressNative]::ShowWindow($windowHandle, 3) | Out-Null
    [HyperGetLiquidProgressNative]::SetWindowPos($windowHandle, [IntPtr](-1), 0, 0, 0, 0, [uint32](0x0001 -bor 0x0002 -bor 0x0040)) | Out-Null
    [HyperGetLiquidProgressNative]::BringWindowToTop($windowHandle) | Out-Null
    [HyperGetLiquidProgressNative]::SetForegroundWindow($windowHandle) | Out-Null
    Start-Sleep -Milliseconds 250
    if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $windowHandle) {
        throw 'Foreground window is not the installed HyperGet window; refusing an obscured screenshot.'
    }

    $fileName = 'hyperget-liquid-progress.bin'
    $outputPath = Join-Path $downloadFolder $fileName
    $url = "http://127.0.0.1:$port/fixture.bin?task=$fileName"
    $addArguments = 'download add http --link "' + $url + '" --folder "' + $downloadFolder + '" --name "' + $fileName + '"'
    foreach ($tempFile in @($addStdout, $addStderr, $resumeStdout, $resumeStderr)) {
        Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
    }
    # Keep add asynchronous: on the installed CLI it may remain attached until
    # the transfer finishes, which otherwise prevents observing Downloading.
    $addProcess = Start-Process -FilePath $cliPath -ArgumentList $addArguments -PassThru -NoNewWindow -RedirectStandardOutput $addStdout -RedirectStandardError $addStderr
    # The isolated runner starts with no downloads, so its first ID is 0.
    # The add process's final stdout is checked after the actual transfer.
    $taskId = [long]0
    $taskIdResolved = $false
    $addResumeSent = $false
    $resumeProcess = $null
    $lastStatusCommandExitCode = $null
    $lastStatusOutputPreview = @()
    $statusOutput = @()
    $finalStatusOutput = @()
    $observedStatuses = @()
    $ready = $false
    $captureStats = $null
    $statusAtCapture = $null
    $lastStatusLine = ''
    $statusPollClock = [System.Diagnostics.Stopwatch]::StartNew()
    $lastStatusPollMilliseconds = -500
    $statusPollCount = 0
    # Resolve the add ID first, then poll status independently of fixture traffic.
    # An Added-only task is Paused until the CLI Resume command is sent; waiting
    # for active fixture bytes before polling status would deadlock that transition.
    for ($attempt = 0; $attempt -lt 600; $attempt++) {
        $captureStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats" -TimeoutSec 5
        if ($addProcess.HasExited -and -not $taskIdResolved) {
            $reportedIds = @(Get-ReportedTaskIds -Path $addStdout)
            if ($reportedIds.Count -eq 1) {
                $taskId = [long]$reportedIds[0]
                $taskIdResolved = $true
            }
        }
        $elapsedMilliseconds = $statusPollClock.ElapsedMilliseconds
        if ($taskIdResolved -and (($elapsedMilliseconds - $lastStatusPollMilliseconds) -ge 500)) {
            $lastStatusPollMilliseconds = $elapsedMilliseconds
            $statusPollCount++
            $statusAtCapture = $null
            $statusOutput = @(& $cliPath download show --status-lines $taskId 2>&1)
            $lastStatusCommandExitCode = $LASTEXITCODE
            $lastStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $statusOutput)
            if ($lastStatusCommandExitCode -eq 0) {
                $statusAtCapture = Get-DownloadStatusFromOutput -Lines $statusOutput -ExpectedId $taskId
                if ($null -ne $statusAtCapture) {
                    $lastStatusLine = ([string]$taskId) + [char]9 + $statusAtCapture
                    $observedStatuses += $statusAtCapture
                    if ($observedStatuses.Count -gt 8) {
                        $observedStatuses = @($observedStatuses | Select-Object -Last 8)
                    }
                    if ($statusAtCapture -eq 'Paused' -and -not $addResumeSent) {
                        $resumeArguments = "download resume $taskId"
                        $resumeProcess = Start-Process -FilePath $cliPath -ArgumentList $resumeArguments -PassThru -NoNewWindow -RedirectStandardOutput $resumeStdout -RedirectStandardError $resumeStderr
                        $addResumeSent = $true
                    }
                }
            }
            if ($statusAtCapture -eq 'Downloading' -and [int]$captureStats.active_distinct_tasks -ge 1 -and [long]$captureStats.bytes_sent -ge $minimumVisualProgressBytes) {
                $ready = $true
                break
            }
            if ($statusAtCapture -eq 'Finished') { break }
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) {
        $addProcessExitCode = $null
        if ($addProcess -and $addProcess.HasExited) { $addProcessExitCode = $addProcess.ExitCode }
        $addOutputIds = @(Get-ReportedTaskIds -Path $addStdout)
        $diagnostics = [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            TaskIdResolved = $taskIdResolved
            ResumeSent = $addResumeSent
            AddProcessExited = [bool]($addProcess -and $addProcess.HasExited)
            AddProcessExitCode = $addProcessExitCode
            AddOutputIdCount = $addOutputIds.Count
            ObservedStatuses = @($observedStatuses | Select-Object -Last 8)
            Reason = 'Could not prove live Downloading state at >=25% progress before screenshot.'
            LastStatusLine = $lastStatusLine
            LastStatus = $statusAtCapture
            StatusPollCount = $statusPollCount
            LastStatusCommandExitCode = $lastStatusCommandExitCode
            LastStatusOutputPreview = $lastStatusOutputPreview
            LastStatusOutputLineCount = @($statusOutput).Count
            MinimumVisualProgressBytes = $minimumVisualProgressBytes
            BytesSentAtLastPoll = [long]$captureStats.bytes_sent
            LastFixtureStats = $captureStats
        }
        $diagnostics | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Could not prove a live transfer at >=25% progress with Downloading status before screenshot; sanitized diagnostics were saved.'
    }

    $bytesBeforeScreenshot = [long]$captureStats.bytes_sent
    Start-Sleep -Milliseconds 900
    $preScreenshotStats = Invoke-RestMethod -Uri "http://127.0.0.1:$port/stats" -TimeoutSec 5
    $bytesAdvancedBeforeScreenshot = [long]$preScreenshotStats.bytes_sent -gt $bytesBeforeScreenshot
    $statusOutput = @(& $cliPath download show --status-lines $taskId 2>&1)
    $lastStatusCommandExitCode = $LASTEXITCODE
    $lastStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $statusOutput)
    $statusPollCount++
    $statusAtCapture = $null
    if ($lastStatusCommandExitCode -eq 0) {
        $statusAtCapture = Get-DownloadStatusFromOutput -Lines $statusOutput -ExpectedId $taskId
        if ($null -ne $statusAtCapture) {
            $lastStatusLine = ([string]$taskId) + [char]9 + $statusAtCapture
        }
    }
    if ([int]$preScreenshotStats.active_distinct_tasks -lt 1 -or
        -not $bytesAdvancedBeforeScreenshot -or
        [long]$preScreenshotStats.bytes_sent -lt $minimumVisualProgressBytes -or
        $statusAtCapture -ne 'Downloading') {
        $addProcessExitCode = $null
        if ($addProcess -and $addProcess.HasExited) { $addProcessExitCode = $addProcess.ExitCode }
        $addOutputIds = @(Get-ReportedTaskIds -Path $addStdout)
        $diagnostics = [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            TaskIdResolved = $taskIdResolved
            ResumeSent = $addResumeSent
            AddProcessExited = [bool]($addProcess -and $addProcess.HasExited)
            AddProcessExitCode = $addProcessExitCode
            AddOutputIdCount = $addOutputIds.Count
            ObservedStatuses = @($observedStatuses | Select-Object -Last 8)
            Reason = 'The transfer was not proven active with >=25% visible fill at screenshot time.'
            LastStatusLine = $lastStatusLine
            LastStatus = $statusAtCapture
            StatusPollCount = $statusPollCount
            LastStatusCommandExitCode = $lastStatusCommandExitCode
            LastStatusOutputPreview = $lastStatusOutputPreview
            LastStatusOutputLineCount = @($statusOutput).Count
            BytesBeforeScreenshot = $bytesBeforeScreenshot
            BytesAtScreenshot = [long]$preScreenshotStats.bytes_sent
            BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot
            LastFixtureStats = $preScreenshotStats
        }
        $diagnostics | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Live transfer ended or stopped advancing before screenshot; sanitized diagnostics were saved.'
    }
    $captureStats = $preScreenshotStats
    $openOutput = @(& $cliPath download open $taskId 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw 'Installed CLI could not open the active HyperGet download-details window.'
    }
    # Compose owns multiple top-level windows in one process. Process.MainWindowHandle
    # points at the download list, not necessarily the active progress dialog. Select
    # the visible task window by its non-secret fixture filename before acceptance.
    $detailWindowHandle = [IntPtr]::Zero
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $detailWindowHandle = [HyperGetLiquidProgressNative]::FindVisibleWindow(
            [uint32]$window.Id,
            $fileName
        )
        # Compose may use a localized/status-only native title. In the isolated
        # process the explicit IPC open must create a second visible top-level
        # window, so accept that window only after excluding the proven main one.
        if ($detailWindowHandle -eq [IntPtr]::Zero) {
            $detailWindowHandle = [HyperGetLiquidProgressNative]::FindVisibleWindowExcluding(
                [uint32]$window.Id,
                $windowHandle
            )
        }
        if ($detailWindowHandle -ne [IntPtr]::Zero) { break }
        Start-Sleep -Milliseconds 250
    }
    if ($detailWindowHandle -eq [IntPtr]::Zero) {
        throw 'The active HyperGet download-details window was not visible; refusing to accept a main-list screenshot as Liquid Progress proof.'
    }
    $windowHandle = $detailWindowHandle
    # Re-assert foreground immediately before capture; crop to the real details window.
    [HyperGetLiquidProgressNative]::ShowWindow($windowHandle, 3) | Out-Null
    [HyperGetLiquidProgressNative]::SetWindowPos($windowHandle, [IntPtr](-1), 0, 0, 0, 0, [uint32](0x0001 -bor 0x0002 -bor 0x0040)) | Out-Null
    [HyperGetLiquidProgressNative]::BringWindowToTop($windowHandle) | Out-Null
    [HyperGetLiquidProgressNative]::SetForegroundWindow($windowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $windowHandle) {
        throw 'Foreground window changed before active Liquid Progress capture.'
    }
    $windowRect = [HyperGetLiquidProgressNative+RECT]::new()
    if (-not [HyperGetLiquidProgressNative]::GetWindowRect($windowHandle, [ref]$windowRect)) {
        throw 'Could not obtain installed HyperGet window bounds.'
    }
    # DWM's extended frame bounds omit invisible maximized-window borders.
    # Keep the GetWindowRect fallback if the DWM attribute is unavailable.
    $dwmVisibleWindowRect = [HyperGetLiquidProgressNative+RECT]::new()
    $dwmResult = [HyperGetLiquidProgressNative]::DwmGetWindowAttribute(
        $windowHandle, 9, [ref]$dwmVisibleWindowRect, 16
    )
    if ($dwmResult -eq 0 -and
        $dwmVisibleWindowRect.Right -gt $dwmVisibleWindowRect.Left -and
        $dwmVisibleWindowRect.Bottom -gt $dwmVisibleWindowRect.Top) {
        $windowRect = $dwmVisibleWindowRect
    }
    $windowWidth = $windowRect.Right - $windowRect.Left
    $windowHeight = $windowRect.Bottom - $windowRect.Top
    # Maximized Windows windows include an invisible 8px resize frame
    # outside the display bounds. That frame is NOT clipped app content.
    # Reject larger overflow, and crop only the non-visible OS frame.
    Add-Type -AssemblyName System.Windows.Forms
    $displayBounds = [System.Windows.Forms.Screen]::FromHandle($windowHandle).Bounds
    $maxInvisibleFrame = 12
    $outsideLeft = [Math]::Max(0, $displayBounds.Left - $windowRect.Left)
    $outsideTop = [Math]::Max(0, $displayBounds.Top - $windowRect.Top)
    $outsideRight = [Math]::Max(0, $windowRect.Right - $displayBounds.Right)
    $outsideBottom = [Math]::Max(0, $windowRect.Bottom - $displayBounds.Bottom)
    if (($windowRect.Right -gt $displayBounds.Right -and $outsideRight -gt $maxInvisibleFrame) -or
        ($windowRect.Bottom -gt $displayBounds.Bottom -and $outsideBottom -gt $maxInvisibleFrame) -or
        $outsideLeft -gt $maxInvisibleFrame -or $outsideTop -gt $maxInvisibleFrame) {
        throw ("Installed download window is outside display bounds: window=({0},{1})-({2},{3}), display=({4},{5})-({6},{7})." -f
            $windowRect.Left, $windowRect.Top, $windowRect.Right, $windowRect.Bottom,
            $displayBounds.Left, $displayBounds.Top, $displayBounds.Right, $displayBounds.Bottom)
    }
    $captureLeft = [Math]::Max($windowRect.Left, $displayBounds.Left)
    $captureTop = [Math]::Max($windowRect.Top, $displayBounds.Top)
    $captureRight = [Math]::Min($windowRect.Right, $displayBounds.Right)
    $captureBottom = [Math]::Min($windowRect.Bottom, $displayBounds.Bottom)
    $captureWidth = $captureRight - $captureLeft
    $captureHeight = $captureBottom - $captureTop
    if ($captureWidth -lt 200 -or $captureHeight -lt 150) {
        throw 'Visible HyperGet window bounds are too small for visual acceptance.'
    }
    Add-Type -AssemblyName System.Drawing
    $bitmap = [System.Drawing.Bitmap]::new($captureWidth, $captureHeight)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $screenshot = Join-Path $root 'liquid-progress-active.png'
    try {
        $graphics.CopyFromScreen(
            [System.Drawing.Point]::new($captureLeft, $captureTop),
            [System.Drawing.Point]::Empty,
            [System.Drawing.Size]::new($captureWidth, $captureHeight)
        )
        $bitmap.Save($screenshot, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }

    # Exercise the real graph visibility control through native Windows UI
    # Automation when Compose exposes the button accessibility semantics.
    # Do not infer a successful click from source contracts or screenshots alone.
    # On runners without an accessible InvokePattern, preserve the transfer
    # acceptance result and report this separate interaction as untested.
    $graphToggleTested = $false
    $graphToggleResult = 'not exposed to Windows UI Automation'
    try {
        Add-Type -AssemblyName UIAutomationClient -ErrorAction Stop
        Add-Type -AssemblyName UIAutomationTypes -ErrorAction Stop
        $automationRoot = [System.Windows.Automation.AutomationElement]::FromHandle($windowHandle)
        $hideCondition = [System.Windows.Automation.PropertyCondition]::new(
            [System.Windows.Automation.AutomationElement]::NameProperty, 'Hide graph'
        )
        $showCondition = [System.Windows.Automation.PropertyCondition]::new(
            [System.Windows.Automation.AutomationElement]::NameProperty, 'Show graph'
        )
        $hideButton = $automationRoot.FindFirst(
            [System.Windows.Automation.TreeScope]::Descendants, $hideCondition
        )
        if ($null -ne $hideButton) {
            $hideInvoke = $hideButton.GetCurrentPattern(
                [System.Windows.Automation.InvokePattern]::Pattern
            )
            $hideInvoke.Invoke()
            Start-Sleep -Milliseconds 350
            $showButton = $automationRoot.FindFirst(
                [System.Windows.Automation.TreeScope]::Descendants, $showCondition
            )
            if ($null -ne $showButton) {
                $showInvoke = $showButton.GetCurrentPattern(
                    [System.Windows.Automation.InvokePattern]::Pattern
                )
                $showInvoke.Invoke()
                Start-Sleep -Milliseconds 350
                $restoredHideButton = $automationRoot.FindFirst(
                    [System.Windows.Automation.TreeScope]::Descendants, $hideCondition
                )
                if ($null -ne $restoredHideButton) {
                    $graphToggleTested = $true
                    $graphToggleResult = 'hide and show controls invoked; original state restored'
                } else {
                    $graphToggleResult = 'show invoked, but restored hide control was not exposed'
                }
            } else {
                $graphToggleResult = 'hide invoked, but show control was not exposed'
            }
        }
    } catch {
        # Avoid storing arbitrary exception messages or private UI text in artifacts.
        $graphToggleResult = 'Windows UI Automation could not invoke both graph controls'
    }
    # Compose Desktop may not expose its button semantics through Windows UIA.
    # In that case use the actual on-screen Hide/Show buttons, then prove the
    # chart area disappeared and returned from real Windows pixel captures.
    $graphHiddenScreenshot = $null
    $graphRestoredScreenshot = $null
    if (-not $graphToggleTested -and $captureWidth -ge 800 -and $captureHeight -ge 600) {
        function Save-GraphInteractionCapture {
            param([string]$Destination)
            $shot = [System.Drawing.Bitmap]::new($captureWidth, $captureHeight)
            $g = [System.Drawing.Graphics]::FromImage($shot)
            try {
                $g.CopyFromScreen(
                    [System.Drawing.Point]::new($captureLeft, $captureTop),
                    [System.Drawing.Point]::Empty,
                    [System.Drawing.Size]::new($captureWidth, $captureHeight)
                )
                $shot.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $g.Dispose()
                $shot.Dispose()
            }
        }
        function Get-GraphBlueSampleCount {
            param([string]$Path)
            $shot = [System.Drawing.Bitmap]::FromFile($Path)
            try {
                $count = 0
                # Stable center of the transfer chart, excluding the blue
                # action button and all file-details text.
                $left = [int]($shot.Width * 0.69)
                $right = [int]($shot.Width * 0.95)
                $top = [int]($shot.Height * 0.19)
                $bottom = [int]($shot.Height * 0.34)
                for ($y = $top; $y -lt $bottom; $y += 3) {
                    for ($x = $left; $x -lt $right; $x += 3) {
                        $pixel = $shot.GetPixel($x, $y)
                        if (($pixel.B - $pixel.R) -gt 12 -and
                            ($pixel.B - $pixel.G) -gt 5 -and $pixel.G -gt 180) {
                            $count++
                        }
                    }
                }
                return $count
            } finally {
                $shot.Dispose()
            }
        }
        $originalBlueCount = Get-GraphBlueSampleCount -Path $screenshot
        if ($originalBlueCount -gt 1000 -and
            [HyperGetLiquidProgressNative]::GetForegroundWindow() -eq $windowHandle) {
            $clickX = $captureRight - [int][Math]::Round($captureWidth * 0.06)
            $clickY = $captureTop + [int][Math]::Round($captureHeight * 0.12)
            $graphHiddenScreenshot = Join-Path $root 'liquid-progress-graph-hidden.png'
            $graphRestoredScreenshot = Join-Path $root 'liquid-progress-graph-restored.png'
            foreach ($target in @($graphHiddenScreenshot, $graphRestoredScreenshot)) {
                if (-not [HyperGetLiquidProgressNative]::SetCursorPos($clickX, $clickY)) {
                    throw 'Could not position the mouse for the real graph toggle test.'
                }
                [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
                [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
                Start-Sleep -Milliseconds 350
                Save-GraphInteractionCapture -Destination $target
            }
            $hiddenBlueCount = Get-GraphBlueSampleCount -Path $graphHiddenScreenshot
            $restoredBlueCount = Get-GraphBlueSampleCount -Path $graphRestoredScreenshot
            if ($hiddenBlueCount -lt $originalBlueCount * 0.25 -and
                $restoredBlueCount -gt $originalBlueCount * 0.5) {
                $graphToggleTested = $true
                $graphToggleResult = 'real mouse clicks hid and restored the chart; Windows pixel evidence verified'
            } else {
                throw ("Graph hide/show failed real Windows pixel verification: original={0}, hidden={1}, restored={2}." -f
                    $originalBlueCount, $hiddenBlueCount, $restoredBlueCount)
            }
        } else {
            $graphToggleResult = 'Windows graph pixel probe unavailable (small window or no foreground chart)'
        }
    }
    Write-Host "Graph toggle interaction: $graphToggleResult"

    # Capture the two other REAL IDM-style tabs for later visual comparison.
    # This deliberately records evidence rather than claiming pixel-perfect
    # parity with historical IDM screenshots that are not in this repository.
    # Positions are restricted to the known 1024x728 Windows CI fixture;
    # never click at guessed coordinates on other display layouts/DPI.
    $tabCaptureResult = 'not captured (different window size or foreground)'
    $tabCaptureFiles = @()
    if ($captureWidth -ge 980 -and $captureWidth -le 1080 -and
        $captureHeight -ge 680 -and $captureHeight -le 770 -and
        [HyperGetLiquidProgressNative]::GetForegroundWindow() -eq $windowHandle) {
        function Save-TabCapture {
            param([string]$Destination)
            $shot = [System.Drawing.Bitmap]::new($captureWidth, $captureHeight)
            $g = [System.Drawing.Graphics]::FromImage($shot)
            try {
                $g.CopyFromScreen(
                    [System.Drawing.Point]::new($captureLeft, $captureTop),
                    [System.Drawing.Point]::Empty,
                    [System.Drawing.Size]::new($captureWidth, $captureHeight)
                )
                $shot.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $g.Dispose()
                $shot.Dispose()
            }
        }
        try {
            $tabClickY = $captureTop + 47
            $tabClicks = @(
                @{ Name = 'speed'; OffsetX = 81 },
                @{ Name = 'on-completion'; OffsetX = 174 },
                @{ Name = 'info-restored'; OffsetX = 28 }
            )
            foreach ($tab in $tabClicks) {
                if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $windowHandle) {
                    throw 'Details window lost foreground during tab screenshot capture.'
                }
                $tabClickX = $captureLeft + [int]$tab.OffsetX
                if (-not [HyperGetLiquidProgressNative]::SetCursorPos($tabClickX, $tabClickY)) {
                    throw 'Could not position mouse to capture a download details tab.'
                }
                [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
                [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
                Start-Sleep -Milliseconds 450
                $tabImage = Join-Path $root ("liquid-progress-tab-{0}.png" -f $tab.Name)
                Save-TabCapture -Destination $tabImage
                $tabCaptureFiles += [IO.Path]::GetFileName($tabImage)
            }
            $tabCaptureResult = 'real Windows click screenshots recorded for human IDM tab review'
        } catch {
            # Screenshots are evidence for review, not an acceptance test that
            # could mask or invalidate the already verified data transfer.
            $tabCaptureResult = 'tab screenshot probe failed (not visually accepted)'
        }
    }
    Write-Host "IDM tab screenshot capture: $tabCaptureResult"

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

    if (-not $addProcess.WaitForExit(180000)) {
        [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'The asynchronous add command did not exit after the transfer completed.'
            AddProcessExited = $false
            ExpectedBytes = $expectedBytes
            ActualBytes = (Get-Item -LiteralPath $outputPath).Length
            ExpectedSHA256 = $expectedSha
            ActualSHA256 = $actualSha
        } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Installed CLI add command did not exit after the verified transfer.'
    }
    $addProcessExitCode = $addProcess.ExitCode
    $reportedIds = @(Get-ReportedTaskIds -Path $addStdout)
    if ($addProcessExitCode -ne 0 -or $reportedIds.Count -ne 1 -or [long]$reportedIds[0] -ne $taskId) {
        [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'The asynchronous add command did not return the expected task ID successfully.'
            AddProcessExited = $true
            AddProcessExitCode = $addProcessExitCode
            AddOutputIdCount = $reportedIds.Count
            ExpectedBytes = $expectedBytes
            ActualBytes = (Get-Item -LiteralPath $outputPath).Length
            ExpectedSHA256 = $expectedSha
            ActualSHA256 = $actualSha
        } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-diagnostics.json') -Encoding utf8
        throw 'Installed CLI add process did not report the expected task ID with exit code zero.'
    }
    if ($resumeProcess) {
        if (-not $resumeProcess.WaitForExit(180000)) {
            throw 'Installed CLI resume command did not exit after the transfer completed.'
        }
        if ($resumeProcess.ExitCode -ne 0) {
            throw "Installed CLI resume command failed with exit code $($resumeProcess.ExitCode)."
        }
    }

    $finalStatusOutput = @(& $cliPath download show --status-lines $taskId 2>&1)
    $finalStatusExitCode = $LASTEXITCODE
    $finalStatusOutputPreview = @(Get-SanitizedStatusPreview -Lines $finalStatusOutput)
    $finalStatus = $null
    if ($finalStatusExitCode -eq 0) {
        $finalStatus = Get-DownloadStatusFromOutput -Lines $finalStatusOutput -ExpectedId $taskId
    }
    if ($finalStatusExitCode -ne 0 -or $finalStatus -cne 'Finished') {
        [pscustomobject]@{
            SourceSHA = $SourceSha
            TaskId = $taskId
            Reason = 'Final CLI status was not proven as Finished after the output hash matched.'
            FinalStatusCommandExitCode = $finalStatusExitCode
            FinalStatusOutputPreview = $finalStatusOutputPreview
            FinalStatusOutputLineCount = @($finalStatusOutput).Count
            FinalStatus = $finalStatus
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
        ActiveDistinctTasksAtCapture = [int]$preScreenshotStats.active_distinct_tasks
        BytesSentAtCapture = [long]$preScreenshotStats.bytes_sent
        MinimumVisualProgressBytes = $minimumVisualProgressBytes
        ProgressFractionAtCapture = [Math]::Round(([long]$preScreenshotStats.bytes_sent / [double]$expectedBytes), 3)
        BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot
        StatusPollCountAtCapture = $statusPollCount
        ExpectedBytes = $expectedBytes
        ActualBytes = (Get-Item -LiteralPath $outputPath).Length
        ExpectedSHA256 = $expectedSha
        ActualSHA256 = $actualSha
        Screenshot = [IO.Path]::GetFileName($screenshot)
        GraphToggleTested = $graphToggleTested
        GraphToggleResult = $graphToggleResult
        GraphHiddenScreenshot = if ($graphHiddenScreenshot) { [IO.Path]::GetFileName($graphHiddenScreenshot) } else { $null }
        GraphRestoredScreenshot = if ($graphRestoredScreenshot) { [IO.Path]::GetFileName($graphRestoredScreenshot) } else { $null }
        TabCaptureResult = $tabCaptureResult
        TabScreenshotFiles = @($tabCaptureFiles)
        FinalStatus = $finalStatus
        Verified = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-report.json') -Encoding utf8
    Write-Host ("PASS: visible Liquid Progress fill reached {0:P0} before screenshot ({1}/{2} bytes)." -f ([long]$preScreenshotStats.bytes_sent / [double]$expectedBytes), [long]$preScreenshotStats.bytes_sent, $expectedBytes)
    Write-Host 'PASS: active Liquid Progress screenshot captured from installed Windows GUI; 16 MiB HTTP download SHA-256 verified.'
} finally {
    if ($resumeProcess -and -not $resumeProcess.HasExited) {
        Stop-Process -Id $resumeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($addProcess -and -not $addProcess.HasExited) {
        Stop-Process -Id $addProcess.Id -Force -ErrorAction SilentlyContinue
    }
    $visible | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    if ($started -and -not $started.HasExited) {
        Stop-Process -Id $started.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
    foreach ($tempFile in @($addStdout, $addStderr, $resumeStdout, $resumeStderr)) {
        if ($tempFile) { Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue }
    }
}
