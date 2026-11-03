<#
.SYNOPSIS
Cross-snapshot Windows upgrade and rollback acceptance for HyperGet.
.DESCRIPTION
Uses installers built from exact base/head source SHAs. The test is confined to
a fresh GitHub-hosted runner and RUNNER_TEMP install paths. It creates a real
completed loopback HTTP download and persistent setting, upgrades Download
Manager -> HyperGet, rolls back, then verifies data survives normal uninstall.
It refuses to start if the expected user-data, registry, install or shortcut
paths already exist.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BaselineInstaller,
    [Parameter(Mandatory = $true)][string] $CandidateInstaller,
    [Parameter(Mandatory = $true)][string] $BaselineSourceSha,
    [Parameter(Mandatory = $true)][string] $CandidateSourceSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')

$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([char[]]@('\','/'))
if ([string]::IsNullOrWhiteSpace($runnerTemp)) { throw 'RUNNER_TEMP is unavailable.' }
$root = Join-Path $runnerTemp 'hyperget-cross-version-upgrade-rollback-e2e'
$installDir = Join-Path $root 'application'
$fixtureDir = Join-Path $root 'fixture'
$downloadDir = Join-Path $root 'downloads'
$proofDir = [IO.Path]::GetFullPath($OutputDirectory)
$proofFile = Join-Path $proofDir 'cross-version-upgrade-rollback.txt'
$transcriptFile = Join-Path $proofDir 'cross-version-upgrade-rollback-transcript.txt'
$dataDir = Join-Path $env:USERPROFILE '.personal-download-manager'
$markerToken = [guid]::NewGuid().ToString('D')
$ownershipMarker = Join-Path $dataDir 'system/hyperget-upgrade-rollback-owned.txt'
$sentinelFile = Join-Path $dataDir 'system/hyperget-upgrade-rollback-sentinel.json'
$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DownloadManager'
$appKey = 'HKCU:\Software\DownloadManager'
$startMenuRoot = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
$legacyStartMenuShortcut = Join-Path $startMenuRoot 'Download Manager\Download Manager.lnk'
$brandedStartMenuShortcut = Join-Path $startMenuRoot 'HyperGet\HyperGet.lnk'
$brandedStartMenuFolder = Join-Path $startMenuRoot 'HyperGet'
$startMenuSentinel = Join-Path $brandedStartMenuFolder 'unrelated-user-content.sentinel'
$legacyDesktopShortcut = Join-Path $env:USERPROFILE 'Desktop\Download Manager.lnk'
$brandedDesktopShortcut = Join-Path $env:USERPROFILE 'Desktop\HyperGet.lnk'
$downloadName = 'cross-version-upgrade-rollback.bin'
$expectedDownloadSize = [long]8 * 1MB
$downloadId = $null
$expectedDownloadSha = $null
$baselineMetadata = $null
$candidateMetadata = $null
$baselineInstallerSha = $null
$candidateInstallerSha = $null
$server = $null
$transcriptStarted = $false
$testOwnedData = $false
$testCreatedRoot = $false
$script:TestTouchedInstaller = $false
$stage = 'preflight'
$script:CurrentCliPath = Join-Path $installDir 'DownloadManagerCli.exe'
$script:SentinelSha = $null
$script:StartMenuSentinelSha = $null
$script:StartMenuSentinelContent = $null
$script:StartMenuSentinelCreated = $false
$script:StartMenuFolderCreated = $false
$script:FixtureStatsUrl = $null
$script:LastStatusProbe = 'not queried'
$script:WinCredReference = [guid]::NewGuid().ToString('D')
$script:WinCredUsername = 'synthetic-upgrade-rollback'
$script:WinCredPassword = 'Synthetic-HyperGet-Only-Δ-2468'
$script:WinCredCreated = $false

if (-not ('HyperGetWinCredSyntheticFixture' -as [type])) {
Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;

public static class HyperGetWinCredSyntheticFixture
{
    private const uint CredTypeGeneric = 1;
    private const uint CredPersistLocalMachine = 2;
    private const int ErrorNotFound = 1168;
    private const string Prefix = "PersonalDownloadManager/FTP/";

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct CredentialW
    {
        public uint Flags;
        public uint Type;
        [MarshalAs(UnmanagedType.LPWStr)] public string TargetName;
        [MarshalAs(UnmanagedType.LPWStr)] public string Comment;
        public FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        [MarshalAs(UnmanagedType.LPWStr)] public string TargetAlias;
        [MarshalAs(UnmanagedType.LPWStr)] public string UserName;
    }

    [DllImport("Advapi32.dll", EntryPoint = "CredWriteW", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredWriteW(ref CredentialW credential, uint flags);

    [DllImport("Advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredReadW(string target, uint type, uint flags, out IntPtr credential);

    [DllImport("Advapi32.dll", EntryPoint = "CredDeleteW", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredDeleteW(string target, uint type, uint flags);

    [DllImport("Advapi32.dll", SetLastError = true)]
    private static extern void CredFree(IntPtr buffer);

    public static void Write(string reference, string username, string password)
    {
        if (String.IsNullOrWhiteSpace(username) || String.IsNullOrEmpty(password))
            throw new ArgumentException("Synthetic WinCred fixture input is invalid.");

        byte[] secret = Encoding.Unicode.GetBytes(password);
        IntPtr memory = IntPtr.Zero;
        try
        {
            memory = Marshal.AllocHGlobal(secret.Length);
            Marshal.Copy(secret, 0, memory, secret.Length);
            CredentialW credential = new CredentialW
            {
                Flags = 0,
                Type = CredTypeGeneric,
                TargetName = Target(reference),
                Comment = null,
                LastWritten = new FILETIME(),
                CredentialBlobSize = (uint)secret.Length,
                CredentialBlob = memory,
                Persist = CredPersistLocalMachine,
                AttributeCount = 0,
                Attributes = IntPtr.Zero,
                TargetAlias = null,
                UserName = username
            };
            if (!CredWriteW(ref credential, 0))
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Synthetic WinCred write failed.");
        }
        finally
        {
            if (memory != IntPtr.Zero)
            {
                byte[] zeros = new byte[secret.Length];
                Marshal.Copy(zeros, 0, memory, zeros.Length);
                Array.Clear(zeros, 0, zeros.Length);
                Marshal.FreeHGlobal(memory);
            }
            Array.Clear(secret, 0, secret.Length);
        }
    }

    public static void AssertPresent(string reference, string username, string password, string stage)
    {
        IntPtr pointer = IntPtr.Zero;
        if (!CredReadW(Target(reference), CredTypeGeneric, 0, out pointer))
            throw new Win32Exception(Marshal.GetLastWin32Error(), "Synthetic WinCred read failed at " + stage + ".");

        try
        {
            CredentialW credential = (CredentialW)Marshal.PtrToStructure(pointer, typeof(CredentialW));
            if (credential.Type != CredTypeGeneric || credential.Persist != CredPersistLocalMachine)
                throw new InvalidOperationException("Synthetic WinCred metadata mismatch at " + stage + ".");
            if (!String.Equals(credential.UserName, username, StringComparison.Ordinal))
                throw new InvalidOperationException("Synthetic WinCred username mismatch at " + stage + ".");

            byte[] expected = Encoding.Unicode.GetBytes(password);
            byte[] actual = null;
            try
            {
                if (credential.CredentialBlob == IntPtr.Zero || credential.CredentialBlobSize != (uint)expected.Length)
                    throw new InvalidOperationException("Synthetic WinCred blob length mismatch at " + stage + ".");
                actual = new byte[expected.Length];
                Marshal.Copy(credential.CredentialBlob, actual, 0, actual.Length);
                int difference = 0;
                for (int i = 0; i < expected.Length; i++)
                    difference |= expected[i] ^ actual[i];
                if (difference != 0)
                    throw new InvalidOperationException("Synthetic WinCred secret mismatch at " + stage + ".");
            }
            finally
            {
                Array.Clear(expected, 0, expected.Length);
                if (actual != null) Array.Clear(actual, 0, actual.Length);
            }
        }
        finally
        {
            CredFree(pointer);
        }
    }

    public static void DeleteAndAssertAbsent(string reference)
    {
        string target = Target(reference);
        if (!CredDeleteW(target, CredTypeGeneric, 0))
        {
            int deleteError = Marshal.GetLastWin32Error();
            if (deleteError != ErrorNotFound)
                throw new Win32Exception(deleteError, "Synthetic WinCred delete failed.");
        }

        IntPtr pointer = IntPtr.Zero;
        if (CredReadW(target, CredTypeGeneric, 0, out pointer))
        {
            CredFree(pointer);
            throw new InvalidOperationException("Synthetic WinCred entry remained after cleanup.");
        }
        int readError = Marshal.GetLastWin32Error();
        if (readError != ErrorNotFound)
            throw new Win32Exception(readError, "Synthetic WinCred absence check failed.");
    }

    private static string Target(string reference)
    {
        Guid parsed;
        if (!Guid.TryParseExact(reference, "D", out parsed))
            throw new ArgumentException("Synthetic WinCred reference must be a UUID.");
        return Prefix + parsed.ToString("D");
    }
}
'@
}

function Write-SyntheticWinCred {
    [HyperGetWinCredSyntheticFixture]::Write($script:WinCredReference, $script:WinCredUsername, $script:WinCredPassword)
    $script:WinCredCreated = $true
    Assert-SyntheticWinCred -Stage 'baseline before upgrade'
}

function Assert-SyntheticWinCred([string] $Stage) {
    [HyperGetWinCredSyntheticFixture]::AssertPresent($script:WinCredReference, $script:WinCredUsername, $script:WinCredPassword, $Stage)
    Write-Host ("PASS: synthetic WinCred entry survived " + $Stage + "; target and secret redacted.")
}

function Remove-SyntheticWinCred {
    [HyperGetWinCredSyntheticFixture]::DeleteAndAssertAbsent($script:WinCredReference)
    $script:WinCredCreated = $false
    Write-Host 'PASS: synthetic WinCred fixture removed and absence verified; target and secret redacted.'
}

New-Item -ItemType Directory -Path $proofDir -Force | Out-Null
Start-Transcript -Path $transcriptFile -Force | Out-Null
$transcriptStarted = $true

function Assert-UnderRunnerTemp([string] $Path, [string] $Label) {
    $full = [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\','/'))
    $prefix = $runnerTemp + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw ($Label + ' must be below RUNNER_TEMP; refused path: ' + $full)
    }
}
function Invoke-AppCli([string[]] $Arguments, [string] $Purpose) {
    if (-not (Test-Path -LiteralPath $script:CurrentCliPath -PathType Leaf)) {
        throw ('CLI missing during ' + $Purpose + ': ' + $script:CurrentCliPath)
    }
    $lines = @(& $script:CurrentCliPath @Arguments 2>&1 | ForEach-Object { "$_" })
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw ('CLI ' + $Purpose + ' failed with exit ' + $exitCode + ': ' + ($lines -join ' | '))
    }
    return $lines
}
function Get-InstalledGuiPath {
    $legacyPath = Join-Path $installDir 'DownloadManager.exe'
    $brandedPath = Join-Path $installDir 'HyperGet.exe'
    $hasLegacy = Test-Path -LiteralPath $legacyPath -PathType Leaf
    $hasBranded = Test-Path -LiteralPath $brandedPath -PathType Leaf
    if ($hasLegacy -eq $hasBranded) {
        throw "Expected exactly one GUI launcher; legacy=$hasLegacy branded=$hasBranded"
    }
    if ($hasBranded) { return $brandedPath }
    return $legacyPath
}
function Install-Snapshot([string] $InstallerPath, [string] $Label) {
    if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) {
        throw ($Label + ' installer missing: ' + $InstallerPath)
    }
    $script:TestTouchedInstaller = $true
    $process = Start-Process -FilePath $InstallerPath -ArgumentList @('/S', ("/D=" + $installDir)) -PassThru
    if (-not $process.WaitForExit(180000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw ($Label + ' silent installer timed out.')
    }
    if ($process.ExitCode -ne 0) { throw ($Label + ' installer exit code ' + $process.ExitCode + '.') }
    Start-Sleep -Seconds 2
    if (@(Get-Process -Name 'DownloadManager','HyperGet' -ErrorAction SilentlyContinue).Count -ne 0) {
        throw ($Label + ' /S install unexpectedly auto-launched the GUI.')
    }
    foreach ($name in @('DownloadManagerCli.exe','DownloadManagerNativeMessagingHost.exe','uninstall.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $installDir $name) -PathType Leaf)) {
            throw ($Label + ' installation is missing ' + $name + '.')
        }
    }
    $guiPath = Get-InstalledGuiPath
    $expectedGuiName = if ($Label -eq 'HyperGet candidate') { 'HyperGet.exe' } else { 'DownloadManager.exe' }
    if ([IO.Path]::GetFileName($guiPath) -cne $expectedGuiName) {
        throw ($Label + ' installed unexpected GUI executable ' + [IO.Path]::GetFileName($guiPath) + '; expected ' + $expectedGuiName + '.')
    }
}
function Get-InstallMetadata([string] $Label) {
    if (-not (Test-Path -LiteralPath $uninstallKey)) {
        throw ($Label + ' Add/Remove Programs registry key is missing.')
    }
    $item = Get-ItemProperty -LiteralPath $uninstallKey
    if ([string]::IsNullOrWhiteSpace([string]$item.DisplayName) -or
        [string]::IsNullOrWhiteSpace([string]$item.DisplayVersion)) {
        throw ($Label + ' install metadata has no display name/version.')
    }
    return [pscustomobject]@{
        DisplayName = [string]$item.DisplayName
        DisplayVersion = [string]$item.DisplayVersion
        GuiExecutableName = [IO.Path]::GetFileName((Get-InstalledGuiPath))
        GuiSha256 = (Get-FileHash -LiteralPath (Get-InstalledGuiPath) -Algorithm SHA256).Hash.ToLowerInvariant()
        CliSha256 = (Get-FileHash -LiteralPath (Join-Path $installDir 'DownloadManagerCli.exe') -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
function Assert-ShortcutBrand([string] $Brand) {
    $legacyStart = Test-Path -LiteralPath $legacyStartMenuShortcut
    $brandedStart = Test-Path -LiteralPath $brandedStartMenuShortcut
    $legacyDesktop = Test-Path -LiteralPath $legacyDesktopShortcut
    $brandedDesktop = Test-Path -LiteralPath $brandedDesktopShortcut
    if ($Brand -eq 'HyperGet') {
        if (-not $brandedStart -or -not $brandedDesktop -or $legacyStart -or $legacyDesktop) {
            throw "HyperGet shortcut transition failed: legacyStart=$legacyStart brandedStart=$brandedStart legacyDesktop=$legacyDesktop brandedDesktop=$brandedDesktop"
        }
    } elseif ($Brand -eq 'Download Manager') {
        if (-not $legacyStart -or -not $legacyDesktop -or $brandedStart -or $brandedDesktop) {
            throw "Rollback shortcut transition failed: legacyStart=$legacyStart brandedStart=$brandedStart legacyDesktop=$legacyDesktop brandedDesktop=$brandedDesktop"
        }
    } else {
        throw ("Unexpected shortcut brand '" + $Brand + "'.")
    }
    $expectedGuiPath = [IO.Path]::GetFullPath((Get-InstalledGuiPath))
    $expectedShortcutPaths = if ($Brand -eq 'HyperGet') {
        @($brandedStartMenuShortcut, $brandedDesktopShortcut)
    } else {
        @($legacyStartMenuShortcut, $legacyDesktopShortcut)
    }
    $shell = New-Object -ComObject WScript.Shell
    try {
        foreach ($shortcutPath in $expectedShortcutPaths) {
            $target = [string]$shell.CreateShortcut($shortcutPath).TargetPath
            if ([string]::IsNullOrWhiteSpace($target) -or
                -not [string]::Equals([IO.Path]::GetFullPath($target), $expectedGuiPath, [StringComparison]::OrdinalIgnoreCase)) {
                throw ("Shortcut target mismatch for " + $shortcutPath + ": expected " + $expectedGuiPath + ", got " + $target)
            }
        }
    } finally {
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($shell)
    }
    Write-Host ("PASS: only '" + $Brand + "' shortcuts exist and both target " + [IO.Path]::GetFileName($expectedGuiPath) + ".")
}
function Assert-StartMenuSentinel([string] $Label) {
    if (-not $script:StartMenuSentinelCreated) { throw ($Label + " Start Menu sentinel was not created by this test.") }
    if (-not (Test-Path -LiteralPath $startMenuSentinel -PathType Leaf)) { throw ($Label + " deleted unrelated Start Menu content.") }
    $actualSha = (Get-FileHash -LiteralPath $startMenuSentinel -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha -ne $script:StartMenuSentinelSha) { throw ($Label + " changed unrelated Start Menu content.") }
    Write-Host ("PASS: " + $Label + " preserved unrelated Start Menu content and SHA-256.")
}
function Start-AppAndWait {
    $guiPath = Get-InstalledGuiPath
    $script:CurrentCliPath = Join-Path $installDir 'DownloadManagerCli.exe'
    Start-Process -FilePath $guiPath -WorkingDirectory $installDir | Out-Null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $output = Invoke-AppCli -Arguments @('settings','max-concurrent-downloads') -Purpose 'GUI readiness probe'
            if (($output -join [Environment]::NewLine) -match '(?m)^\s*\d+\s*$') { return }
        } catch {
            # The single-instance IPC endpoint is not ready yet; keep the wait bounded.
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'Installed GUI/CLI IPC did not become ready within 30 seconds.'
}
function Stop-AppGracefully {
    if (Test-Path -LiteralPath $script:CurrentCliPath -PathType Leaf) {
        try { Invoke-AppCli -Arguments @('gui','exit') -Purpose 'graceful GUI shutdown' | Out-Null } catch {}
    }
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if (@(Get-Process -Name 'DownloadManager','HyperGet' -ErrorAction SilentlyContinue).Count -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw 'GUI did not exit after the supported gui exit command.'
}
function Read-MaxConcurrent {
    $output = Invoke-AppCli -Arguments @('settings','max-concurrent-downloads') -Purpose 'read persisted concurrency'
    $numbers = @(
        foreach ($line in $output) {
            $value = ([string]$line).Trim()
            if ($value -match '^\d+$') { [int]$value }
        }
    )
    if ($numbers.Count -eq 0) { throw ('No numeric max-concurrent setting in CLI output: ' + ($output -join ' | ')) }
    return $numbers[-1]
}
function Get-FixtureStatsSummary {
    if ([string]::IsNullOrWhiteSpace($script:FixtureStatsUrl)) { return 'unavailable=no-fixture-url' }
    try {
        $stats = Invoke-RestMethod -Uri $script:FixtureStatsUrl -TimeoutSec 3
        return ('requests={0};bytesSent={1};peakRequests={2};peakDistinctTasks={3}' -f
            [long]$stats.total_requests,
            [long]$stats.bytes_sent,
            [long]$stats.peak_requests,
            [long]$stats.peak_distinct_tasks)
    } catch {
        return ('unavailable=' + $_.Exception.GetType().Name)
    }
}
function Read-DownloadStatus([long] $Id) {
    # The exact baseline predates machine-readable status flags; inspect its standard table.
    $output = Invoke-AppCli -Arguments @('download','show',([string]$Id)) -Purpose ('read download ' + $Id)
    $script:LastStatusProbe = 'no matching row'
    $rowStartPattern = '^\s*' + [regex]::Escape([string]$Id) + '(?:\s|$)'
    $anyRowStartPattern = '^\s*\d+(?:\s|$)'
    # The baseline CLI's fixed-width table can truncate Finished to Finish…; PowerShell may render the ellipsis as '?'.
    # Match only the status cell at the start of the ID row (or a wrapped continuation), never a download name.
    $statusPattern = '^(?<status>Downloading|Finished|Finish(?:[?…�])?|Error|Paused|PreparingFile|Resuming|Retrying)(?=$|[\s?])'
    $normalizedLines = @(
        foreach ($entry in $output) {
            # Native PowerShell capture may yield one multiline string or multiple line objects;
            # handle CRLF, LF, and CR, then strip ANSI and Mordant table separators.
            foreach ($line in ([string]$entry -split '\r\n|\n|\r')) {
                $plain = [regex]::Replace([string]$line, '\x1B\[[0-?]*[ -/]*[@-~]', '')
                $plain = $plain -replace '[│┃|]', ' '
                if (-not [string]::IsNullOrWhiteSpace($plain)) {
                    # Native Windows capture can turn Mordant's outer box glyphs into '?'.
                    # Strip only line-edge table markers; preserve the '?' after a truncated status cell.
                    $plain.Trim([char[]]@(' ', '?'))
                }
            }
        }
    )

    # Some terminal widths wrap a table row so ID and Status land on separate physical lines.
    # Once the requested ID row is seen, accept its status cell until another numeric-ID row
    # starts; never borrow a neighboring task's state.
    $insideTargetRow = $false
    foreach ($plain in $normalizedLines) {
        if ($plain -match 'No downloads found') {
            $script:LastStatusProbe = 'CLI reported no downloads'
            $insideTargetRow = $false
            continue
        }

        if ($plain -match $rowStartPattern) {
            # Strip the requested numeric ID first, then parse only the first status cell.
            # This avoids mistaking a download name containing a status word for the state.
            $statusText = [regex]::Replace($plain, $rowStartPattern, '')
            $statusText = $statusText.TrimStart([char[]]@(' ', '?'))
            $statusMatch = [regex]::Match($statusText, $statusPattern)
            if ($statusMatch.Success) {
                $status = $statusMatch.Groups['status'].Value
                if ($status -like 'Finish*') { $status = 'Finished' }
                $script:LastStatusProbe = ('id={0};status={1}' -f $Id, $status)
                return $status
            }
            $insideTargetRow = $true
            $script:LastStatusProbe = 'matching ID row without recognized status'
            continue
        }

        # Check the row boundary before accepting a status token; a neighboring
        # numeric ID row must never satisfy the requested download.
        if ($insideTargetRow -and $plain -match $anyRowStartPattern) {
            $insideTargetRow = $false
        }

        if ($insideTargetRow) {
            # Wrapped status cells may begin after table/ellipsis characters.
            $wrappedText = $plain.TrimStart([char[]]@(' ', '?'))
            $statusMatch = [regex]::Match($wrappedText, $statusPattern)
            if ($statusMatch.Success) {
                $status = $statusMatch.Groups['status'].Value
                if ($status -like 'Finish*') { $status = 'Finished' }
                $script:LastStatusProbe = ('id={0};wrapped-status={1}' -f $Id, $status)
                return $status
            }
        }
    }
    if ($script:LastStatusProbe -eq 'no matching row') {
        # On a failed poll, emit a short diagnostic shape rather than silently
        # repeating the same generic message. Redact URLs, paths, fixture names,
        # profile paths, and synthetic WinCred values before they reach CI logs.
        $preview = @(
            $normalizedLines | Select-Object -First 8 | ForEach-Object {
                $safeLine = [string]$_
                foreach ($pathToRedact in @(
                    $env:USERPROFILE, $env:APPDATA, $runnerTemp, $installDir, $downloadDir, $fixtureDir
                )) {
                    $value = [string]$pathToRedact
                    if (-not [string]::IsNullOrEmpty($value)) {
                        $safeLine = $safeLine.Replace($value, '[PATH]')
                    }
                }
                foreach ($sensitive in @(
                    $script:WinCredReference, $script:WinCredUsername, $script:WinCredPassword, $downloadName
                )) {
                    $value = [string]$sensitive
                    if (-not [string]::IsNullOrEmpty($value)) {
                        $safeLine = $safeLine.Replace($value, '[redacted]')
                    }
                }
                $safeLine = [regex]::Replace($safeLine, '(?i)https?://\S+', '[URL]')
                if ($safeLine.Length -gt 120) { $safeLine = $safeLine.Substring(0, 120) }
                $safeLine
            }
        )
        $script:LastStatusProbe = 'no matching row; lineCount=' + $normalizedLines.Count + '; sample=' + ($preview -join ' || ')
    }
    return $null
}

function Wait-ForFinishedDownload([long] $Id, [string] $FilePath) {
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    $lastStatus = '__not-observed__'
    $unrecognizedSince = $null
    $noBytesStatus = $null
    $noBytesSince = $null
    $lastFileState = 'not-inspected'
    while ([DateTime]::UtcNow -lt $deadline) {
        $status = Read-DownloadStatus -Id $Id
        if ($status -ne $lastStatus) {
            $displayStatus = if ($null -eq $status) { 'UNPARSED' } else { [string]$status }
            Write-Host ("Baseline download status: id={0}, state={1}" -f $Id, $displayStatus)
            $lastStatus = $status
        }
        if ($null -eq $status) {
            if ($null -eq $unrecognizedSince) { $unrecognizedSince = [DateTime]::UtcNow }
            if (([DateTime]::UtcNow - $unrecognizedSince).TotalSeconds -ge 30) {
                $fixture = Get-FixtureStatsSummary
                $fileSummary = 'missing'
                if (Test-Path -LiteralPath $FilePath -PathType Leaf) {
                    $item = Get-Item -LiteralPath $FilePath
                    $fileSummary = 'length=' + $item.Length
                    if ($item.Length -eq $expectedDownloadSize) {
                        $hash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
                        $fileSummary += ';sha256=' + $(if ($hash -eq $expectedDownloadSha) { 'match' } else { 'mismatch' })
                    }
                }
                throw ("Baseline download status was not observable for 30 seconds; id={0}; lastProbe={1}; file={2}; fixture={3}" -f $Id, $script:LastStatusProbe, $fileSummary, $fixture)
            }
        } else {
            $unrecognizedSince = $null
        }
        if ($status -eq 'Error') {
            throw ('Download ' + $Id + ' entered Error status; fixture=' + (Get-FixtureStatsSummary))
        }
        if ($status -in @('Paused','Downloading')) {
            if ($status -ne $noBytesStatus) {
                $noBytesStatus = $status
                $noBytesSince = [DateTime]::UtcNow
            } elseif ($null -ne $noBytesSince -and ([DateTime]::UtcNow - $noBytesSince).TotalSeconds -ge 20) {
                $fixture = Get-FixtureStatsSummary
                if ($fixture -match 'bytesSent=0(?:;|$)') {
                    throw ("Baseline download remained {0} after --start with zero fixture bytes; id={1}; lastProbe={2}; fixture={3}" -f $status, $Id, $script:LastStatusProbe, $fixture)
                }
                $noBytesSince = [DateTime]::UtcNow
            }
        } else {
            $noBytesStatus = $null
            $noBytesSince = $null
        }
        if ($status -eq 'Finished') {
            if (Test-Path -LiteralPath $FilePath -PathType Leaf) {
                $item = Get-Item -LiteralPath $FilePath
                $lastFileState = 'length=' + $item.Length
                if ($item.Length -eq $expectedDownloadSize) {
                    $hash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
                    if ($hash -eq $expectedDownloadSha) { return }
                    $lastFileState = 'length=' + $item.Length + ';sha256=mismatch'
                }
            } else {
                $lastFileState = 'missing'
            }
        } elseif (Test-Path -LiteralPath $FilePath -PathType Leaf) {
            $item = Get-Item -LiteralPath $FilePath
            $lastFileState = 'length=' + $item.Length
        }
        Start-Sleep -Milliseconds 500
    }
    $fixture = Get-FixtureStatsSummary
    if (Test-Path -LiteralPath $FilePath -PathType Leaf) {
        $item = Get-Item -LiteralPath $FilePath
        $lastFileState = 'length=' + $item.Length
        if ($item.Length -eq $expectedDownloadSize) {
            $hash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
            $lastFileState += ';sha256=' + $(if ($hash -eq $expectedDownloadSha) { 'match' } else { 'mismatch' })
        }
    }
    throw ("Download {0} did not reach Finished with exact size and SHA-256; lastStatus={1}; lastProbe={2}; file={3}; fixture={4}" -f $Id, $lastStatus, $script:LastStatusProbe, $lastFileState, $fixture)
}
function Assert-UserState([string] $Label) {
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) { throw ($Label + ' lost the user-data sentinel.') }
    $sentinelHash = (Get-FileHash -LiteralPath $sentinelFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sentinelHash -ne $script:SentinelSha) { throw ($Label + ' changed the user-data sentinel.') }
    $setting = Read-MaxConcurrent
    if ($setting -ne 4) { throw ($Label + ' did not preserve max-concurrent=4; got ' + $setting + '.') }
    $status = Read-DownloadStatus -Id $downloadId
    if ($status -ne 'Finished') { throw ($Label + ' did not preserve download ' + $downloadId + ' as Finished; status=' + $status + '.') }
    $downloadPath = Join-Path $downloadDir $downloadName
    if (-not (Test-Path -LiteralPath $downloadPath -PathType Leaf)) { throw ($Label + ' lost the completed download output.') }
    $hash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $expectedDownloadSha) { throw ($Label + ' changed the completed download output SHA-256.') }
    Write-Host ("PASS: " + $Label + " preserved setting=4, task=" + $downloadId + " Finished, sentinel and output SHA-256.")
}

try {
    Assert-UnderRunnerTemp -Path $installDir -Label 'Install directory'
    if ($BaselineSourceSha -eq $CandidateSourceSha) { throw 'Baseline and candidate source SHAs must differ.' }
    foreach ($path in @($BaselineInstaller,$CandidateInstaller)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Installer missing: ' + $path) }
    }
    if (Test-Path -LiteralPath $root) { throw ('Refusing to use pre-existing test root: ' + $root) }
    if (Test-Path -LiteralPath $installDir) { throw ('Refusing to overwrite pre-existing test install path: ' + $installDir) }
    if (Test-Path -LiteralPath $dataDir) { throw ('Refusing to touch pre-existing user data: ' + $dataDir) }
    foreach ($key in @($uninstallKey,$appKey)) {
        if (Test-Path -LiteralPath $key) { throw ('Refusing to modify pre-existing product registry key: ' + $key) }
    }
    foreach ($path in @($legacyStartMenuShortcut,$brandedStartMenuShortcut,$legacyDesktopShortcut,$brandedDesktopShortcut)) {
        if (Test-Path -LiteralPath $path) { throw ('Refusing to modify pre-existing product shortcut: ' + $path) }
    }
    if (Test-Path -LiteralPath $brandedStartMenuFolder) {
        throw ('Refusing to modify pre-existing HyperGet Start Menu folder: ' + $brandedStartMenuFolder)
    }

    New-Item -ItemType Directory -Path $root,$fixtureDir,$downloadDir -Force | Out-Null
    $testCreatedRoot = $true
    $baselineInstallerSha = (Get-FileHash -LiteralPath $BaselineInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
    $candidateInstallerSha = (Get-FileHash -LiteralPath $CandidateInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($baselineInstallerSha -eq $candidateInstallerSha) { throw 'Baseline and candidate installer bytes are identical; refusing a false cross-version pass.' }

    $stage = 'install baseline snapshot'
    Install-Snapshot -InstallerPath ([IO.Path]::GetFullPath($BaselineInstaller)) -Label 'Baseline'
    $baselineMetadata = Get-InstallMetadata -Label 'Baseline'
    if ($baselineMetadata.DisplayName -ne 'Download Manager') {
        throw ("Expected baseline legacy display name 'Download Manager'; got '" + $baselineMetadata.DisplayName + "'.")
    }
    Assert-ShortcutBrand -Brand 'Download Manager'

    # The profile data directory was absent at preflight, so this marker
    # proves that cleanup may remove only this run's newly created data tree.
    New-Item -ItemType Directory -Path (Join-Path $dataDir 'system') -Force | Out-Null
    Set-Content -LiteralPath $ownershipMarker -Value $markerToken -NoNewline -Encoding utf8
    $testOwnedData = $true
    Set-Content -LiteralPath $sentinelFile -Value ('upgrade-rollback-proof:' + $markerToken) -NoNewline -Encoding utf8
    $script:SentinelSha = (Get-FileHash -LiteralPath $sentinelFile -Algorithm SHA256).Hash.ToLowerInvariant()

    $stage = 'create unrelated Start Menu content sentinel'
    New-Item -ItemType Directory -Path $brandedStartMenuFolder | Out-Null
    $script:StartMenuFolderCreated = $true
    $script:StartMenuSentinelContent = 'unrelated-start-menu-content:' + $markerToken
    Set-Content -LiteralPath $startMenuSentinel -Value $script:StartMenuSentinelContent -NoNewline -Encoding utf8
    $script:StartMenuSentinelSha = (Get-FileHash -LiteralPath $startMenuSentinel -Algorithm SHA256).Hash.ToLowerInvariant()
    $script:StartMenuSentinelCreated = $true
    Assert-StartMenuSentinel -Label 'pre-upgrade fixture'

    $stage = 'create real persisted state on baseline'
    Start-AppAndWait
    Invoke-AppCli -Arguments @('settings','max-concurrent-downloads','--set','4') -Purpose 'set baseline concurrency' | Out-Null
    if ((Read-MaxConcurrent) -ne 4) { throw 'Baseline failed to persist max-concurrent=4.' }

    $fixture = Join-Path $fixtureDir 'upgrade-rollback-fixture.bin'
    & python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*32768)' $fixture
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create deterministic 8 MiB HTTP fixture.' }
    $expectedDownloadSha = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    $portFile = Join-Path $fixtureDir 'port.txt'
    $fixtureScript = Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts/LocalHttpRangeFixture.py'
    if (-not (Test-Path -LiteralPath $fixtureScript -PathType Leaf)) { throw ('Local HTTP fixture script missing: ' + $fixtureScript) }
    $serverOut = Join-Path $fixtureDir 'server-stdout.txt'
    $serverErr = Join-Path $fixtureDir 'server-stderr.txt'
    $serverArgs = @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $fixture + '"'),
        '--port-file', ('"' + $portFile + '"'), '--chunk-delay-ms', '5'
    )
    $server = Start-Process -FilePath 'python' -ArgumentList $serverArgs -WorkingDirectory (Split-Path $PSScriptRoot -Parent) -PassThru -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portFile -Server $server -FixtureName 'Cross-version HTTP fixture' -Attempts 80 -ServerErrorPath $serverErr
    $url = "http://127.0.0.1:$port/fixture.bin"
    # Validate and log only the synthetic loopback endpoint shape before passing it to the app.
    # Do not log arbitrary download URLs, query strings, headers, or credentials.
    try {
        $parsedLoopbackUrl = [System.Uri]::new($url, [System.UriKind]::Absolute)
    } catch {
        throw 'Loopback HTTP URL preflight failed: URI was not absolute.'
    }
    if ($parsedLoopbackUrl.Scheme -ne 'http' -or
        $parsedLoopbackUrl.Host -ne '127.0.0.1' -or
        $parsedLoopbackUrl.Port -lt 1 -or
        $parsedLoopbackUrl.AbsolutePath -ne '/fixture.bin' -or
        -not [string]::IsNullOrEmpty($parsedLoopbackUrl.Query)) {
        throw 'Loopback HTTP URL preflight failed: unexpected scheme, host, port, path, or query.'
    }
    Write-Host ("Loopback URL preflight PASS: host=127.0.0.1 port={0} path=/fixture.bin; no query; length={1}" -f $parsedLoopbackUrl.Port, $url.Length)
    $script:FixtureStatsUrl = "http://127.0.0.1:$port/stats"
    # Match the direct native CLI invocation used by the already-passing installed HTTP E2E.
    # This removes the wrapper/splatting difference from this cross-version test.
    $addOutputRaw = & $script:CurrentCliPath download add http --link $url --folder $downloadDir --name $downloadName --start 2>&1
    $addExitCode = $LASTEXITCODE
    $addOutput = @($addOutputRaw | ForEach-Object { "$_" })
    if ($addExitCode -ne 0) {
        throw ('CLI create baseline HTTP download failed with exit ' + $addExitCode + ': ' + ($addOutput -join ' | '))
    }
    $idLines = @(
        foreach ($line in $addOutput) {
            $value = ([string]$line).Trim()
            if ($value -match '^\d+$') { $value }
        }
    )
    if ($idLines.Count -ne 1) { throw ('Expected exactly one numeric download ID; CLI output: ' + ($addOutput -join ' | ')) }
    $downloadId = [long]$idLines[0]
    Write-Host ("Baseline HTTP download created: id={0}; expectedBytes={1}; fixture={2}" -f $downloadId, $expectedDownloadSize, (Get-FixtureStatsSummary))
    $downloadPath = Join-Path $downloadDir $downloadName
    Wait-ForFinishedDownload -Id $downloadId -FilePath $downloadPath
    Stop-AppGracefully
    $baselineMetadata = Get-InstallMetadata -Label 'Baseline after real transfer'
    $stage = 'write synthetic WinCred fixture before upgrade'
    Write-SyntheticWinCred

    $stage = 'upgrade baseline to HyperGet candidate'
    Install-Snapshot -InstallerPath ([IO.Path]::GetFullPath($CandidateInstaller)) -Label 'HyperGet candidate'
    $candidateMetadata = Get-InstallMetadata -Label 'HyperGet candidate'
    if ($candidateMetadata.DisplayName -ne 'HyperGet') { throw ("Candidate display name is '" + $candidateMetadata.DisplayName + "', expected HyperGet.") }
    if ($candidateMetadata.DisplayVersion -eq $baselineMetadata.DisplayVersion) {
        throw 'Baseline and candidate DisplayVersion are identical; the run cannot prove a versioned upgrade.'
    }
    Assert-ShortcutBrand -Brand 'HyperGet'
    Assert-StartMenuSentinel -Label 'upgrade to HyperGet'
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) { throw 'Upgrade deleted user data.' }
    Start-AppAndWait
    Assert-UserState -Label 'Upgrade to HyperGet'
    Assert-SyntheticWinCred -Stage 'upgrade to HyperGet'
    Stop-AppGracefully

    $stage = 'remove candidate while preserving user data before rollback'
    $candidateUninstaller = Join-Path $installDir 'uninstall.exe'
    $candidateUninstall = Start-Process -FilePath $candidateUninstaller -ArgumentList @('/S') -PassThru
    if (-not $candidateUninstall.WaitForExit(180000)) {
        Stop-Process -Id $candidateUninstall.Id -Force -ErrorAction SilentlyContinue
        throw 'HyperGet candidate uninstall timed out before rollback.'
    }
    if ($candidateUninstall.ExitCode -ne 0) { throw ('HyperGet candidate uninstall exit code ' + $candidateUninstall.ExitCode + '.') }
    Start-Sleep -Seconds 2
    foreach ($name in @('HyperGet.exe','DownloadManager.exe','DownloadManagerCli.exe','DownloadManagerNativeMessagingHost.exe','uninstall.exe')) {
        if (Test-Path -LiteralPath (Join-Path $installDir $name)) { throw ('Candidate uninstall left ' + $name + ' behind.') }
    }
    foreach ($key in @($uninstallKey,$appKey)) {
        if (Test-Path -LiteralPath $key) { throw ('Candidate uninstall left registry key ' + $key + '.') }
    }
    foreach ($path in @($legacyStartMenuShortcut,$brandedStartMenuShortcut,$legacyDesktopShortcut,$brandedDesktopShortcut)) {
        if (Test-Path -LiteralPath $path) { throw ('Candidate uninstall left product shortcut ' + $path + '.') }
    }
    Assert-StartMenuSentinel -Label 'candidate uninstall before rollback'
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) { throw 'Candidate uninstall removed user data before rollback.' }
    if ((Get-FileHash -LiteralPath $sentinelFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $script:SentinelSha) {
        throw 'Candidate uninstall changed the user-data sentinel before rollback.'
    }
    Assert-SyntheticWinCred -Stage 'candidate uninstall before rollback'
    $stage = 'reinstall baseline snapshot after preserving-data rollback'
    Install-Snapshot -InstallerPath ([IO.Path]::GetFullPath($BaselineInstaller)) -Label 'Rollback to baseline'
    $rollbackMetadata = Get-InstallMetadata -Label 'Rollback to baseline'
    if ($rollbackMetadata.DisplayName -ne 'Download Manager' -or
        $rollbackMetadata.DisplayVersion -ne $baselineMetadata.DisplayVersion) {
        throw ('Rollback did not restore baseline metadata: name=' + $rollbackMetadata.DisplayName + ' version=' + $rollbackMetadata.DisplayVersion)
    }
    Assert-ShortcutBrand -Brand 'Download Manager'
    Assert-StartMenuSentinel -Label 'rollback to baseline'
    Assert-SyntheticWinCred -Stage 'rollback to baseline'
    Start-AppAndWait
    Assert-UserState -Label 'Rollback to baseline'
    Stop-AppGracefully

    $stage = 'normal uninstall preserves user data'
    $uninstaller = Join-Path $installDir 'uninstall.exe'
    $uninstall = Start-Process -FilePath $uninstaller -ArgumentList @('/S') -PassThru
    if (-not $uninstall.WaitForExit(180000)) {
        Stop-Process -Id $uninstall.Id -Force -ErrorAction SilentlyContinue
        throw 'Baseline silent uninstall timed out.'
    }
    if ($uninstall.ExitCode -ne 0) { throw ('Baseline silent uninstall exit code ' + $uninstall.ExitCode + '.') }
    Start-Sleep -Seconds 2
    foreach ($name in @('HyperGet.exe','DownloadManager.exe','DownloadManagerCli.exe','DownloadManagerNativeMessagingHost.exe','uninstall.exe')) {
        if (Test-Path -LiteralPath (Join-Path $installDir $name)) { throw ('Uninstall left ' + $name + ' behind.') }
    }
    foreach ($key in @($uninstallKey,$appKey)) {
        if (Test-Path -LiteralPath $key) { throw ('Uninstall left registry key ' + $key + '.') }
    }
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) {
        throw 'Normal uninstall removed user data; this must remain opt-in.'
    }
    $afterUninstallSentinelSha = (Get-FileHash -LiteralPath $sentinelFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($afterUninstallSentinelSha -ne $script:SentinelSha) { throw 'Normal uninstall changed user data.' }
    foreach ($path in @($legacyStartMenuShortcut,$brandedStartMenuShortcut,$legacyDesktopShortcut,$brandedDesktopShortcut)) {
        if (Test-Path -LiteralPath $path) { throw ('Uninstall left product shortcut ' + $path + '.') }
    }
    Assert-StartMenuSentinel -Label 'normal uninstall'
    Assert-SyntheticWinCred -Stage 'normal uninstall'
    Remove-SyntheticWinCred

    $stage = 'write passing proof'
    @(
        'Result=PASS'
        "BaselineSourceSHA=$BaselineSourceSha"
        "CandidateSourceSHA=$CandidateSourceSha"
        "BaselineInstallerSHA256=$baselineInstallerSha"
        "CandidateInstallerSHA256=$candidateInstallerSha"
        "BaselineDisplayName=$($baselineMetadata.DisplayName)"
        "BaselineDisplayVersion=$($baselineMetadata.DisplayVersion)"
        "CandidateDisplayName=$($candidateMetadata.DisplayName)"
        "CandidateDisplayVersion=$($candidateMetadata.DisplayVersion)"
        "BaselineGuiSHA256=$($baselineMetadata.GuiSha256)"
        "CandidateGuiSHA256=$($candidateMetadata.GuiSha256)"
        "DownloadId=$downloadId"
        "DownloadBytes=$expectedDownloadSize"
        "DownloadExpectedSHA256=$expectedDownloadSha"
        "DownloadActualSHA256=$((Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant())"
        'MaxConcurrentPreserved=4'
        "UserDataSentinelSHA256=$script:SentinelSha"
        'UpgradeDataAndHistory=PASS'
        'RollbackDataAndHistory=PASS'
        'CandidateUninstallBeforeRollback=PASS'
        'ShortcutTransitions=PASS'
        'StartMenuUserContentSentinelPreserved=PASS'
        "StartMenuUserContentSentinelSHA256=$script:StartMenuSentinelSha"
        'NormalUninstallPreservesUserData=PASS'
        'SyntheticWinCredPreservedAcrossUpgradeRollbackAndUninstall=PASS'
        'SyntheticWinCredCleanup=PASS'
    ) | Set-Content -LiteralPath $proofFile -Encoding utf8
    Write-Host ("PASS: cross-version upgrade and rollback completed. Proof: " + $proofFile)
} catch {
    @(
        'Result=FAIL'
        "Stage=$stage"
        "BaselineSourceSHA=$BaselineSourceSha"
        "CandidateSourceSHA=$CandidateSourceSha"
        ("Error=" + $_.Exception.Message)
    ) | Set-Content -LiteralPath $proofFile -Encoding utf8
    throw
} finally {
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:TestTouchedInstaller) {
        if (Test-Path -LiteralPath $script:CurrentCliPath -PathType Leaf) {
            try { & $script:CurrentCliPath gui exit 2>&1 | Out-Null } catch {}
        }
        Start-Sleep -Milliseconds 500
        Get-Process -Name 'DownloadManager','HyperGet' -ErrorAction SilentlyContinue |
            Stop-Process -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath (Join-Path $installDir 'uninstall.exe') -PathType Leaf) {
            try {
                $cleanup = Start-Process -FilePath (Join-Path $installDir 'uninstall.exe') -ArgumentList @('/S') -PassThru
                if (-not $cleanup.WaitForExit(60000)) { Stop-Process -Id $cleanup.Id -Force -ErrorAction SilentlyContinue }
            } catch {
                Write-Warning ("Best-effort test install cleanup failed: " + $_.Exception.Message)
            }
        }
    }
    if ($script:WinCredCreated) {
        try {
            [HyperGetWinCredSyntheticFixture]::DeleteAndAssertAbsent($script:WinCredReference)
            $script:WinCredCreated = $false
            Write-Host 'Cleaned up synthetic WinCred fixture after an incomplete run; target and secret redacted.'
        } catch {
            Write-Warning 'Synthetic WinCred cleanup failed after an incomplete run; target and secret redacted.'
        }
    }
    $script:WinCredPassword = $null
    $script:WinCredReference = $null
    if ($testOwnedData -and (Test-Path -LiteralPath $ownershipMarker -PathType Leaf)) {
        $owner = (Get-Content -LiteralPath $ownershipMarker -Raw).Trim()
        $actualDataPath = [IO.Path]::GetFullPath($dataDir).TrimEnd([char[]]@('\','/'))
        $expectedDataPath = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.personal-download-manager')).TrimEnd([char[]]@('\','/'))
        if ($owner -eq $markerToken -and $actualDataPath.Equals($expectedDataPath, [StringComparison]::OrdinalIgnoreCase)) {
            try {
                Remove-Item -LiteralPath $dataDir -Recurse -Force
                Write-Host 'Removed only the test-created .personal-download-manager tree after ownership-marker verification.'
            } catch {
                Write-Warning ("Could not remove test-owned data tree: " + $_.Exception.Message)
            }
        } else {
            Write-Warning 'Did not remove user data because the ownership marker/path check failed.'
        }
    }
    if ($script:TestTouchedInstaller) {
        foreach ($path in @($legacyStartMenuShortcut,$brandedStartMenuShortcut,$legacyDesktopShortcut,$brandedDesktopShortcut)) {
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
    }
    if ($script:StartMenuSentinelCreated -and (Test-Path -LiteralPath $startMenuSentinel -PathType Leaf)) {
        try {
            $actualContent = Get-Content -LiteralPath $startMenuSentinel -Raw
            $actualSha = (Get-FileHash -LiteralPath $startMenuSentinel -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualContent -ne $script:StartMenuSentinelContent -or $actualSha -ne $script:StartMenuSentinelSha) {
                throw 'Start Menu sentinel changed; preserving it instead of deleting it.'
            }
            Remove-Item -LiteralPath $startMenuSentinel -Force -ErrorAction Stop
            Write-Host 'Removed only the test-owned Start Menu sentinel after exact content and SHA-256 verification.'
        } catch {
            Write-Warning ("Start Menu sentinel cleanup refused or failed: " + $_.Exception.Message)
        }
    }
    if ($script:StartMenuFolderCreated -and (Test-Path -LiteralPath $brandedStartMenuFolder -PathType Container)) {
        try {
            Remove-Item -LiteralPath $brandedStartMenuFolder -Force -ErrorAction Stop
            Write-Host 'Removed only the empty test-created HyperGet Start Menu folder.'
        } catch {
            Write-Warning 'Preserved the HyperGet Start Menu folder because it is no longer empty or could not be safely removed.'
        }
    }
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
    if ($testCreatedRoot -and (Test-Path -LiteralPath $root)) {
        try { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop } catch {
            Write-Warning ("Could not remove test build/install directory: " + $_.Exception.Message)
        }
    }
}
