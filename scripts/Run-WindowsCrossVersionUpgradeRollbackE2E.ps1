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

$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([char[]]@('\\','/'))
if ([string]::IsNullOrWhiteSpace($runnerTemp)) { throw 'RUNNER_TEMP is unavailable.' }
$root = Join-Path $runnerTemp 'hyperget-cross-version-upgrade-rollback'
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
$uninstallKey = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\DownloadManager'
$appKey = 'HKCU:\\Software\\DownloadManager'
$startMenuRoot = Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs'
$legacyStartMenuShortcut = Join-Path $startMenuRoot 'Download Manager\\Download Manager.lnk'
$brandedStartMenuShortcut = Join-Path $startMenuRoot 'HyperGet\\HyperGet.lnk'
$legacyDesktopShortcut = Join-Path $env:USERPROFILE 'Desktop\\Download Manager.lnk'
$brandedDesktopShortcut = Join-Path $env:USERPROFILE 'Desktop\\HyperGet.lnk'
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

New-Item -ItemType Directory -Path $proofDir -Force | Out-Null
Start-Transcript -Path $transcriptFile -Force | Out-Null
$transcriptStarted = $true

function Assert-UnderRunnerTemp([string] $Path, [string] $Label) {
    $full = [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\\','/'))
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
    if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -ne 0) {
        throw ($Label + ' /S install unexpectedly auto-launched the GUI.')
    }
    foreach ($name in @('DownloadManager.exe','DownloadManagerCli.exe','uninstall.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $installDir $name) -PathType Leaf)) {
            throw ($Label + ' installation is missing ' + $name + '.')
        }
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
        GuiSha256 = (Get-FileHash -LiteralPath (Join-Path $installDir 'DownloadManager.exe') -Algorithm SHA256).Hash.ToLowerInvariant()
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
    Write-Host ("PASS: only '" + $Brand + "' Start Menu and Desktop shortcuts exist.")
}
function Start-AppAndWait {
    $guiPath = Join-Path $installDir 'DownloadManager.exe'
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
        if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -eq 0) { return }
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
function Read-DownloadStatus([long] $Id) {
    $output = Invoke-AppCli -Arguments @('download','show',([string]$Id),'--status-lines') -Purpose ('read download ' + $Id)
    $pattern = '^\s*' + [regex]::Escape([string]$Id) + '\t(?<status>[A-Za-z]+)\s*$'
    foreach ($line in $output) {
        if ([string]$line -match $pattern) { return $Matches.status }
    }
    return $null
}
function Wait-ForFinishedDownload([long] $Id, [string] $FilePath) {
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $deadline) {
        $status = Read-DownloadStatus -Id $Id
        if ($status -eq 'Error') { throw ('Download ' + $Id + ' entered Error status.') }
        if ($status -eq 'Finished' -and (Test-Path -LiteralPath $FilePath -PathType Leaf)) {
            $item = Get-Item -LiteralPath $FilePath
            if ($item.Length -eq $expectedDownloadSize) {
                $hash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($hash -eq $expectedDownloadSha) { return }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw ('Download ' + $Id + ' did not reach Finished with exact size and SHA-256 within 180 seconds.')
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
    for ($attempt = 0; $attempt -lt 80 -and -not (Test-Path -LiteralPath $portFile); $attempt++) {
        if ($server.HasExited) {
            $serverError = Get-Content -LiteralPath $serverErr -Raw -ErrorAction SilentlyContinue
            throw ('Local HTTP fixture server exited. ' + $serverError)
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not (Test-Path -LiteralPath $portFile)) { throw 'Local HTTP fixture server did not open a port.' }
    $port = [int](Get-Content -LiteralPath $portFile -Raw)
    $url = "http://127.0.0.1:$port/fixture.bin?task=upgrade-rollback"
    $addOutput = Invoke-AppCli -Arguments @('download','add','http','--link',$url,'--folder',$downloadDir,'--name',$downloadName,'--start') -Purpose 'create baseline HTTP download'
    $idLines = @(
        foreach ($line in $addOutput) {
            $value = ([string]$line).Trim()
            if ($value -match '^\d+$') { $value }
        }
    )
    if ($idLines.Count -ne 1) { throw ('Expected exactly one numeric download ID; CLI output: ' + ($addOutput -join ' | ')) }
    $downloadId = [long]$idLines[0]
    $downloadPath = Join-Path $downloadDir $downloadName
    Wait-ForFinishedDownload -Id $downloadId -FilePath $downloadPath
    Stop-AppGracefully
    $baselineMetadata = Get-InstallMetadata -Label 'Baseline after real transfer'

    $stage = 'upgrade baseline to HyperGet candidate'
    Install-Snapshot -InstallerPath ([IO.Path]::GetFullPath($CandidateInstaller)) -Label 'HyperGet candidate'
    $candidateMetadata = Get-InstallMetadata -Label 'HyperGet candidate'
    if ($candidateMetadata.DisplayName -ne 'HyperGet') { throw ("Candidate display name is '" + $candidateMetadata.DisplayName + "', expected HyperGet.") }
    if ($candidateMetadata.DisplayVersion -eq $baselineMetadata.DisplayVersion) {
        throw 'Baseline and candidate DisplayVersion are identical; the run cannot prove a versioned upgrade.'
    }
    Assert-ShortcutBrand -Brand 'HyperGet'
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) { throw 'Upgrade deleted user data.' }
    Start-AppAndWait
    Assert-UserState -Label 'Upgrade to HyperGet'
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
    foreach ($name in @('DownloadManager.exe','DownloadManagerCli.exe','uninstall.exe')) {
        if (Test-Path -LiteralPath (Join-Path $installDir $name)) { throw ('Candidate uninstall left ' + $name + ' behind.') }
    }
    foreach ($key in @($uninstallKey,$appKey)) {
        if (Test-Path -LiteralPath $key) { throw ('Candidate uninstall left registry key ' + $key + '.') }
    }
    foreach ($path in @($legacyStartMenuShortcut,$brandedStartMenuShortcut,$legacyDesktopShortcut,$brandedDesktopShortcut)) {
        if (Test-Path -LiteralPath $path) { throw ('Candidate uninstall left product shortcut ' + $path + '.') }
    }
    if (-not (Test-Path -LiteralPath $sentinelFile -PathType Leaf)) { throw 'Candidate uninstall removed user data before rollback.' }
    if ((Get-FileHash -LiteralPath $sentinelFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $script:SentinelSha) {
        throw 'Candidate uninstall changed the user-data sentinel before rollback.'
    }
    $stage = 'reinstall baseline snapshot after preserving-data rollback'
    Install-Snapshot -InstallerPath ([IO.Path]::GetFullPath($BaselineInstaller)) -Label 'Rollback to baseline'
    $rollbackMetadata = Get-InstallMetadata -Label 'Rollback to baseline'
    if ($rollbackMetadata.DisplayName -ne 'Download Manager' -or
        $rollbackMetadata.DisplayVersion -ne $baselineMetadata.DisplayVersion) {
        throw ('Rollback did not restore baseline metadata: name=' + $rollbackMetadata.DisplayName + ' version=' + $rollbackMetadata.DisplayVersion)
    }
    Assert-ShortcutBrand -Brand 'Download Manager'
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
    foreach ($name in @('DownloadManager.exe','DownloadManagerCli.exe','uninstall.exe')) {
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
        'NormalUninstallPreservesUserData=PASS'
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
        Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue |
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
    if ($testOwnedData -and (Test-Path -LiteralPath $ownershipMarker -PathType Leaf)) {
        $owner = (Get-Content -LiteralPath $ownershipMarker -Raw).Trim()
        $actualDataPath = [IO.Path]::GetFullPath($dataDir).TrimEnd([char[]]@('\\','/'))
        $expectedDataPath = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.personal-download-manager')).TrimEnd([char[]]@('\\','/'))
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
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
    if ($testCreatedRoot -and (Test-Path -LiteralPath $root)) {
        try { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop } catch {
            Write-Warning ("Could not remove test build/install directory: " + $_.Exception.Message)
        }
    }
}
