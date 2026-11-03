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
$probeAddProcess = $null
$probeStdout = $null
$probeStderr = $null
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
    public static extern bool PostMessage(IntPtr handle, uint message, IntPtr wParam, IntPtr lParam);
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

    # Open the real, installed Appearance window before starting the live
    # transfer. This proves the interactive icon studio is visible in the
    # actual Windows app; a source-only assertion cannot establish that.
    $appearancePreviewScreenshot = Join-Path $root 'hyperget-appearance-live-preview.png'
    $appearanceStudioVerified = $false
    $settingsWindowHandle = [IntPtr]::Zero
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    try {
        [System.Windows.Forms.SendKeys]::SendWait('^%s') # Actual Ctrl+Alt+S Home shortcut
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            $settingsWindowHandle = [HyperGetLiquidProgressNative]::FindVisibleWindow(
                [uint32]$window.Id, 'Settings'
            )
            if ($settingsWindowHandle -ne [IntPtr]::Zero) { break }
            Start-Sleep -Milliseconds 250
        }
        if ($settingsWindowHandle -eq [IntPtr]::Zero) {
            throw 'Ctrl+Alt+S did not open an actual HyperGet Settings window.'
        }
        [HyperGetLiquidProgressNative]::ShowWindow($settingsWindowHandle, 9) | Out-Null
        [HyperGetLiquidProgressNative]::BringWindowToTop($settingsWindowHandle) | Out-Null
        for ($focusAttempt = 0; $focusAttempt -lt 5; $focusAttempt++) {
            [HyperGetLiquidProgressNative]::SetForegroundWindow($settingsWindowHandle) | Out-Null
            if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -eq $settingsWindowHandle) { break }
            Start-Sleep -Milliseconds 150
        }
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $settingsWindowHandle) {
            throw 'Appearance capture could not foreground the real Settings window.'
        }
        Start-Sleep -Milliseconds 650 # Let Compose commit the initial Appearance layout.
        $settingsRect = New-Object HyperGetLiquidProgressNative+RECT
        if (-not [HyperGetLiquidProgressNative]::GetWindowRect($settingsWindowHandle, [ref]$settingsRect)) {
            throw 'Cannot read actual installed Appearance window bounds.'
        }
        $settingsWidth = [int]($settingsRect.Right - $settingsRect.Left)
        $settingsHeight = [int]($settingsRect.Bottom - $settingsRect.Top)
        if ($settingsWidth -lt 540 -or $settingsHeight -lt 380) {
            throw "Actual Appearance window too small for preview capture: ${settingsWidth}x${settingsHeight}."
        }
        $settingsBitmap = [System.Drawing.Bitmap]::new($settingsWidth, $settingsHeight)
        $settingsGraphics = [System.Drawing.Graphics]::FromImage($settingsBitmap)
        try {
            $settingsGraphics.CopyFromScreen(
                [System.Drawing.Point]::new($settingsRect.Left, $settingsRect.Top),
                [System.Drawing.Point]::Empty,
                [System.Drawing.Size]::new($settingsWidth, $settingsHeight)
            )
            $settingsBitmap.Save($appearancePreviewScreenshot, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $settingsGraphics.Dispose()
            $settingsBitmap.Dispose()
        }
        if ((Get-Item -LiteralPath $appearancePreviewScreenshot).Length -lt 9000) {
            throw 'Installed Appearance screenshot was empty or unexpectedly small.'
        }
        $appearanceStudioVerified = $true
        Write-Host 'PASS: installed HyperGet Settings opened by Ctrl+Alt+S; real Appearance screenshot captured.'
    } finally {
        if ($settingsWindowHandle -ne [IntPtr]::Zero) {
            # Target the Settings HWND directly. Never send a blind Alt+F4
            # that could close the main app or cancel the running fixture.
            [HyperGetLiquidProgressNative]::PostMessage(
                $settingsWindowHandle, [uint32]0x0010, [IntPtr]::Zero, [IntPtr]::Zero
            ) | Out-Null
            Start-Sleep -Milliseconds 500
        }
        [HyperGetLiquidProgressNative]::BringWindowToTop($windowHandle) | Out-Null
        [HyperGetLiquidProgressNative]::SetForegroundWindow($windowHandle) | Out-Null
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $windowHandle) {
            throw 'Appearance capture could not return focus to installed HyperGet Home.'
        }
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

    # Real Windows main-list evidence during a verified live HTTP transfer.
    # Existing installed-GUI smoke screenshots show an EMPTY list. This shot
    # reveals the actual row, status and compact Liquid progress rendering.
    # Capture BEFORE opening the details window, not a fabricated UI preview.
    $mainWindowHandle = $window.MainWindowHandle
    [HyperGetLiquidProgressNative]::ShowWindow($mainWindowHandle, 3) | Out-Null
    [HyperGetLiquidProgressNative]::SetWindowPos(
        $mainWindowHandle, [IntPtr](-1), 0, 0, 0, 0,
        [uint32](0x0001 -bor 0x0002 -bor 0x0040)
    ) | Out-Null
    [HyperGetLiquidProgressNative]::BringWindowToTop($mainWindowHandle) | Out-Null
    [HyperGetLiquidProgressNative]::SetForegroundWindow($mainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $mainWindowHandle) {
        throw 'Installed main download list was obscured during active-transfer screenshot.'
    }
    $mainRect = [HyperGetLiquidProgressNative+RECT]::new()
    if (-not [HyperGetLiquidProgressNative]::GetWindowRect($mainWindowHandle, [ref]$mainRect)) {
        throw 'Cannot resolve bounds of active installed HyperGet main window.'
    }
    $mainDwm = [HyperGetLiquidProgressNative+RECT]::new()
    if ([HyperGetLiquidProgressNative]::DwmGetWindowAttribute(
        $mainWindowHandle, 9, [ref]$mainDwm, 16
    ) -eq 0 -and $mainDwm.Right -gt $mainDwm.Left -and
        $mainDwm.Bottom -gt $mainDwm.Top) {
        $mainRect = $mainDwm
    }
    Add-Type -AssemblyName System.Windows.Forms
    $mainDisplay = [System.Windows.Forms.Screen]::FromHandle($mainWindowHandle).Bounds
    $mainOverflow = @(
        [Math]::Max(0, $mainDisplay.Left - $mainRect.Left),
        [Math]::Max(0, $mainDisplay.Top - $mainRect.Top),
        [Math]::Max(0, $mainRect.Right - $mainDisplay.Right),
        [Math]::Max(0, $mainRect.Bottom - $mainDisplay.Bottom)
    )
    if (@($mainOverflow | Where-Object { $_ -gt 12 }).Count -gt 0) {
        throw 'Main window exceeded visible Windows display bounds during active-transfer capture.'
    }
    $mainLeft = [Math]::Max($mainRect.Left, $mainDisplay.Left)
    $mainTop = [Math]::Max($mainRect.Top, $mainDisplay.Top)
    $mainWidth = [Math]::Min($mainRect.Right, $mainDisplay.Right) - $mainLeft
    $mainHeight = [Math]::Min($mainRect.Bottom, $mainDisplay.Bottom) - $mainTop
    if ($mainWidth -lt 800 -or $mainHeight -lt 500) {
        throw 'Installed main window capture is too small for useful transfer-row evidence.'
    }
    Add-Type -AssemblyName System.Drawing
    $mainDownloadListScreenshot = Join-Path $root 'hyperget-main-list-active-download.png'
    $mainBitmap = [System.Drawing.Bitmap]::new($mainWidth, $mainHeight)
    $mainGraphics = [System.Drawing.Graphics]::FromImage($mainBitmap)
    try {
        $mainGraphics.CopyFromScreen(
            [System.Drawing.Point]::new($mainLeft, $mainTop),
            [System.Drawing.Point]::Empty,
            [System.Drawing.Size]::new($mainWidth, $mainHeight)
        )
        $mainBitmap.Save($mainDownloadListScreenshot, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $mainGraphics.Dispose()
        $mainBitmap.Dispose()
    }
    # Fail on the exact visual regression that left an ice-white empty
    # channel beside the Home liquid in the dark theme. Sample only the
    # known clean 1024x728-ish runner geometry; other DPI layouts remain
    # unaccepted until explicit scaled Windows screenshots exist.
    $homeLiquidUnfilledTrackPixelRGB = $null
    $homeLiquidDarkBackgroundTested = $false
    if ($mainWidth -ge 980 -and $mainWidth -le 1080 -and
        $mainHeight -ge 680 -and $mainHeight -le 770) {
        $probeX = [int][Math]::Round($mainWidth * 0.590)
        $probeY = [int][Math]::Round($mainHeight * 0.213)
        $trackProbe = [System.Drawing.Bitmap]::FromFile($mainDownloadListScreenshot)
        try {
            $sample = $trackProbe.GetPixel($probeX, $probeY)
            $homeLiquidUnfilledTrackPixelRGB = @([int]$sample.R, [int]$sample.G, [int]$sample.B)
            if ($sample.R -ge 155 -and $sample.G -ge 165 -and $sample.B -ge 175) {
                throw "Main Home Liquid has a light/white unfilled track at ($probeX,$probeY); dark mode background regression."
            }
            $homeLiquidDarkBackgroundTested = $true
        } finally {
            $trackProbe.Dispose()
        }
    }

    # This is the real installed Windows Home screenshot. CLI/fixture checks
    # separately prove the Downloading state and the real HTTP byte stream;
    # do not assert pixel-perfect IDM parity from one hosted screenshot.
    $mainDownloadListStatusAtCapture = $statusAtCapture

    # Exercise the opt-in Columns control with a REAL mouse click on the
    # tested 1040x744 Windows visual fixture. Inspect the icon's actual pixels
    # before clicking; never blindly click unknown desktop/DPI positions.
    $columnsMenuTested = $false
    $columnsMenuResult = 'not attempted outside known Windows GUI geometry'
    $columnsMenuScreenshot = $null
    if ($mainWidth -ge 980 -and $mainWidth -le 1080 -and
        $mainHeight -ge 680 -and $mainHeight -le 770) {
        $beforeColumns = [System.Drawing.Bitmap]::FromFile($mainDownloadListScreenshot)
        try {
            # DWM cropping can remove an invisible 8px maximized-window
            # frame. Search the small button region, not one brittle pixel.
            $brightIconSamples = 0
            for ($sampleY = 102; $sampleY -le 123; $sampleY += 2) {
                for ($sampleX = ($mainWidth - 41); $sampleX -le ($mainWidth - 15); $sampleX += 2) {
                    $columnsIconPixel = $beforeColumns.GetPixel($sampleX, $sampleY)
                    if ($columnsIconPixel.R -ge 90 -and
                        $columnsIconPixel.G -ge 100 -and $columnsIconPixel.B -ge 110) {
                        $brightIconSamples++
                    }
                }
            }
            if ($brightIconSamples -lt 3) {
                throw 'Columns button icon was not visually present inside the guarded header region.'
            }
        } finally {
            $beforeColumns.Dispose()
        }
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $mainWindowHandle) {
            throw 'Main download list lost foreground before Columns button acceptance.'
        }
        # Center stays inside the 28dp control with or without DWM cropping.
        $columnsX = $mainLeft + $mainWidth - 25
        $columnsY = $mainTop + 112
        if (-not [HyperGetLiquidProgressNative]::SetCursorPos($columnsX, $columnsY)) {
            throw 'Cannot move mouse to visible Columns control.'
        }
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
        try {
            Start-Sleep -Milliseconds 550
            $columnsMenuScreenshot = Join-Path $root 'hyperget-main-columns-menu-open.png'
            $menuBitmap = [System.Drawing.Bitmap]::new($mainWidth, $mainHeight)
            $menuGraphics = [System.Drawing.Graphics]::FromImage($menuBitmap)
            try {
                $menuGraphics.CopyFromScreen(
                    [System.Drawing.Point]::new($mainLeft, $mainTop),
                    [System.Drawing.Point]::Empty,
                    [System.Drawing.Size]::new($mainWidth, $mainHeight)
                )
                $menuBitmap.Save($columnsMenuScreenshot, [System.Drawing.Imaging.ImageFormat]::Png)
                $baseBitmap = [System.Drawing.Bitmap]::FromFile($mainDownloadListScreenshot)
                try {
                    $changedSamples = 0
                    # Only inspect below the live download row. A changing
                    # Liquid progress fill must NOT falsely satisfy this gate.
                    for ($y = 180; $y -lt [Math]::Min($mainHeight - 100, 500); $y += 4) {
                        for ($x = [int]($mainWidth * 0.55); $x -lt ($mainWidth - 32); $x += 4) {
                            $a = $baseBitmap.GetPixel($x, $y)
                            $b = $menuBitmap.GetPixel($x, $y)
                            $delta = [Math]::Abs([int]$a.R - [int]$b.R) +
                                [Math]::Abs([int]$a.G - [int]$b.G) +
                                [Math]::Abs([int]$a.B - [int]$b.B)
                            if ($delta -ge 24) { $changedSamples++ }
                        }
                    }
                    if ($changedSamples -lt 400) {
                        throw 'Real Columns click did not reveal a substantial menu overlay in the guarded header region.'
                    }
                    $columnsMenuTested = $true
                    $columnsMenuResult = 'real mouse click opened Columns menu; 400+ changed pixels verified below dynamic download row'
                } finally {
                    $baseBitmap.Dispose()
                }
            } finally {
                $menuGraphics.Dispose()
                $menuBitmap.Dispose()
            }
        } finally {
            # Clicking a known empty list area dismisses the popup without
            # altering columns, sort, widths or downloads.
            [HyperGetLiquidProgressNative]::SetCursorPos(
                ($mainLeft + 500), ($mainTop + 440)
            ) | Out-Null
            [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
            [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
            Start-Sleep -Milliseconds 150
        }
    }
    Write-Host "Windows Columns menu interaction: $columnsMenuResult"


    # Acceptance for N20: search by the actual loopback URL's HOST, which
    # is absent from the filename. The active file row must remain visible.
    # This is an isolated CI task; no user sources/credentials are involved.
    $sourceUrlSearchTested = $false
    $sourceUrlSearchScreenshot = Join-Path $root 'hyperget-main-search-by-source-url.png'
    if ($mainWidth -ge 980 -and $mainWidth -le 1080 -and
        $mainHeight -ge 680 -and $mainHeight -le 770) {
        Add-Type -AssemblyName System.Windows.Forms
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $mainWindowHandle) {
            throw 'Cannot test source URL search when installed main GUI is not foreground.'
        }
        [System.Windows.Forms.SendKeys]::SendWait('^f')
        Start-Sleep -Milliseconds 200
        [System.Windows.Forms.SendKeys]::SendWait('127.0.0.1')
        Start-Sleep -Milliseconds 450
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $mainWindowHandle) {
            throw 'Source URL search was interrupted by another foreground window.'
        }
        $urlSearchImage = [System.Drawing.Bitmap]::new($mainWidth, $mainHeight)
        $urlSearchGraphics = [System.Drawing.Graphics]::FromImage($urlSearchImage)
        try {
            $urlSearchGraphics.CopyFromScreen(
                [System.Drawing.Point]::new($mainLeft, $mainTop),
                [System.Drawing.Point]::Empty,
                [System.Drawing.Size]::new($mainWidth, $mainHeight)
            )
            $urlSearchImage.Save($sourceUrlSearchScreenshot, [System.Drawing.Imaging.ImageFormat]::Png)
            $originalImage = [System.Drawing.Bitmap]::FromFile($mainDownloadListScreenshot)
            try {
                $searchTextChanged = 0
                for ($y = 14; $y -le 33; $y++) {
                    for ($x = [int]($mainWidth * 0.70); $x -lt [int]($mainWidth * 0.83); $x++) {
                        $a = $originalImage.GetPixel($x, $y)
                        $c = $urlSearchImage.GetPixel($x, $y)
                        if (([Math]::Abs([int]$a.R - [int]$c.R) +
                            [Math]::Abs([int]$a.G - [int]$c.G) +
                            [Math]::Abs([int]$a.B - [int]$c.B)) -ge 45) {
                            $searchTextChanged++
                        }
                    }
                }
                $retainedNamePixels = 0
                # Filename glyphs only; the progress bar to the right moves.
                for ($y = 132; $y -lt 158; $y += 2) {
                    for ($x = 264; $x -lt 408; $x += 2) {
                        $a = $originalImage.GetPixel($x, $y)
                        $c = $urlSearchImage.GetPixel($x, $y)
                        if ($a.R -ge 120 -and $a.G -ge 120 -and $a.B -ge 120 -and
                            ([Math]::Abs([int]$a.R - [int]$c.R) +
                            [Math]::Abs([int]$a.G - [int]$c.G) +
                            [Math]::Abs([int]$a.B - [int]$c.B)) -le 25) {
                            $retainedNamePixels++
                        }
                    }
                }
                if ($searchTextChanged -lt 65 -or $retainedNamePixels -lt 12) {
                    throw "Real URL search failed: typed search pixels=$searchTextChanged, retained filename glyph pixels=$retainedNamePixels. Query '127.0.0.1' must match link, not filename."
                }
                $sourceUrlSearchTested = $true
                Write-Host "PASS: Ctrl+F typed a loopback HOST absent from filename; real downloading row retained ($retainedNamePixels matching filename glyph pixels)."
            } finally {
                $originalImage.Dispose()
            }
        } finally {
            $urlSearchGraphics.Dispose()
            $urlSearchImage.Dispose()
            [System.Windows.Forms.SendKeys]::SendWait('^a')
            [System.Windows.Forms.SendKeys]::SendWait('{BACKSPACE}')
            Start-Sleep -Milliseconds 150
        }
    }


    # Real installed-Windows clipboard acceptance: Ctrl+V into HomeSearch,
    # followed by Ctrl+A/Ctrl+C back out of the same focused text editor.
    # A sentinel replaces the clipboard BETWEEN paste and copy, so a missed
    # copy cannot produce a false pass from the original clipboard value.
    $clipboardShortcutsTested = $false
    if ($mainWidth -ge 980 -and $mainWidth -le 1080 -and
        $mainHeight -ge 680 -and $mainHeight -le 770) {
        $windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
        if (-not (Test-Path -LiteralPath $windowsPowerShell -PathType Leaf)) {
            throw 'Real clipboard acceptance requires Windows PowerShell STA.'
        }
        function ConvertTo-ClipboardCommand {
            param([string]$Command)
            [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
        }
        function Set-ClipboardFixture {
            param([string]$Text)
            # The Windows Forms clipboard requires STA; the outer pwsh test
            # runner's apartment state is not assumed to be STA.
            $escaped = $Text.Replace("'", "''")
            $cmd = "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Clipboard]::SetText('$escaped')"
            $encoded = ConvertTo-ClipboardCommand -Command $cmd
            $p = Start-Process -FilePath $windowsPowerShell -ArgumentList @(
                '-NoProfile', '-NonInteractive', '-Sta', '-EncodedCommand', $encoded
            ) -WindowStyle Hidden -Wait -PassThru
            if ($p.ExitCode -ne 0) { throw 'Failed to seed isolated Windows test clipboard.' }
        }
        function Get-ClipboardFixture {
            $cmd = 'Add-Type -AssemblyName System.Windows.Forms; [Console]::Out.Write([System.Windows.Forms.Clipboard]::GetText())'
            $encoded = ConvertTo-ClipboardCommand -Command $cmd
            $lines = @(& $windowsPowerShell -NoProfile -NonInteractive -Sta -EncodedCommand $encoded)
            if ($LASTEXITCODE -ne 0) { throw 'Failed to read isolated Windows test clipboard.' }
            return ($lines -join "`n").Trim()
        }

        $clipboardProbe = '127.0.0.1'
        $copiedSearchText = ''
        # Foreground/focus can race with a freshly started Compose window.
        # Use a bounded real keyboard retry: NEVER accept an attempt unless
        # clipboard data copied by Ctrl+C equals the actual pasted probe.
        for ($clipboardAttempt = 1; $clipboardAttempt -le 3; $clipboardAttempt++) {
            Set-ClipboardFixture -Text $clipboardProbe
            if ((Get-ClipboardFixture) -cne $clipboardProbe) {
                throw 'Failed to verify the synthetic Windows clipboard probe before paste.'
            }
            [HyperGetLiquidProgressNative]::SetForegroundWindow($mainWindowHandle) | Out-Null
            Start-Sleep -Milliseconds 200
            if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $mainWindowHandle) {
                throw 'Clipboard shortcut acceptance cannot focus the installed Home window.'
            }
            [System.Windows.Forms.SendKeys]::SendWait('^f')
            Start-Sleep -Milliseconds 300
            [System.Windows.Forms.SendKeys]::SendWait('^a')
            [System.Windows.Forms.SendKeys]::SendWait('^v')
            Start-Sleep -Milliseconds 450
            Set-ClipboardFixture -Text 'HYPERGET-COPY-NOT-YET-TESTED'
            # Seeding the sentinel launches another Windows PowerShell STA
            # process, which can temporarily disturb native input focus.
            # Verify foreground before using the *real* Ctrl+A/C keypresses;
            # never pass on a seeded clipboard or silently skip the copy.
            $homeForegroundForCopy = $false
            for ($focusAttempt = 1; $focusAttempt -le 5; $focusAttempt++) {
                [HyperGetLiquidProgressNative]::SetForegroundWindow($mainWindowHandle) | Out-Null
                Start-Sleep -Milliseconds 150
                if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -eq $mainWindowHandle) {
                    $homeForegroundForCopy = $true
                    break
                }
            }
            if (-not $homeForegroundForCopy) {
                throw 'Clipboard copy acceptance could not restore installed Home window foreground after STA sentinel.'
            }
            # Ctrl+F explicitly re-requests the SearchBox focus after the STA
            # fixture, instead of relying on an unverified stale focus state.
            [System.Windows.Forms.SendKeys]::SendWait('^f')
            Start-Sleep -Milliseconds 250
            [System.Windows.Forms.SendKeys]::SendWait('^a')
            [System.Windows.Forms.SendKeys]::SendWait('^c')
            Start-Sleep -Milliseconds 250
            $copiedSearchText = Get-ClipboardFixture
            if ($copiedSearchText -ceq $clipboardProbe) { break }
            if ($clipboardAttempt -lt 3) {
                Write-Host "Clipboard keyboard focus race on attempt $clipboardAttempt; repeating actual keypress validation."
                Start-Sleep -Milliseconds 350
            }
        }
        if ($copiedSearchText -cne $clipboardProbe) {
            throw "Ctrl+V/Ctrl+C real HomeSearch regression: copied search text does not match the known loopback-host probe after 3 bounded attempts."
        }
        $clipboardShortcutsTested = $true
        Write-Host 'PASS: real Windows Ctrl+F, Ctrl+V, Ctrl+A and Ctrl+C preserve focused search text.'
        [HyperGetLiquidProgressNative]::SetForegroundWindow($mainWindowHandle) | Out-Null
        [System.Windows.Forms.SendKeys]::SendWait('{BACKSPACE}')
        Start-Sleep -Milliseconds 150
    }

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
    $completionPreferenceDiskTested = $false
    $completionPreferenceDiskResult = 'not attempted'
    $completionPreferenceRestartTested = $false
    $completionPreferenceRestartResult = 'not attempted'
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

                if ($tab.Name -eq 'on-completion') {
                    # Only the non-destructive completion-dialog switch is
                    # touched. Never click the shutdown-on-completion control.
                    # Verify real screen pixels before any coordinate click.
                    $preview = [System.Drawing.Bitmap]::FromFile($tabImage)
                    $safeToProbe = $false
                    $initialEnabled = $false
                    try {
                        $selectedLine = $preview.GetPixel(175, 61)
                        $toggleSample = $preview.GetPixel(($preview.Width - 55), 150)
                        $tabIsSelected = ($selectedLine.B -gt ($selectedLine.R + 90))
                        $toggleIsBlue = ($toggleSample.B -gt ($toggleSample.R + 45))
                        $toggleIsGray = (
                            [Math]::Abs([int]$toggleSample.B - [int]$toggleSample.R) -le 16 -and
                            [int]$toggleSample.R -ge 60 -and [int]$toggleSample.R -le 225
                        )
                        if ($tabIsSelected -and ($toggleIsBlue -or $toggleIsGray)) {
                            $safeToProbe = $true
                            $initialEnabled = $toggleIsBlue
                        }
                    } finally {
                        $preview.Dispose()
                    }
                    if ($safeToProbe) {
                        $settingFile = Join-Path $env:USERPROFILE (
                            ".personal-download-manager\config\download_db\extra_download_settings\{0}.json" -f $taskId
                        )
                        $allPersisted = $true
                        $clickX = $captureRight - 55
                        $clickY = $captureTop + 150
                        # Restore the original value; finished downloads intentionally
                        # clean per-file settings, so the restart probe uses a paused task.
                        foreach ($expected in @((-not $initialEnabled), $initialEnabled)) {
                            if (-not [HyperGetLiquidProgressNative]::SetCursorPos($clickX, $clickY)) {
                                throw 'Could not position mouse for completion preference test.'
                            }
                            [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
                            [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
                            $matched = $false
                            for ($attempt = 0; $attempt -lt 30; $attempt++) {
                                Start-Sleep -Milliseconds 200
                                if (Test-Path -LiteralPath $settingFile -PathType Leaf) {
                                    try {
                                        $stored = Get-Content -LiteralPath $settingFile -Raw | ConvertFrom-Json
                                        if (($stored.PSObject.Properties.Name -contains 'showCompletionDialogOverride') -and
                                            ([bool]$stored.showCompletionDialogOverride -eq [bool]$expected)) {
                                            $matched = $true
                                            break
                                        }
                                    } catch {
                                        # A transactional file rename may still be in progress.
                                    }
                                }
                            }
                            if (-not $matched) { $allPersisted = $false }
                        }
                        $completionPreferenceDiskTested = $allPersisted
                        $completionPreferenceDiskResult = if ($allPersisted) {
                            'Windows mouse toggle off/on persisted both values to per-download JSON'
                        } else {
                            'per-download completion preference disk roundtrip not confirmed'
                        }
                    } else {
                        $completionPreferenceDiskResult = 'switch or selected tab pixel confirmation unavailable'
                    }
                }
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


    # Only run this after the 16 MiB transfer was independently verified.
    # The setting was deliberately left opposite to the opening UI state,
    # which distinguishes an actual per-file restore from a global default.
    # This is an isolated runner: terminate only our own installed GUI,
    # never invoke the OS shutdown-on-completion control.

    if ($completionPreferenceDiskTested) {
        # Normal completion intentionally deletes the first task's extra settings.
        # Use a DIFFERENT, unfinished/paused task to make restart retention meaningful.
        $verifiedDownloadId = $taskId
        $probeName = 'hyperget-preference-restart.bin'
        $probeTaskId = $taskId + 1
        $probeUrl = "http://127.0.0.1:$port/fixture.bin?task=$probeName"
        $probeArgs = 'download add http --link "' + $probeUrl + '" --folder "' +
            $downloadFolder + '" --name "' + $probeName + '"'
        $probeStdout = Join-Path $env:RUNNER_TEMP ("hyperget-probe-$SourceSha.stdout.txt")
        $probeStderr = Join-Path $env:RUNNER_TEMP ("hyperget-probe-$SourceSha.stderr.txt")
        $probeAddProcess = Start-Process -FilePath $cliPath -ArgumentList $probeArgs -PassThru `
            -NoNewWindow -RedirectStandardOutput $probeStdout -RedirectStandardError $probeStderr
        $paused = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Milliseconds 300
            $statusOutput = @(& $cliPath download show --status-lines $probeTaskId 2>&1)
            if ($LASTEXITCODE -ne 0) { continue }
            $probeStatus = Get-DownloadStatusFromOutput -Lines $statusOutput -ExpectedId $probeTaskId
            if ($probeStatus -eq 'Paused') {
                $paused = $true
                break
            }
            if ($probeStatus -eq 'Downloading' -or $probeStatus -eq 'Finished') {
                throw 'Restart fixture must remain paused, never completed.'
            }
        }
        if (-not $paused) {
            throw 'Could not establish the separate paused-download preference fixture.'
        }
        $probeOpen = @(& $cliPath download open $probeTaskId 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'Could not open the paused preference fixture.' }
        $probeHandle = [IntPtr]::Zero
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            $probeHandle = [HyperGetLiquidProgressNative]::FindVisibleWindow(
                [uint32]$window.Id, $probeName
            )
            if ($probeHandle -ne [IntPtr]::Zero) { break }
            Start-Sleep -Milliseconds 250
        }
        if ($probeHandle -eq [IntPtr]::Zero) {
            throw 'Paused preference fixture download window never appeared.'
        }
        [HyperGetLiquidProgressNative]::ShowWindow($probeHandle, 3) | Out-Null
        [HyperGetLiquidProgressNative]::SetWindowPos(
            $probeHandle, [IntPtr](-1), 0, 0, 0, 0, [uint32](0x0001 -bor 0x0002 -bor 0x0040)
        ) | Out-Null
        [HyperGetLiquidProgressNative]::BringWindowToTop($probeHandle) | Out-Null
        [HyperGetLiquidProgressNative]::SetForegroundWindow($probeHandle) | Out-Null
        Start-Sleep -Milliseconds 300
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $probeHandle) {
            throw 'Paused preference fixture was not foreground before click.'
        }

    # Window frame geometry from GetWindowRect can extend by <=12 physical
    # pixels beyond the display when Compose requests SW_MAXIMIZE on Windows.
    # DWM frame bounds usually remove that margin but can be unavailable.
    # Use VISIBLE pixels for coordinate interactions. Reject genuine overflow.
    function Get-ValidatedVisibleDownloadRect {
        param([IntPtr]$Handle, [string]$ProbeName)
        $screen = [System.Windows.Forms.Screen]::FromHandle($Handle).Bounds
        $lastRaw = ''
        for ($attempt = 0; $attempt -lt 15; $attempt++) {
            $raw = [HyperGetLiquidProgressNative+RECT]::new()
            if (-not [HyperGetLiquidProgressNative]::GetWindowRect($Handle, [ref]$raw)) {
                throw "$ProbeName window geometry unavailable."
            }
            $dwm = [HyperGetLiquidProgressNative+RECT]::new()
            if ([HyperGetLiquidProgressNative]::DwmGetWindowAttribute(
                    $Handle, 9, [ref]$dwm, 16
                ) -eq 0 -and $dwm.Right -gt $dwm.Left -and
                $dwm.Bottom -gt $dwm.Top) {
                $raw = $dwm
            }
            $outsideLeft = [Math]::Max(0, $screen.Left - $raw.Left)
            $outsideTop = [Math]::Max(0, $screen.Top - $raw.Top)
            $outsideRight = [Math]::Max(0, $raw.Right - $screen.Right)
            $outsideBottom = [Math]::Max(0, $raw.Bottom - $screen.Bottom)
            $lastRaw = "window=($($raw.Left),$($raw.Top))..($($raw.Right),$($raw.Bottom)), display=($($screen.Left),$($screen.Top))..($($screen.Right),$($screen.Bottom)), overflow=$outsideLeft/$outsideTop/$outsideRight/$outsideBottom"
            if ($outsideLeft -le 12 -and $outsideTop -le 12 -and
                $outsideRight -le 12 -and $outsideBottom -le 12) {
                $visible = [HyperGetLiquidProgressNative+RECT]::new()
                $visible.Left = [Math]::Max($raw.Left, $screen.Left)
                $visible.Top = [Math]::Max($raw.Top, $screen.Top)
                $visible.Right = [Math]::Min($raw.Right, $screen.Right)
                $visible.Bottom = [Math]::Min($raw.Bottom, $screen.Bottom)
                $visibleWidth = $visible.Right - $visible.Left
                $visibleHeight = $visible.Bottom - $visible.Top
                if ($visibleWidth -ge 980 -and $visibleWidth -le 1080 -and
                    $visibleHeight -ge 680 -and $visibleHeight -le 770) {
                    return $visible
                }
            }
            # Windows/JBR can report a stale maximized size (e.g. 978x673)
            # for a secondary Compose window on a 1024x768 CI desktop. Do
            # one genuine resize/restore in this *isolated test fixture* only.
            # Never relax the pixel/geometry checks or use this fallback at a
            # different physical resolution.
            if ($attempt -eq 7 -and $screen.Width -eq 1024 -and $screen.Height -eq 768) {
                Write-Host "Restoring and resizing $ProbeName into verified 1024x768 test bounds."
                [HyperGetLiquidProgressNative]::ShowWindow($Handle, 9) | Out-Null
                if (-not [HyperGetLiquidProgressNative]::SetWindowPos(
                    $Handle, [IntPtr](-1),
                    ($screen.Left + 8), ($screen.Top + 8), 1004, 728,
                    [uint32]0x0040
                )) { throw "$ProbeName could not be resized for known CI fixture." }
                [HyperGetLiquidProgressNative]::BringWindowToTop($Handle) | Out-Null
                [HyperGetLiquidProgressNative]::SetForegroundWindow($Handle) | Out-Null
            }
            Start-Sleep -Milliseconds 200
        }
        throw "$ProbeName window never settled into the known 1024x728 visible CI layout: $lastRaw"
    }

        $probeRect = Get-ValidatedVisibleDownloadRect -Handle $probeHandle -ProbeName 'Paused preference fixture'
        $probeWidth = $probeRect.Right - $probeRect.Left
        $probeHeight = $probeRect.Bottom - $probeRect.Top
        if (-not [HyperGetLiquidProgressNative]::SetCursorPos(
            ($probeRect.Left + 174), ($probeRect.Top + 47)
        )) { throw 'Could not select paused-task On Completion tab.' }
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 450
        $probeBeforeShot = Join-Path $root 'liquid-progress-tab-on-completion-before-restart.png'
        $probeBitmap = [System.Drawing.Bitmap]::new($probeWidth, $probeHeight)
        $probeGraphics = [System.Drawing.Graphics]::FromImage($probeBitmap)
        try {
            $probeGraphics.CopyFromScreen(
                [System.Drawing.Point]::new($probeRect.Left, $probeRect.Top),
                [System.Drawing.Point]::Empty,
                [System.Drawing.Size]::new($probeWidth, $probeHeight)
            )
            $probeBitmap.Save($probeBeforeShot, [System.Drawing.Imaging.ImageFormat]::Png)
            $selected = $probeBitmap.GetPixel(175, 61)
            $sample = $probeBitmap.GetPixel(($probeWidth - 55), 150)
            $selectedConfirmed = ($selected.B -gt ($selected.R + 90))
            $isBlue = ($sample.B -gt ($sample.R + 45))
            $isGray = (
                [Math]::Abs([int]$sample.B - [int]$sample.R) -le 16 -and
                [int]$sample.R -ge 60 -and [int]$sample.R -le 225
            )
            if (-not $selectedConfirmed -or -not ($isBlue -or $isGray)) {
                throw 'Paused-task On Completion tab or safe dialog switch not confirmed.'
            }
            $initialEnabled = $isBlue
        } finally {
            $probeGraphics.Dispose()
            $probeBitmap.Dispose()
        }
        $tabCaptureFiles += [IO.Path]::GetFileName($probeBeforeShot)
        # Never click the shutdown-on-completion control, only the SECOND switch.
        if (-not [HyperGetLiquidProgressNative]::SetCursorPos(
            ($probeRect.Right - 55), ($probeRect.Top + 150)
        )) { throw 'Could not click the safe paused-task completion switch.' }
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
        $settingFile = Join-Path $env:USERPROFILE (
            ".personal-download-manager\config\download_db\extra_download_settings\{0}.json" -f $probeTaskId
        )
        $savedInverse = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            Start-Sleep -Milliseconds 200
            if (-not (Test-Path -LiteralPath $settingFile -PathType Leaf)) { continue }
            try {
                $stored = Get-Content -LiteralPath $settingFile -Raw | ConvertFrom-Json
                if (($stored.PSObject.Properties.Name -contains 'showCompletionDialogOverride') -and
                    ([bool]$stored.showCompletionDialogOverride -eq (-not $initialEnabled))) {
                    $savedInverse = $true
                    break
                }
            } catch { }
        }
        if (-not $savedInverse) {
            throw 'Paused per-file switch change was not persisted before restart.'
        }
        # Reuse the existing strict reboot/crop/pixel/restore gate below,
        # but target the new paused task rather than the completed test file.
        $taskId = $probeTaskId
        $fileName = $probeName
        $originalGuiId = $window.Id
        Stop-Process -Id $originalGuiId -Force -ErrorAction Stop
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            if (-not (Get-Process -Id $originalGuiId -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Milliseconds 250
        }
        if (Get-Process -Id $originalGuiId -ErrorAction SilentlyContinue) {
            throw 'Restart preference test could not stop the original isolated GUI.'
        }

        $restarted = Start-Process -FilePath $guiPath -PassThru
        $started = $restarted
        $restartMain = [IntPtr]::Zero
        $restartOwner = $null
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Milliseconds 300
            $candidate = @(Get-Process -Name $guiProcessName -ErrorAction SilentlyContinue |
                Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero } | Select-Object -First 1)
            if ($candidate.Count -gt 0) {
                $restartOwner = $candidate[0]
                $restartMain = $restartOwner.MainWindowHandle
                break
            }
            if ($restarted.HasExited) {
                throw 'Restarted HyperGet exited before exposing its main window.'
            }
        }
        if ($restartMain -eq [IntPtr]::Zero) {
            throw 'Restarted HyperGet did not present its main window.'
        }
        $visible = @($restartOwner)
        $openAgain = @(& $cliPath download open $taskId 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw 'Installed CLI could not reopen the same completed download after GUI restart.'
        }
        $restartDetail = [IntPtr]::Zero
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            $restartDetail = [HyperGetLiquidProgressNative]::FindVisibleWindow(
                [uint32]$restartOwner.Id, $fileName
            )
            if ($restartDetail -eq [IntPtr]::Zero) {
                $restartDetail = [HyperGetLiquidProgressNative]::FindVisibleWindowExcluding(
                    [uint32]$restartOwner.Id, $restartMain
                )
            }
            if ($restartDetail -ne [IntPtr]::Zero) { break }
            Start-Sleep -Milliseconds 250
        }
        if ($restartDetail -eq [IntPtr]::Zero) {
            throw 'Paused download details did not reopen after GUI restart.'
        }
        [HyperGetLiquidProgressNative]::ShowWindow($restartDetail, 3) | Out-Null
        [HyperGetLiquidProgressNative]::SetWindowPos(
            $restartDetail, [IntPtr](-1), 0, 0, 0, 0,
            [uint32](0x0001 -bor 0x0002 -bor 0x0040)
        ) | Out-Null
        [HyperGetLiquidProgressNative]::BringWindowToTop($restartDetail) | Out-Null
        [HyperGetLiquidProgressNative]::SetForegroundWindow($restartDetail) | Out-Null
        Start-Sleep -Milliseconds 350
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $restartDetail) {
            throw 'Restarted download details were not foreground for safe pixel verification.'
        }

        $restartRect = Get-ValidatedVisibleDownloadRect -Handle $restartDetail -ProbeName 'Restarted preference fixture'
        $restartWidth = $restartRect.Right - $restartRect.Left
        $restartHeight = $restartRect.Bottom - $restartRect.Top

        # Click ONLY the On Completion tab; the lower switch is read before
        # any further interaction. Saving a screenshot alone does not count.
        $restartTabX = $restartRect.Left + 174
        $restartTabY = $restartRect.Top + 47
        if (-not [HyperGetLiquidProgressNative]::SetCursorPos($restartTabX, $restartTabY)) {
            throw 'Could not select the On Completion tab after restart.'
        }
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 550

        $restartScreenshot = Join-Path $root 'liquid-progress-tab-on-completion-after-restart.png'
        $restartBitmap = [System.Drawing.Bitmap]::new($restartWidth, $restartHeight)
        $restartGraphics = [System.Drawing.Graphics]::FromImage($restartBitmap)
        try {
            $restartGraphics.CopyFromScreen(
                [System.Drawing.Point]::new($restartRect.Left, $restartRect.Top),
                [System.Drawing.Point]::Empty,
                [System.Drawing.Size]::new($restartWidth, $restartHeight)
            )
            $restartBitmap.Save($restartScreenshot, [System.Drawing.Imaging.ImageFormat]::Png)
            $selectionPixel = $restartBitmap.GetPixel(175, 61)
            $switchPixel = $restartBitmap.GetPixel(($restartWidth - 55), 150)
            $selectionConfirmed = ($selectionPixel.B -gt ($selectionPixel.R + 90))
            $switchBlue = ($switchPixel.B -gt ($switchPixel.R + 45))
            $switchGray = (
                [Math]::Abs([int]$switchPixel.B - [int]$switchPixel.R) -le 16 -and
                [int]$switchPixel.R -ge 60 -and [int]$switchPixel.R -le 225
            )
            if (-not $selectionConfirmed -or -not ($switchBlue -or $switchGray) -or
                $switchBlue -ne (-not $initialEnabled)) {
                throw 'Completion switch did not reload the explicit per-file override after GUI restart.'
            }
        } finally {
            $restartGraphics.Dispose()
            $restartBitmap.Dispose()
        }
        $tabCaptureFiles += [IO.Path]::GetFileName($restartScreenshot)

        # Restore the original user-facing choice, without touching the
        # separate shutdown switch, and require a real JSON disk write.
        if ([HyperGetLiquidProgressNative]::GetForegroundWindow() -ne $restartDetail -or
            -not [HyperGetLiquidProgressNative]::SetCursorPos(
                ($restartRect.Right - 55), ($restartRect.Top + 150)
            )) {
            throw 'Restarted On Completion window not safe for preference restoration.'
        }
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0002, 0, 0, 0, [UIntPtr]::Zero)
        [HyperGetLiquidProgressNative]::mouse_event([uint32]0x0004, 0, 0, 0, [UIntPtr]::Zero)
        $restoredOnDisk = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            Start-Sleep -Milliseconds 200
            try {
                $restored = Get-Content -LiteralPath $settingFile -Raw | ConvertFrom-Json
                if (($restored.PSObject.Properties.Name -contains 'showCompletionDialogOverride') -and
                    ([bool]$restored.showCompletionDialogOverride -eq [bool]$initialEnabled)) {
                    $restoredOnDisk = $true
                    break
                }
            } catch {
                # Transactional JSON rename may be in flight.
            }
        }
        if (-not $restoredOnDisk) {
            throw 'Could not restore the completion preference on disk after restart.'
        }
        $completionPreferenceRestartTested = $true
        $taskId = $verifiedDownloadId
        $completionPreferenceRestartResult =
            'Installed Windows GUI restarted; paused task reopened with saved per-file switch state, then original preference restored on disk'
        Write-Host 'PASS: completion-dialog override survives a real installed GUI restart.'
    } else {
        $completionPreferenceRestartResult = 'not tested: safe disk toggle prerequisite unavailable'
    }

    [pscustomobject]@{
        SourceSHA = $SourceSha
        AppearanceStudioVerified = $appearanceStudioVerified
        AppearancePreviewScreenshot = if ($appearanceStudioVerified) { [IO.Path]::GetFileName($appearancePreviewScreenshot) } else { $null }
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
        MainDownloadListScreenshot = [IO.Path]::GetFileName($mainDownloadListScreenshot)
        MainDownloadListStatusAtCapture = $mainDownloadListStatusAtCapture
        HomeLiquidDarkBackgroundTested = $homeLiquidDarkBackgroundTested
        HomeLiquidUnfilledTrackPixelRGB = $homeLiquidUnfilledTrackPixelRGB
        SourceUrlSearchTested = $sourceUrlSearchTested
        ClipboardShortcutsTested = $clipboardShortcutsTested
        SourceUrlSearchScreenshot = if ($sourceUrlSearchTested) { [IO.Path]::GetFileName($sourceUrlSearchScreenshot) } else { $null }
        ColumnsMenuTested = $columnsMenuTested
        ColumnsMenuResult = $columnsMenuResult
        ColumnsMenuScreenshot = if ($columnsMenuScreenshot) { [IO.Path]::GetFileName($columnsMenuScreenshot) } else { $null }
        GraphToggleTested = $graphToggleTested
        GraphToggleResult = $graphToggleResult
        GraphHiddenScreenshot = if ($graphHiddenScreenshot) { [IO.Path]::GetFileName($graphHiddenScreenshot) } else { $null }
        GraphRestoredScreenshot = if ($graphRestoredScreenshot) { [IO.Path]::GetFileName($graphRestoredScreenshot) } else { $null }
        TabCaptureResult = $tabCaptureResult
        TabScreenshotFiles = @($tabCaptureFiles)
        CompletionPreferenceDiskTested = $completionPreferenceDiskTested
        CompletionPreferenceDiskResult = $completionPreferenceDiskResult
        CompletionPreferenceRestartTested = $completionPreferenceRestartTested
        CompletionPreferenceRestartTaskId = if ($completionPreferenceRestartTested) { $probeTaskId } else { $null }
        CompletionPreferenceRestartResult = $completionPreferenceRestartResult
        FinalStatus = $finalStatus
        Verified = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'liquid-progress-report.json') -Encoding utf8
    Write-Host ("PASS: visible Liquid Progress fill reached {0:P0} before screenshot ({1}/{2} bytes)." -f ([long]$preScreenshotStats.bytes_sent / [double]$expectedBytes), [long]$preScreenshotStats.bytes_sent, $expectedBytes)
    Write-Host 'PASS: active Liquid Progress screenshot captured from installed Windows GUI; 16 MiB HTTP download SHA-256 verified.'
} finally {
    if ($probeAddProcess -and -not $probeAddProcess.HasExited) {
        Stop-Process -Id $probeAddProcess.Id -Force -ErrorAction SilentlyContinue
    }
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
    foreach ($tempFile in @($addStdout, $addStderr, $resumeStdout, $resumeStderr, $probeStdout, $probeStderr)) {
        if ($tempFile) { Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue }
    }
}
