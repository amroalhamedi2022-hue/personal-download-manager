<#
.SYNOPSIS
Runs authenticated FTP pause/restart/resume acceptance through installed GUI/CLI.
.DESCRIPTION
Uses only the installed app and the loopback LocalFtpFixture.py. The password is
piped to CLI stdin, stored by the GUI in Windows Credential Manager, and never
placed in a process argument or evidence file. Success requires final SHA-256,
a positive REST resume offset, persistence across a real GUI restart, and safe
task/file removal.
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
$root = [IO.Path]::GetFullPath($OutputDirectory)
$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$cliPath = [IO.Path]::GetFullPath($CliExecutable)
$fixtureScript = Join-Path $PSScriptRoot 'LocalFtpFixture.py'
foreach ($required in @($guiPath, $cliPath, $fixtureScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required program or fixture missing: $required"
    }
}
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw "FTP evidence directory must be empty: $root"
    }
}
New-Item -ItemType Directory -Path $root -Force | Out-Null
$downloads = Join-Path $root 'downloads'
New-Item -ItemType Directory -Path $downloads | Out-Null
$source = Join-Path $root 'fixture [windows].bin'
$portFile = Join-Path $root 'ftp-port.txt'
$statsFile = Join-Path $root 'ftp-stats.json'
$outputName = 'authenticated pause resume [windows].bin'
$outputPath = Join-Path $downloads $outputName
$partialPath = "$outputPath.ftp-part"
$username = 'windows-ci-user'
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
$env:IDM_FTP_FIXTURE_PASSWORD = $password
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $source 32
if ($LASTEXITCODE -ne 0) { throw 'Could not create deterministic FTP source.' }
$expectedBytes = 32MB
$expectedSha = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$server = $null
$gui = $null
$taskId = $null

function Start-InstalledGui {
    $process = Start-Process -FilePath $guiPath -PassThru
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Milliseconds 500
        if ($process.HasExited) { throw "Installed GUI exited with code $($process.ExitCode)." }
        if (@(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue).Count -gt 0) {
            return $process
        }
    }
    throw 'Installed GUI did not become available.'
}

try {
    $serverArgs = @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $source + '"'),
        '--port-file', ('"' + $portFile + '"'), '--stats-file', ('"' + $statsFile + '"'),
        '--username', $username, '--password-env', 'IDM_FTP_FIXTURE_PASSWORD',
        '--chunk-delay-ms', '20'
    )
    $server = Start-Process -FilePath 'python' -ArgumentList $serverArgs -PassThru `
        -RedirectStandardOutput (Join-Path $root 'ftp-server-stdout.txt') `
        -RedirectStandardError (Join-Path $root 'ftp-server-stderr.txt')
    for ($attempt = 0; $attempt -lt 80 -and -not (Test-Path -LiteralPath $portFile); $attempt++) {
        if ($server.HasExited) { throw "FTP fixture exited with code $($server.ExitCode)." }
        Start-Sleep -Milliseconds 250
    }
    if (-not (Test-Path -LiteralPath $portFile)) { throw 'FTP fixture did not start.' }
    $port = [int](Get-Content -LiteralPath $portFile -Raw)
    $url = "ftp://127.0.0.1:$port/fixture [windows].bin"
    $gui = Start-InstalledGui

    $cliOutput = @($password | & $cliPath download add ftp `
        --link $url --folder $downloads --name $outputName `
        --username $username --password-stdin --start 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Installed FTP CLI add failed ($LASTEXITCODE): $($cliOutput -join ' ')"
    }
    $ids = @($cliOutput | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
    if ($ids.Count -ne 1) { throw "FTP CLI did not return exactly one task ID: $($cliOutput -join ' ')" }
    $taskId = [long]$ids[0]

    $observedPartial = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
            $length = (Get-Item -LiteralPath $partialPath).Length
            if ($length -gt 128KB -and $length -lt ($expectedBytes - 128KB)) {
                $observedPartial = $true
                break
            }
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $observedPartial) { throw 'Did not observe an in-flight staged FTP file.' }
    & $cliPath download pause $taskId
    if ($LASTEXITCODE -ne 0) { throw 'Installed FTP pause command failed.' }
    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        $stats = Get-Content -LiteralPath $statsFile -Raw | ConvertFrom-Json
        if ([int]$stats.active_retrievals -eq 0) { break }
        Start-Sleep -Milliseconds 250
    }
    if ([int]$stats.active_retrievals -ne 0) { throw 'FTP data connection remained active after Pause.' }
    $pausedBytes = (Get-Item -LiteralPath $partialPath).Length
    if ($pausedBytes -le 0 -or $pausedBytes -ge $expectedBytes -or (Test-Path -LiteralPath $outputPath)) {
        throw "FTP pause staging invariant failed: partial=$pausedBytes final=$(Test-Path -LiteralPath $outputPath)"
    }

    # A real GUI restart proves both task persistence and retrieval of the
    # opaque credential reference from Windows Credential Manager.
    Stop-Process -Id $gui.Id -Force
    $gui.WaitForExit(15000) | Out-Null
    $gui = Start-InstalledGui
    & $cliPath download resume $taskId
    if ($LASTEXITCODE -ne 0) { throw 'Installed FTP resume after GUI restart failed.' }

    $completed = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
            if ((Get-Item -LiteralPath $outputPath).Length -eq $expectedBytes) {
                try {
                    $actualSha = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
                    if ($actualSha -eq $expectedSha) { $completed = $true; break }
                } catch { }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $completed) { throw 'FTP resumed output did not complete with the expected SHA-256.' }
    if ((Test-Path -LiteralPath $partialPath) -or (Test-Path -LiteralPath "$partialPath.properties")) {
        throw 'FTP staging data remained after successful commit.'
    }
    # The default Mordant table truncates status columns on narrow Windows CI
    # consoles (e.g. "Fini…"). Use the production's stable, non-sensitive
    # machine-readable ID/status output rather than matching rendered columns.
    $statusOutput = @(& $cliPath download show --status-lines $taskId 2>&1)
    $statusExitCode = $LASTEXITCODE
    $statusRows = @($statusOutput | ForEach-Object { "$_".Trim() })
    # PowerShell can normalize native stdout tab stops to spaces. Validate the
    # machine-readable fields, not the exact whitespace representation.
    if ($statusExitCode -ne 0 -or $statusRows.Count -ne 1) {
        throw "Persisted FTP task returned an invalid number of status rows or nonzero exit: $($statusRows -join ' ')"
    }
    $statusMatch = [regex]::Match($statusRows[0], '^([0-9]+)\s+Finished
    $stats = Get-Content -LiteralPath $statsFile -Raw | ConvertFrom-Json
    $positiveOffsets = @($stats.rest_offsets | Where-Object { [long]$_ -gt 0 })
    if ([int]$stats.retrievals -lt 2 -or [int]$stats.completed_retrievals -ne 1 -or $positiveOffsets.Count -lt 1) {
        throw "FTP REST evidence is incomplete: $($stats | ConvertTo-Json -Compress)"
    }
    if ([long]($positiveOffsets | Measure-Object -Maximum).Maximum -ne $pausedBytes) {
        throw "FTP resumed at a different offset than the committed paused stage: paused=$pausedBytes offsets=$($positiveOffsets -join ',')"
    }
    $dataRoot = Join-Path $env:USERPROFILE '.personal-download-manager'
    if (Test-Path -LiteralPath $dataRoot -PathType Container) {
        foreach ($storedFile in @(Get-ChildItem -LiteralPath $dataRoot -File -Recurse -Filter '*.json')) {
            if ([IO.File]::ReadAllText($storedFile.FullName).Contains($password)) {
                throw "FTP password leaked into application JSON: $($storedFile.FullName)"
            }
        }
    }

    [pscustomobject]@{
        SourceSha = $SourceSha
        TaskId = $taskId
        Bytes = $expectedBytes
        SHA256 = $actualSha
        PausedBytes = $pausedBytes
        RestOffset = [long]($positiveOffsets | Measure-Object -Maximum).Maximum
        Retrievals = [int]$stats.retrievals
        CompletedRetrievals = [int]$stats.completed_retrievals
        CredentialMode = 'Windows Credential Manager reference; password supplied through stdin'
        Verified = $true
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'ftp-e2e-report.json') -Encoding utf8

    & $cliPath download remove --remove-file $taskId
    if ($LASTEXITCODE -ne 0) { throw 'FTP task removal failed.' }
    Start-Sleep -Seconds 1
    if ((Test-Path -LiteralPath $outputPath) -or (Test-Path -LiteralPath $partialPath)) {
        throw 'FTP task removal left downloaded or staged data behind.'
    }
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $vaultTargets = @(& cmdkey.exe /list 2>&1)
        if (($vaultTargets -join "`n") -notmatch 'PersonalDownloadManager/FTP/') { break }
        Start-Sleep -Milliseconds 250
    }
    if (($vaultTargets -join "`n") -match 'PersonalDownloadManager/FTP/') {
        throw 'Removing the final FTP task left its Windows Credential Manager entry behind.'
    }
    Write-Host "SUCCESS: installed authenticated FTP paused at $pausedBytes bytes, resumed with REST after GUI restart, and verified SHA256 $actualSha."
} finally {
    Remove-Item Env:IDM_FTP_FIXTURE_PASSWORD -ErrorAction SilentlyContinue
    if ($gui -and -not $gui.HasExited) {
        Stop-Process -Id $gui.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
)
    if (-not $statusMatch.Success -or [long]$statusMatch.Groups[1].Value -ne $taskId) {
        throw "Persisted FTP task did not report Finished for expected ID $taskId : $($statusRows -join ' ')"
    }
    $stats = Get-Content -LiteralPath $statsFile -Raw | ConvertFrom-Json
    $positiveOffsets = @($stats.rest_offsets | Where-Object { [long]$_ -gt 0 })
    if ([int]$stats.retrievals -lt 2 -or [int]$stats.completed_retrievals -ne 1 -or $positiveOffsets.Count -lt 1) {
        throw "FTP REST evidence is incomplete: $($stats | ConvertTo-Json -Compress)"
    }
    if ([long]($positiveOffsets | Measure-Object -Maximum).Maximum -ne $pausedBytes) {
        throw "FTP resumed at a different offset than the committed paused stage: paused=$pausedBytes offsets=$($positiveOffsets -join ',')"
    }
    $dataRoot = Join-Path $env:USERPROFILE '.personal-download-manager'
    if (Test-Path -LiteralPath $dataRoot -PathType Container) {
        foreach ($storedFile in @(Get-ChildItem -LiteralPath $dataRoot -File -Recurse -Filter '*.json')) {
            if ([IO.File]::ReadAllText($storedFile.FullName).Contains($password)) {
                throw "FTP password leaked into application JSON: $($storedFile.FullName)"
            }
        }
    }

    [pscustomobject]@{
        SourceSha = $SourceSha
        TaskId = $taskId
        Bytes = $expectedBytes
        SHA256 = $actualSha
        PausedBytes = $pausedBytes
        RestOffset = [long]($positiveOffsets | Measure-Object -Maximum).Maximum
        Retrievals = [int]$stats.retrievals
        CompletedRetrievals = [int]$stats.completed_retrievals
        CredentialMode = 'Windows Credential Manager reference; password supplied through stdin'
        Verified = $true
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'ftp-e2e-report.json') -Encoding utf8

    & $cliPath download remove --remove-file $taskId
    if ($LASTEXITCODE -ne 0) { throw 'FTP task removal failed.' }
    Start-Sleep -Seconds 1
    if ((Test-Path -LiteralPath $outputPath) -or (Test-Path -LiteralPath $partialPath)) {
        throw 'FTP task removal left downloaded or staged data behind.'
    }
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $vaultTargets = @(& cmdkey.exe /list 2>&1)
        if (($vaultTargets -join "`n") -notmatch 'PersonalDownloadManager/FTP/') { break }
        Start-Sleep -Milliseconds 250
    }
    if (($vaultTargets -join "`n") -match 'PersonalDownloadManager/FTP/') {
        throw 'Removing the final FTP task left its Windows Credential Manager entry behind.'
    }
    Write-Host "SUCCESS: installed authenticated FTP paused at $pausedBytes bytes, resumed with REST after GUI restart, and verified SHA256 $actualSha."
} finally {
    Remove-Item Env:IDM_FTP_FIXTURE_PASSWORD -ErrorAction SilentlyContinue
    if ($gui -and -not $gui.HasExited) {
        Stop-Process -Id $gui.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
