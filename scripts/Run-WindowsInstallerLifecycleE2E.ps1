<#
.SYNOPSIS
Real NSIS silent reinstall and uninstall acceptance on isolated Windows CI.
.DESCRIPTION
Only touches the explicit installer test directory under RUNNER_TEMP.
Same-version reinstall is NOT a cross-version upgrade/migration test.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $InstallerExecutable,
    [Parameter(Mandatory = $true)][string] $InstallDirectory,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [Parameter(Mandatory = $true)][string] $ProofFile
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$installer = [IO.Path]::GetFullPath($InstallerExecutable)
$installDir = [IO.Path]::GetFullPath($InstallDirectory).TrimEnd([char[]]@('\','/'))
$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([char[]]@('\','/'))
if (-not $installDir.StartsWith($runnerTemp + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Safety gate: lifecycle test may only operate below RUNNER_TEMP.'
}
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) { throw 'Installer missing.' }
if (-not (Test-Path -LiteralPath $installDir -PathType Container)) { throw 'Previously installed app missing.' }
$main = Join-Path $installDir 'DownloadManager.exe'
$cli = Join-Path $installDir 'DownloadManagerCli.exe'
$uninstaller = Join-Path $installDir 'uninstall.exe'
foreach ($path in @($main, $cli, $uninstaller)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Expected installed app executable is missing: $path"
    }
}
$initialHash = (Get-FileHash -LiteralPath $main -Algorithm SHA256).Hash
if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'GUI must be stopped before silent reinstall and uninstall.'
}
# Reinstall the exact same signed/unsigned snapshot build. This checks NSIS
# replacement and launcher consistency; it is NOT a versioned data migration.
$reinstall = Start-Process -FilePath $installer -ArgumentList @('/S', "/D=$installDir") -PassThru
if (-not $reinstall.WaitForExit(180000)) {
    Stop-Process -Id $reinstall.Id -Force -ErrorAction SilentlyContinue
    throw 'NSIS reinstall timed out.'
}
if ($reinstall.ExitCode -ne 0) { throw "NSIS reinstall exit $($reinstall.ExitCode)" }
Start-Sleep -Seconds 2
if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'NSIS silent reinstall unexpectedly launched a GUI.'
}
foreach ($path in @($main, $cli, $uninstaller)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "NSIS reinstall lost launcher: $path"
    }
}
$reinstalledHash = (Get-FileHash -LiteralPath $main -Algorithm SHA256).Hash
if ($reinstalledHash -ne $initialHash) {
    throw 'Reinstall changed the tested desktop executable unexpectedly.'
}
$uninstall = Start-Process -FilePath $uninstaller -ArgumentList @('/S') -PassThru
if (-not $uninstall.WaitForExit(180000)) {
    Stop-Process -Id $uninstall.Id -Force -ErrorAction SilentlyContinue
    throw 'NSIS uninstall timed out.'
}
if ($uninstall.ExitCode -ne 0) { throw "NSIS uninstall exit $($uninstall.ExitCode)" }
Start-Sleep -Seconds 2
foreach ($path in @($main, $cli, $uninstaller)) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        throw "NSIS uninstall left an installed executable: $path"
    }
}
$registryUninstall = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DownloadManager'
if (Test-Path -LiteralPath $registryUninstall) {
    throw 'NSIS uninstall left the per-user Add/Remove Programs registry entry.'
}
$proofDir = Split-Path ([IO.Path]::GetFullPath($ProofFile)) -Parent
New-Item -ItemType Directory -Path $proofDir -Force | Out-Null
@(
    "SourceSha=$SourceSha"
    "InstallerSHA256=$((Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash)"
    "ReinstalledGuiSHA256=$reinstalledHash"
    "SilentAutoLaunch=false"
    "ReinstallSameVersion=PASS"
    "SilentUninstall=PASS"
    "UninstallRegistryRemoved=PASS"
    "CrossVersionUpgrade=NOT_TESTED"
) | Set-Content -LiteralPath $ProofFile -Encoding utf8
Write-Host 'NSIS same-version reinstall and silent uninstall completed on isolated Windows runner.'
