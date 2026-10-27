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
if (Test-Path -LiteralPath $installDir) { throw 'Refusing to overwrite a pre-existing controlled install directory.' }
if (Test-Path -LiteralPath $dataDir) { throw 'Refusing to touch pre-existing user data.' }
foreach ($key in $uninstallKeys) { if (Test-Path -LiteralPath $key) { throw "Refusing to touch pre-existing registry key: $key" } }
foreach ($shortcut in $shortcuts) { if (Test-Path -LiteralPath $shortcut) { throw "Refusing to touch pre-existing shortcut: $shortcut" } }
$baselineInstallerSha = (Get-FileHash -LiteralPath $BaselineInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
$candidateInstallerSha = (Get-FileHash -LiteralPath $CandidateInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
@(
    [pscustomobject]@{ Variant='baseline'; SourceSha=$BaselineSourceSha; InstallerSHA256=$baselineInstallerSha }
    [pscustomobject]@{ Variant='candidate'; SourceSha=$CandidateSourceSha; InstallerSHA256=$candidateInstallerSha }
) | Export-Csv -LiteralPath (Join-Path $root 'installer-hashes.csv') -NoTypeInformation -Encoding utf8
$script:ActiveInstallDir = $null
$script:ActiveMarkerToken = $null
$script:ActiveDataDir = $dataDir
$transcriptStarted = $false
try {
    Start-Transcript -LiteralPath (Join-Path $root 'controlled-comparison-transcript.txt') -Force | Out-Null
    $transcriptStarted = $true
    Write-Host "Baseline source SHA: $BaselineSourceSha"
    Write-Host "Candidate source SHA: $CandidateSourceSha"
    Write-Host "Harness source SHA: $HarnessSha"
    Write-Host "Baseline installer SHA256: $baselineInstallerSha"
    Write-Host "Candidate installer SHA256: $candidateInstallerSha"
    Write-Host 'Predeclared gates: median throughput regression <= 15%; median peak Working Set and Private Bytes regression <= 15% per workload.'
    function Stop-ProcessesUnderInstall {
        param([string] $Path)
        $prefix = [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\','/')) + '\'
        $processes = @(Get-CimInstance -ClassName Win32_Process | Where-Object {
            -not [string]::IsNullOrWhiteSpace([string]$_.ExecutablePath) -and
            ([string]$_.ExecutablePath).StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
        })
        foreach ($process in $processes) {
            Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Milliseconds 750
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
    function Assert-NoProductResidue {
        foreach ($key in $uninstallKeys) {
            if (Test-Path -LiteralPath $key) { throw "Uninstall left registry key: $key" }
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
        Assert-NoProductResidue
        Remove-OwnedData -ExpectedToken $token
        $script:ActiveInstallDir = $null
        Write-Host "PASS: $Variant round $Round completed; install, registry, shortcuts and owned profile data cleaned."
    }
    for ($round = 1; $round -le $Rounds; $round++) {
        $order = if ($round -eq 2) { @('candidate','baseline') } else { @('baseline','candidate') }
        foreach ($variant in $order) { Invoke-VariantRound -Variant $variant -Round $round }
    }
    & $compareScript -BaselineDirectory (Join-Path $root 'baseline') -CandidateDirectory (Join-Path $root 'candidate') -BaselineSourceSha $BaselineSourceSha -CandidateSourceSha $CandidateSourceSha -HarnessSha $HarnessSha -OutputPath (Join-Path $root 'comparison-report.md') -FileSizeMiB 32 -MaxRegressionPercent 15
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
            if ($normalizedInstall.StartsWith($runnerTemp + '\', [StringComparison]::OrdinalIgnoreCase)) {
                Remove-Item -LiteralPath $script:ActiveInstallDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
    if ($script:ActiveMarkerToken -and (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        try { Remove-OwnedData -ExpectedToken $script:ActiveMarkerToken }
        catch { Write-Warning "Owned profile cleanup refused: $($_.Exception.Message)" }
    }
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
}
