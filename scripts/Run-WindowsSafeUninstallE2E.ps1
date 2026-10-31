<#
Runs only on an ephemeral Windows GitHub Actions runner. Installs under RUNNER_TEMP,
checks /S never opens the GUI and verifies uninstall preserves an unrelated file
beside the application shortcut. No production user directory may be selected.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string] $InstallerExecutable,
    [Parameter(Mandatory=$true)][string] $ProofFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $env:GITHUB_ACTIONS -or -not $env:RUNNER_TEMP) {
    throw 'Refusing to run installer lifecycle outside an ephemeral GitHub Actions runner.'
}
$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([char[]]@('\','/'))
$installDir = Join-Path $runnerTemp "hyperget-uninstall-safety-$env:GITHUB_RUN_ID"
$installer = [IO.Path]::GetFullPath($InstallerExecutable)
$proof = [IO.Path]::GetFullPath($ProofFile)
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Download Manager'
$shortcut = Join-Path $startMenu 'Download Manager.lnk'
$sentinel = Join-Path $startMenu 'keep-unrelated-user-file.txt'
$registry = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DownloadManager'

if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    throw "Built NSIS installer is missing: $installer"
}
if (-not $installDir.StartsWith("$runnerTemp\", [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Install directory is not under RUNNER_TEMP.'
}
if ((Test-Path -LiteralPath $installDir) -or (Test-Path -LiteralPath $registry) -or
    (Test-Path -LiteralPath $startMenu)) {
    throw 'Runner already has installer-owned directories or registry: refusing destructive test.'
}
if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'DownloadManager is already running on runner.'
}

$installed = $false
$sentinelCreated = $false
try {
    $install = Start-Process -FilePath $installer -ArgumentList @('/S', "/D=$installDir") -PassThru
    if (-not $install.WaitForExit(180000)) {
        Stop-Process -Id $install.Id -Force -ErrorAction SilentlyContinue
        throw 'Silent install timed out.'
    }
    if ($install.ExitCode -ne 0) { throw "Silent install failed ($($install.ExitCode))." }
    $installed = $true
    Start-Sleep -Seconds 2
    if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -ne 0) {
        throw 'Silent /S install unexpectedly started the desktop GUI.'
    }
    $gui = Join-Path $installDir 'DownloadManager.exe'
    $uninstaller = Join-Path $installDir 'uninstall.exe'
    if (-not (Test-Path -LiteralPath $gui -PathType Leaf)) { throw 'Installed GUI executable missing.' }
    if (-not (Test-Path -LiteralPath $uninstaller -PathType Leaf)) { throw 'Installed uninstaller missing.' }
    if (-not (Test-Path -LiteralPath $shortcut -PathType Leaf)) {
        throw 'Start Menu shortcut missing; cannot test safe deletion.'
    }
    if (-not (Test-Path -LiteralPath $registry)) {
        throw 'Uninstall registration missing; cannot test cleanup.'
    }
    Set-Content -LiteralPath $sentinel -Value 'keep this unrelated file' -Encoding UTF8
    $sentinelCreated = $true

    $remove = Start-Process -FilePath $uninstaller -ArgumentList @('/S') -PassThru
    if (-not $remove.WaitForExit(180000)) {
        Stop-Process -Id $remove.Id -Force -ErrorAction SilentlyContinue
        throw 'Silent uninstall timed out.'
    }
    if ($remove.ExitCode -ne 0) { throw "Silent uninstall failed ($($remove.ExitCode))." }
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    do {
        $residue = (Test-Path -LiteralPath $gui) -or
                   (Test-Path -LiteralPath $shortcut) -or
                   (Test-Path -LiteralPath $registry)
        if (-not $residue) { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($residue) { throw 'Uninstaller left GUI, shortcut or registry registration.' }
    if (-not (Test-Path -LiteralPath $sentinel -PathType Leaf)) {
        throw 'Uninstall deleted an unrelated file in the Start Menu folder.'
    }
    $proofDir = Split-Path -Parent $proof
    New-Item -ItemType Directory -Path $proofDir -Force | Out-Null
    @(
        "SourceSha=$env:GITHUB_SHA"
        "InstallerSha256=$((Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash)"
        "SilentInstall=PASS"
        "NoUnexpectedGui=PASS"
        "SilentUninstall=PASS"
        "RemovedGuiShortcutAndRegistry=PASS"
        "UnrelatedStartMenuFilePreserved=PASS"
    ) | Set-Content -LiteralPath $proof -Encoding utf8
    Write-Host 'PASS: real Windows NSIS silent install, GUI-free setup, safe uninstall and preserved unrelated Start Menu file.'
} finally {
    # Delete only our own test sentinel. Never recursively clean the user's
    # shared Start Menu folder or alter an existing registered installation.
    if ($sentinelCreated -and (Test-Path -LiteralPath $sentinel)) {
        Remove-Item -LiteralPath $sentinel -Force
        if (Test-Path -LiteralPath $startMenu) {
            Remove-Item -LiteralPath $startMenu -ErrorAction SilentlyContinue
        }
    }
}
