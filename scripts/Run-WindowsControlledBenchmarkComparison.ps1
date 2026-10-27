#requires -Version 7.0
<#
.SYNOPSIS
Runs a paired, same-runner Windows performance comparison for two exact HyperGet source snapshots.
.DESCRIPTION
Installs each snapshot in alternating round order, runs the same candidate-side harness,
verifies 63 SHA-256 downloads per snapshot and nine CPU/RAM rounds per snapshot, and
preserves only marker-owned test data. This is a regression comparison, not an IDM benchmark.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BaselineInstaller,
    [Parameter(Mandatory = $true)][string] $CandidateInstaller,
    [Parameter(Mandatory = $true)][string] $BaselineSourceSha,
    [Parameter(Mandatory = $true)][string] $CandidateSourceSha,
    [Parameter(Mandatory = $true)][string] $HarnessSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [ValidateRange(3, 3)][int] $Rounds = 3
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw 'Controlled-comparison output directory must be absent or empty.'
    }
} else {
    New-Item -ItemType Directory -Path $root -Force | Out-Null
}
$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([char[]]@('\','/'))
if ([string]::IsNullOrWhiteSpace($runnerTemp)) { throw 'RUNNER_TEMP is unavailable.' }
$installDir = Join-Path $runnerTemp 'hyperget-controlled-benchmark-install'
$dataDir = Join-Path $env:USERPROFILE '.personal-download-manager'
$markerPath = Join-Path $dataDir 'system/hyperget-controlled-benchmark-owned.txt'
$matrixScript = Join-Path $PSScriptRoot 'Run-WindowsActiveBenchmarkMatrix.ps1'
$summarizeScript = Join-Path $PSScriptRoot 'Summarize-WindowsActiveBenchmark.ps1'
$compareScript = Join-Path $PSScriptRoot 'Compare-WindowsActiveBenchmarks.ps1'
$uninstallKeys = @(
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DownloadManager',
    'HKCU:\Software\DownloadManager',
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run\DownloadManager'
)
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
$shortcuts = @(
    (Join-Path $startMenu 'Download Manager\Download Manager.lnk'),
    (Join-Path $startMenu 'HyperGet\HyperGet.lnk'),
    (Join-Path $env:USERPROFILE 'Desktop\Download Manager.lnk'),
    (Join-Path $env:USERPROFILE 'Desktop\HyperGet.lnk')
)
foreach ($required in @($BaselineInstaller, $CandidateInstaller, $matrixScript, $summarizeScript, $compareScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required comparison input missing: $required" }
}
foreach ($sha in @($BaselineSourceSha, $CandidateSourceSha, $HarnessSha)) {
    if ($sha -notmatch '^[0-9a-fA-F]{40}$') { throw 'All source and harness SHAs must be full 40-character commits.' }
}
if ($BaselineSourceSha -eq $CandidateSourceSha) { throw 'Baseline and candidate must be different exact source snapshots.' }
$script:InstallDirExistedAtPreflight = Test-Path -LiteralPath $installDir -PathType Container
if ($script:InstallDirExistedAtPreflight) { throw 'Refusing to overwrite a pre-existing controlled install directory.' }
if (Test-Path -LiteralPath $dataDir) { throw 'Refusing to touch pre-existing user data.' }
foreach ($key in $uninstallKeys) { if (Test-Path -LiteralPath $key) { throw "Refusing to touch pre-existing registry key: $key" } }
$script:ShortcutParentExistedAtPreflight = @{}
foreach ($shortcut in $shortcuts) {
    if (Test-Path -LiteralPath $shortcut) { throw "Refusing to touch pre-existing shortcut: $shortcut" }
    $parent = Split-Path -Parent $shortcut
    $script:ShortcutParentExistedAtPreflight[$parent] = Test-Path -LiteralPath $parent -PathType Container
}
$baselineInstallerSha = (Get-FileHash -LiteralPath $BaselineInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
$candidateInstallerSha = (Get-FileHash -LiteralPath $CandidateInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
@(
    [pscustomobject]@{ Variant='baseline'; SourceSha=$BaselineSourceSha; InstallerSHA256=$baselineInstallerSha }
    [pscustomobject]@{ Variant='candidate'; SourceSha=$CandidateSourceSha; InstallerSHA256=$candidateInstallerSha }
) | Export-Csv -LiteralPath (Join-Path $root 'installer-hashes.csv') -NoTypeInformation -Encoding utf8
$script:ActiveInstallDir = $null
$script:ActiveMarkerToken = $null
$script:ActiveDataDir = $dataDir
$script:RegistryResiduePath = Join-Path $root 'registry-residue.csv'
$script:ShortcutResiduePath = Join-Path $root 'shortcut-residue.csv'
$script:InstallFileResiduePath = Join-Path $root 'install-file-residue.csv'
$script:ExpectedGuiPath = $null
$script:ExpectedCliPath = $null
$transcriptStarted = $false
try {
    Start-Transcript -Path (Join-Path $root 'controlled-comparison-transcript.txt') -Force | Out-Null
    $transcriptStarted = $true
    Write-Host "Baseline source SHA: $BaselineSourceSha"
    Write-Host "Candidate source SHA: $CandidateSourceSha"
    Write-Host "Harness source SHA: $HarnessSha"
    Write-Host "Baseline installer SHA256: $baselineInstallerSha"
    Write-Host "Candidate installer SHA256: $candidateInstallerSha"
    Write-Host 'Predeclared gates: median throughput regression <= 15%; median peak Working Set and Private Bytes regression <= 15% per workload.'
    function Get-ProcessesUnderInstall {
        param([string] $Path)
        $prefix = [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\','/')) + '\'
        return @(Get-CimInstance -ClassName Win32_Process | Where-Object {
            -not [string]::IsNullOrWhiteSpace([string]$_.ExecutablePath) -and
            ([string]$_.ExecutablePath).StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
        })
    }
    function Stop-ProcessesUnderInstall {
        param([string] $Path)
        $processes = @(Get-ProcessesUnderInstall -Path $Path)
        foreach ($process in $processes) {
            Write-Host "Stopping controlled-install process $($process.Name)#$($process.ProcessId)."
            Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Milliseconds 750
        $remaining = @(Get-ProcessesUnderInstall -Path $Path)
        if ($remaining.Count -gt 0) {
            $details = ($remaining | ForEach-Object { "$($_.Name)#$($_.ProcessId)" }) -join ', '
            throw "Processes remain under controlled install after stop request: $details"
        }
    }
    function Remove-OwnedData {
        param([string] $ExpectedToken)
        if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
            throw 'Owned data marker is missing; refusing recursive data cleanup.'
        }
        $actualToken = (Get-Content -LiteralPath $markerPath -Raw).Trim()
        if ($actualToken -ne $ExpectedToken) { throw 'Owned data marker does not match; refusing recursive data cleanup.' }
        $actualDataPath = [IO.Path]::GetFullPath($dataDir).TrimEnd([char[]]@('\','/'))
        $expectedDataPath = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.personal-download-manager')).TrimEnd([char[]]@('\','/'))
        if (-not [string]::Equals($actualDataPath, $expectedDataPath, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Owned data path differs from the exact expected profile path.'
        }
        Remove-Item -LiteralPath $dataDir -Recurse -Force
        if (Test-Path -LiteralPath $dataDir) { throw 'Owned benchmark data cleanup did not complete.' }
        $script:ActiveMarkerToken = $null
    }
    function Normalize-RegistryValuePath {
        param([AllowNull()][string] $Value)
        if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
        $trimmed = $Value.Trim().Trim([char]'"')
        if ([string]::IsNullOrWhiteSpace($trimmed)) { return '' }
        try { return [IO.Path]::GetFullPath($trimmed).TrimEnd([char[]]@('\\','/')) }
        catch { return '' }
    }
    function Clear-OwnedRegistryResidue {
        param([ValidateSet('baseline','candidate')][string] $Variant, [int] $Round)
        $expectedInstall = Normalize-RegistryValuePath $installDir
        foreach ($key in $uninstallKeys) {
            if (-not (Test-Path -LiteralPath $key)) { continue }
            $properties = Get-ItemProperty -LiteralPath $key -ErrorAction Stop
            $owned = $false
            $evidence = ''
            if ($key -eq $uninstallKeys[0]) {
                $registeredInstall = Normalize-RegistryValuePath ([string]$properties.InstallLocation)
                $registeredUninstaller = Normalize-RegistryValuePath ([string]$properties.UninstallString)
                $expectedUninstaller = Normalize-RegistryValuePath (Join-Path $installDir 'uninstall.exe')
                $owned = $registeredInstall -eq $expectedInstall -and $registeredUninstaller -eq $expectedUninstaller
                $evidence = "InstallLocation=$registeredInstall; UninstallString=$registeredUninstaller"
            } elseif ($key -eq $uninstallKeys[1]) {
                $registeredInstall = Normalize-RegistryValuePath ([string]$properties.InstallPath)
                $owned = $registeredInstall -eq $expectedInstall
                $evidence = "InstallPath=$registeredInstall"
            } else {
                $runTarget = Normalize-RegistryValuePath ([string]$properties.DownloadManager)
                $expectedExe = Normalize-RegistryValuePath (Join-Path $installDir 'DownloadManager.exe')
                $nonMetadata = @($properties.PSObject.Properties | Where-Object { $_.Name -notmatch '^PS' })
                if (-not [string]::IsNullOrWhiteSpace($runTarget)) {
                    $owned = $runTarget -eq $expectedExe
                    $evidence = "DownloadManager=$runTarget"
                } elseif ($nonMetadata.Count -eq 0) {
                    # Preflight proved this known product key did not exist before this isolated install.
                    $owned = $true
                    $evidence = 'Empty known product key; absent during preflight'
                }
            }
            if (-not $owned) {
                throw "Refusing to remove registry key not proven to belong to the controlled install: $key"
            }
            $record = [pscustomobject]@{
                Variant = $Variant
                Round = $Round
                RegistryKey = $key
                Evidence = $evidence
                Action = 'Removed test-owned residue after recording'
            }
            if (Test-Path -LiteralPath $script:RegistryResiduePath -PathType Leaf) {
                $record | Export-Csv -LiteralPath $script:RegistryResiduePath -NoTypeInformation -Append -Encoding utf8
            } else {
                $record | Export-Csv -LiteralPath $script:RegistryResiduePath -NoTypeInformation -Encoding utf8
            }
            Remove-Item -LiteralPath $key -Recurse -Force
            if (Test-Path -LiteralPath $key) { throw "Failed to clear recorded test-owned registry residue: $key" }
            Write-Warning "$Variant round $Round uninstaller left a test-owned registry key; recorded and removed it to isolate performance rounds: $key"
        }
    }
    function Clear-OwnedShortcutResidue {
        param([ValidateSet('baseline','candidate')][string] $Variant, [int] $Round)
        if ([string]::IsNullOrWhiteSpace([string]$script:ExpectedGuiPath) -or
            [string]::IsNullOrWhiteSpace([string]$script:ExpectedCliPath)) {
            throw 'Expected installed executable paths are unavailable; refusing shortcut cleanup.'
        }
        $expectedGui = Normalize-RegistryValuePath $script:ExpectedGuiPath
        $expectedCli = Normalize-RegistryValuePath $script:ExpectedCliPath
        $shell = $null
        try {
            $shell = New-Object -ComObject WScript.Shell
            foreach ($shortcut in $shortcuts) {
                if (-not (Test-Path -LiteralPath $shortcut -PathType Leaf)) { continue }
                $link = $null
                try {
                    $link = $shell.CreateShortcut($shortcut)
                    $targetRaw = ([string]$link.TargetPath).Trim().Trim([char]'"')
                    $workingRaw = ([string]$link.WorkingDirectory).Trim().Trim([char]'"')
                    $target = Normalize-RegistryValuePath $targetRaw
                    $working = Normalize-RegistryValuePath $workingRaw
                    $owned = [IO.Path]::IsPathRooted($targetRaw) -and
                        ($target -eq $expectedGui -or $target -eq $expectedCli)
                    if (-not $owned) {
                        throw "Refusing to remove shortcut not proven to target the controlled install: $shortcut (TargetPath=$target; WorkingDirectory=$working)"
                    }
                    $record = [pscustomobject]@{
                        Variant = $Variant
                        Round = $Round
                        ShortcutPath = $shortcut
                        TargetPath = $target
                        WorkingDirectory = $working
                        Action = 'Removed test-owned shortcut after recording'
                    }
                    if (Test-Path -LiteralPath $script:ShortcutResiduePath -PathType Leaf) {
                        $record | Export-Csv -LiteralPath $script:ShortcutResiduePath -NoTypeInformation -Append -Encoding utf8
                    } else {
                        $record | Export-Csv -LiteralPath $script:ShortcutResiduePath -NoTypeInformation -Encoding utf8
                    }
                    Remove-Item -LiteralPath $shortcut -Force
                    if (Test-Path -LiteralPath $shortcut) { throw "Failed to clear recorded test-owned shortcut: $shortcut" }
                    $parent = Split-Path -Parent $shortcut
                    $parentWasPresent = $script:ShortcutParentExistedAtPreflight.ContainsKey($parent) -and
                        [bool]$script:ShortcutParentExistedAtPreflight[$parent]
                    if (-not $parentWasPresent -and (Test-Path -LiteralPath $parent -PathType Container)) {
                        $children = @(Get-ChildItem -LiteralPath $parent -Force)
                        if ($children.Count -eq 0) {
                            Remove-Item -LiteralPath $parent -Force
                            if (Test-Path -LiteralPath $parent) { throw "Failed to remove empty test-created shortcut folder: $parent" }
                        }
                    }
                    Write-Warning "$Variant round $Round uninstaller left a test-owned shortcut; recorded and removed it to isolate performance rounds: $shortcut"
                } finally {
                    if ($link) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($link) }
                }
            }
        } finally {
            if ($shell) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($shell) }
        }
    }
    function Clear-OwnedInstallDirectoryResidue {
        param([ValidateSet('baseline','candidate')][string] $Variant, [int] $Round)
        if (-not (Test-Path -LiteralPath $installDir -PathType Container)) { return }

        $actualInstallDir = [IO.Path]::GetFullPath($installDir).TrimEnd([char[]]@('\','/'))
        $expectedInstallDir = [IO.Path]::GetFullPath((Join-Path $runnerTemp 'hyperget-controlled-benchmark-install')).TrimEnd([char[]]@('\','/'))
        if (-not [string]::Equals($actualInstallDir, $expectedInstallDir, [StringComparison]::OrdinalIgnoreCase) -or
            -not $actualInstallDir.StartsWith($runnerTemp + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to clear an install directory outside the exact controlled runner-temp path.'
        }
        if ($script:InstallDirExistedAtPreflight) {
            throw 'Refusing to clear an install directory that existed at preflight.'
        }
        if ([string]::IsNullOrWhiteSpace([string]$script:ActiveMarkerToken) -or
            -not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
            throw 'Refusing to clear install residue without the active test-owned data marker.'
        }
        if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -ne $script:ActiveMarkerToken) {
            throw 'Active test-owned data marker does not match; refusing install cleanup.'
        }

        Stop-ProcessesUnderInstall -Path $actualInstallDir

        # NSIS can finish deleting its install directory asynchronously after the
        # uninstaller process exits. Re-check after process quiescence and tolerate
        # disappearance during enumeration only when the path is now truly absent.
        if (-not (Test-Path -LiteralPath $actualInstallDir -PathType Container)) {
            Write-Host "$Variant round $Round controlled install directory disappeared during process quiescence; no install-file residue remains."
            return
        }
        try {
            $files = @(Get-ChildItem -LiteralPath $actualInstallDir -File -Recurse -Force -ErrorAction Stop)
        } catch {
            if (-not (Test-Path -LiteralPath $actualInstallDir -PathType Container)) {
                Write-Host "$Variant round $Round controlled install directory disappeared during residue inventory; no install-file residue remains."
                return
            }
            throw
        }
        $records = [System.Collections.Generic.List[object]]::new()
        if ($files.Count -eq 0) {
            $records.Add([pscustomobject]@{
                Variant = $Variant
                Round = $Round
                InstallDirectory = $actualInstallDir
                RelativePath = '.'
                FullPath = $actualInstallDir
                SizeBytes = 0
                SHA256 = ''
                Action = 'Removed test-owned install directory after recording'
            })
        } else {
            foreach ($file in $files) {
                $relative = [IO.Path]::GetRelativePath($actualInstallDir, $file.FullName)
                $digest = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                $records.Add([pscustomobject]@{
                    Variant = $Variant
                    Round = $Round
                    InstallDirectory = $actualInstallDir
                    RelativePath = $relative
                    FullPath = $file.FullName
                    SizeBytes = [long]$file.Length
                    SHA256 = $digest
                    Action = 'Removed test-owned install directory after recording'
                })
            }
        }
        foreach ($record in $records) {
            if (Test-Path -LiteralPath $script:InstallFileResiduePath -PathType Leaf) {
                $record | Export-Csv -LiteralPath $script:InstallFileResiduePath -NoTypeInformation -Append -Encoding utf8
            } else {
                $record | Export-Csv -LiteralPath $script:InstallFileResiduePath -NoTypeInformation -Encoding utf8
            }
        }

        Remove-Item -LiteralPath $actualInstallDir -Recurse -Force
        if (Test-Path -LiteralPath $actualInstallDir) {
            throw 'Failed to clear the exact marker-owned controlled install directory after recording residue.'
        }
        $fileCount = $files.Count
        Write-Warning "$Variant round $Round uninstaller left $fileCount install file(s) (or an empty install directory); inventory recorded before test-owned cleanup. This is not uninstall acceptance PASS."
    }
    function Assert-NoProductResidue {
        param([ValidateSet('baseline','candidate')][string] $Variant, [int] $Round)
        Clear-OwnedRegistryResidue -Variant $Variant -Round $Round
        Clear-OwnedShortcutResidue -Variant $Variant -Round $Round
        Clear-OwnedInstallDirectoryResidue -Variant $Variant -Round $Round
        foreach ($key in $uninstallKeys) {
            if (Test-Path -LiteralPath $key) { throw "Registry residue cleanup did not complete: $key" }
        }
        foreach ($shortcut in $shortcuts) {
            if (Test-Path -LiteralPath $shortcut) { throw "Uninstall left product shortcut: $shortcut" }
        }
        foreach ($name in @('DownloadManager.exe','DownloadManagerCli.exe','uninstall.exe')) {
            if (Test-Path -LiteralPath (Join-Path $installDir $name)) { throw "Uninstall left $name in the install directory." }
        }
        if (Test-Path -LiteralPath $installDir) { throw 'Uninstall left the controlled install directory behind.' }
    }
    function Invoke-VariantRound {
        param([ValidateSet('baseline','candidate')][string] $Variant, [int] $Round)
        if (Test-Path -LiteralPath $installDir) { throw 'Install directory exists before a fresh variant install.' }
        if (Test-Path -LiteralPath $dataDir) { throw 'User-data directory exists before a fresh variant run.' }
        foreach ($key in $uninstallKeys) { if (Test-Path -LiteralPath $key) { throw "Registry residue before install: $key" } }
        foreach ($shortcut in $shortcuts) { if (Test-Path -LiteralPath $shortcut) { throw "Shortcut residue before install: $shortcut" } }
        $sourceSha = if ($Variant -eq 'baseline') { $BaselineSourceSha } else { $CandidateSourceSha }
        $installer = if ($Variant -eq 'baseline') { $BaselineInstaller } else { $CandidateInstaller }
        $install = Start-Process -FilePath $installer -ArgumentList @('/S', "/D=$installDir") -PassThru
        if (-not $install.WaitForExit(180000)) {
            Stop-Process -Id $install.Id -Force -ErrorAction SilentlyContinue
            throw "$Variant installer timed out."
        }
        if ($install.ExitCode -ne 0) { throw "$Variant installer exit code $($install.ExitCode)." }
        $script:ActiveInstallDir = $installDir
        $gui = @(Get-ChildItem -LiteralPath $installDir -File -Recurse -Filter 'DownloadManager.exe')
        $cli = @(Get-ChildItem -LiteralPath $installDir -File -Recurse -Filter 'DownloadManagerCli.exe')
        if ($gui.Count -ne 1 -or $cli.Count -ne 1) { throw "$Variant installed executable set is invalid: GUI=$($gui.Count), CLI=$($cli.Count)." }
        $script:ExpectedGuiPath = $gui[0].FullName
        $script:ExpectedCliPath = $cli[0].FullName
        New-Item -ItemType Directory -Path (Split-Path -Parent $markerPath) -Force | Out-Null
        $token = [guid]::NewGuid().ToString('D')
        Set-Content -LiteralPath $markerPath -Value $token -NoNewline -Encoding utf8
        $script:ActiveMarkerToken = $token
        $roundDir = Join-Path (Join-Path $root $Variant) "round-$Round"
        if (Test-Path -LiteralPath $roundDir) { throw "Round evidence directory already exists: $roundDir" }
        Write-Host "Starting $Variant round $Round with app SHA $sourceSha."
        & $matrixScript -GuiExecutable $gui[0].FullName -CliExecutable $cli[0].FullName -SourceSha $sourceSha -HarnessSha $HarnessSha -OutputDirectory $roundDir -Rounds 1 -SampleSeconds 8 -ServerChunkDelayMs 500 -FileSizeMiB 32 -CompletionTimeoutSeconds 300
        & $summarizeScript -Directory $roundDir -SourceSha $sourceSha -HarnessSha $HarnessSha -Rounds 1
        Stop-ProcessesUnderInstall -Path $installDir
        $uninstaller = Join-Path $installDir 'uninstall.exe'
        if (-not (Test-Path -LiteralPath $uninstaller -PathType Leaf)) { throw "$Variant uninstaller missing after benchmark." }
        $remove = Start-Process -FilePath $uninstaller -ArgumentList @('/S') -PassThru
        if (-not $remove.WaitForExit(180000)) {
            Stop-Process -Id $remove.Id -Force -ErrorAction SilentlyContinue
            throw "$Variant silent uninstall timed out."
        }
        if ($remove.ExitCode -ne 0) { throw "$Variant silent uninstall exit code $($remove.ExitCode)." }
        Assert-NoProductResidue -Variant $Variant -Round $Round
        Remove-OwnedData -ExpectedToken $token
        $script:ActiveInstallDir = $null
        $script:ExpectedGuiPath = $null
        $script:ExpectedCliPath = $null
        Write-Host "PASS: $Variant round $Round completed; install, registry, shortcuts and owned profile data cleaned."
    }
    for ($round = 1; $round -le $Rounds; $round++) {
        $order = if ($round -eq 2) { @('candidate','baseline') } else { @('baseline','candidate') }
        foreach ($variant in $order) { Invoke-VariantRound -Variant $variant -Round $round }
    }
    try {
        & $compareScript -BaselineDirectory (Join-Path $root 'baseline') -CandidateDirectory (Join-Path $root 'candidate') -BaselineSourceSha $BaselineSourceSha -CandidateSourceSha $CandidateSourceSha -HarnessSha $HarnessSha -OutputPath (Join-Path $root 'comparison-report.md') -FileSizeMiB 32 -MaxRegressionPercent 15
    } finally {
        if ((Test-Path -LiteralPath $script:RegistryResiduePath -PathType Leaf) -or
            (Test-Path -LiteralPath $script:ShortcutResiduePath -PathType Leaf) -or
            (Test-Path -LiteralPath $script:InstallFileResiduePath -PathType Leaf)) {
            $registryCount = if (Test-Path -LiteralPath $script:RegistryResiduePath -PathType Leaf) {
                @(Import-Csv -LiteralPath $script:RegistryResiduePath).Count
            } else { 0 }
            $shortcutCount = if (Test-Path -LiteralPath $script:ShortcutResiduePath -PathType Leaf) {
                @(Import-Csv -LiteralPath $script:ShortcutResiduePath).Count
            } else { 0 }
            $installFileCount = if (Test-Path -LiteralPath $script:InstallFileResiduePath -PathType Leaf) {
                @(Import-Csv -LiteralPath $script:InstallFileResiduePath).Count
            } else { 0 }
            $diagnostic = @(
                ''
                '## Separate installer-uninstall diagnostic'
                ''
                "- Registry key residue records: $registryCount."
                "- Shortcut residue records: $shortcutCount."
                "- Install-file residue records: $installFileCount."
                '- Registry cleanup was limited to known keys absent at preflight whose values proved ownership by the controlled install (or empty known product keys).'
                '- Shortcut cleanup was limited to preflight-absent known links whose absolute target exactly matched the installed GUI or CLI executable; only empty test-created parent folders were removed.'
                '- Install-directory cleanup was limited to the exact runner-temp path, absent at preflight, with the active marker verified and no running process under the install path; all residual files were SHA-256 inventoried before cleanup.'
                '- Any registry, shortcut, or install-file residue is a separate uninstall defect diagnostic; cleanup is not evidence that application uninstall acceptance passed.'
            )
            $reportPath = Join-Path $root 'comparison-report.md'
            Add-Content -LiteralPath $reportPath -Value $diagnostic -Encoding utf8
            Write-Warning "Installer-uninstall diagnostic: $registryCount registry, $shortcutCount shortcut, and $installFileCount install-file residue record(s). Details are in the corresponding CSVs; this is not an uninstall acceptance PASS."
        }
    }
    Write-Host 'Controlled baseline/candidate comparison finished.'
} finally {
    if ($script:ActiveInstallDir) {
        try {
            Stop-ProcessesUnderInstall -Path $script:ActiveInstallDir
            $uninstaller = Join-Path $script:ActiveInstallDir 'uninstall.exe'
            if (Test-Path -LiteralPath $uninstaller -PathType Leaf) {
                $cleanup = Start-Process -FilePath $uninstaller -ArgumentList @('/S') -PassThru
                if ($cleanup.WaitForExit(180000)) { Write-Host "Cleanup uninstaller exit code: $($cleanup.ExitCode)." }
            }
        } catch { Write-Warning "Best-effort owned install cleanup failed: $($_.Exception.Message)" }
        if (Test-Path -LiteralPath $script:ActiveInstallDir) {
            $normalizedInstall = [IO.Path]::GetFullPath($script:ActiveInstallDir).TrimEnd([char[]]@('\','/'))
            $expectedControlledInstall = [IO.Path]::GetFullPath((Join-Path $runnerTemp 'hyperget-controlled-benchmark-install')).TrimEnd([char[]]@('\','/'))
            if (-not [string]::Equals($normalizedInstall, $expectedControlledInstall, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'Refusing best-effort recursive cleanup outside the exact controlled install path.'
            }
            $remainingProcesses = @(Get-ProcessesUnderInstall -Path $normalizedInstall)
            if ($remainingProcesses.Count -eq 0) {
                Remove-Item -LiteralPath $normalizedInstall -Recurse -Force -ErrorAction SilentlyContinue
            } else {
                $details = ($remainingProcesses | ForEach-Object { "$($_.Name)#$($_.ProcessId)" }) -join ', '
                Write-Warning "Preserving test install directory because process still active: $details"
            }
        }
    }
    if ($script:ActiveMarkerToken -and (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        try { Remove-OwnedData -ExpectedToken $script:ActiveMarkerToken }
        catch { Write-Warning "Owned profile cleanup refused: $($_.Exception.Message)" }
    }
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
}
