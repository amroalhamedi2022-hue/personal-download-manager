<# 
.SYNOPSIS
Runs the Windows-only aria2 Stage 1 viability gate without bundling aria2.
.DESCRIPTION
Exercises HTTP redirect/status/ignored-Range handling, true interrupted HTTP
resume, authenticated FTP, and local-only authenticated JSON-RPC lifecycle.
The stock upstream binary is evaluation-only: if BitTorrent is enabled, it is
never copied into HyperGet. No secrets or credential-bearing URLs are written.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Aria2Executable,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [ValidateRange(8, 64)][int] $FileSizeMiB = 16,
    [ValidateRange(50, 500)][int] $ResumeChunkDelayMs = 150
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($SourceSha -notmatch '^[0-9a-fA-F]{40}$') { throw 'SourceSha must be a full 40-character commit.' }
$aria2 = [IO.Path]::GetFullPath($Aria2Executable)
if (-not (Test-Path -LiteralPath $aria2 -PathType Leaf)) { throw 'Pinned aria2c.exe is missing.' }
. (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')

$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw 'aria2 viability output directory must be absent or empty.'
    }
} else {
    New-Item -ItemType Directory -Force -Path $root | Out-Null
}
$source = Join-Path $root 'fixture.bin'
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $source $FileSizeMiB
if ($LASTEXITCODE -ne 0) { throw 'Unable to create deterministic aria2 fixture.' }
$expectedBytes = [long]$FileSizeMiB * 1MB
if ((Get-Item -LiteralPath $source).Length -ne $expectedBytes) { throw 'Fixture length mismatch.' }
$expectedHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$versionText = (& $aria2 --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'aria2 version 1\.37\.0') {
    throw 'The executable is not the pinned aria2 1.37.0 binary.'
}
$bitTorrentEnabled = $versionText -match '(?i)\bBitTorrent\b'
$sftpAdvertised = $versionText -match '(?i)\bSFTP\b'
Write-Host "ARIA2_VERSION=1.37.0"
Write-Host "ARIA2_BITTORRENT_ENABLED=$bitTorrentEnabled (evaluation only; never bundle this stock binary)"
Write-Host "ARIA2_SFTP_FEATURE_ADVERTISED=$sftpAdvertised (feature-list evidence only; no SFTP server E2E)"

$results = [System.Collections.Generic.List[object]]::new()
$ownedProcesses = [System.Collections.Generic.List[object]]::new()
$sensitiveValues = [System.Collections.Generic.List[string]]::new()
$script:SecretLeakDetected = $false
$ftpServer = $null
$httpServer = $null
$resumeProcess = $null
$rpcProcessOne = $null
$rpcProcessTwo = $null

function Add-Result {
    param([string] $Name, [string] $Result, [string] $Evidence)
    $results.Add([pscustomobject]@{
        Name = $Name
        Result = $Result
        Evidence = $Evidence
    }) | Out-Null
    Write-Host "$Name=$Result; $Evidence"
}

function ConvertTo-ProcessArgumentLine {
    param([string[]] $Values)
    $quoted = foreach ($value in $Values) {
        if ($value -match '[\s"]') {
            '"' + ($value.Replace('"', '\"')) + '"'
        } else {
            $value
        }
    }
    return ($quoted -join ' ')
}

function Start-OwnedProcess {
    param(
        [string] $FilePath,
        [string[]] $Arguments,
        [string] $Label
    )
    $stdout = Join-Path $root "$Label.stdout.log"
    $stderr = Join-Path $root "$Label.stderr.log"
    $argumentLine = ConvertTo-ProcessArgumentLine -Values $Arguments
    $process = Start-Process -FilePath $FilePath -ArgumentList $argumentLine -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $ownedProcesses.Add($process) | Out-Null
    return $process
}

function Invoke-Aria2 {
    param(
        [string] $Label,
        [string[]] $Arguments,
        [int] $TimeoutSeconds = 180,
        [switch] $AllowFailure
    )
    $process = Start-OwnedProcess -FilePath $aria2 -Arguments $Arguments -Label $Label
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "$Label timed out after $TimeoutSeconds seconds."
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "$Label exited with code $($process.ExitCode); inspect sanitized stdout/stderr evidence."
    }
    return $process
}

function Wait-PortFile {
    param([string] $PortFile, [object] $Process, [string] $Label)
    for ($i = 0; $i -lt 100; $i++) {
        if (Test-Path -LiteralPath $PortFile -PathType Leaf) {
            $value = (Get-Content -LiteralPath $PortFile -Raw).Trim()
            if ($value -match '^\d+$' -and [int]$value -ge 1 -and [int]$value -le 65535) {
                return [int]$value
            }
        }
        $Process.Refresh()
        if ($Process.HasExited) { throw "$Label exited before publishing a port." }
        Start-Sleep -Milliseconds 100
    }
    throw "$Label did not publish a valid port."
}

function Start-HttpFixture {
    param([string] $Label, [int] $DelayMs)
    $portFile = Join-Path $root "$Label.port"
    $server = Start-OwnedProcess -FilePath 'python' -Arguments @(
        (Join-Path $PSScriptRoot 'LocalHttpRangeFixture.py'),
        '--file', $source, '--port-file', $portFile,
        '--chunk-delay-ms', "$DelayMs", '--task-chunk-delay-ms', '0'
    ) -Label $Label
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portFile -Server $server -FixtureName $Label -Attempts 100 -ServerErrorPath (Join-Path $root "$Label.stderr.log")
    return [pscustomobject]@{ Process = $server; Port = $port; BaseUri = "http://127.0.0.1:$port" }
}

function Get-HttpStats {
    param([string] $BaseUri, [switch] $Reset)
    $uri = if ($Reset) { "$BaseUri/stats?reset=1" } else { "$BaseUri/stats" }
    return Invoke-RestMethod -Uri $uri -TimeoutSec 10
}

function Assert-ExactFile {
    param([string] $Path, [string] $Label)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Label output is missing." }
    $actualBytes = (Get-Item -LiteralPath $Path).Length
    $actualHash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualBytes -ne $expectedBytes -or $actualHash -ne $expectedHash) {
        throw "$Label output failed exact size/SHA-256 verification."
    }
    return $actualHash
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return [int]$listener.LocalEndpoint.Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-Rpc {
    param(
        [int] $Port,
        [string] $Method,
        [string[]] $Params
    )
    $body = @{
        jsonrpc = '2.0'
        id = 'hyperget-aria2-viability'
        method = $Method
        params = @($Params)
    } | ConvertTo-Json -Depth 8 -Compress
    try {
        # Keep the HTTP status even for 4xx responses. Invoke-RestMethod throws
        # for these by default, and collapsing an expected auth rejection to
        # $null would confuse "denied" with "RPC unreachable".
        $response = Invoke-WebRequest -Method Post -Uri "http://127.0.0.1:$Port/jsonrpc" -ContentType 'application/json' -Body $body -TimeoutSec 5 -SkipHttpErrorCheck
        $json = $null
        if (-not [string]::IsNullOrWhiteSpace([string]$response.Content)) {
            try {
                $json = $response.Content | ConvertFrom-Json -ErrorAction Stop
            } catch {
                $json = $null
            }
        }
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Json = $json
            TransportError = $null
            TransportMessage = $null
        }
    } catch {
        return [pscustomobject]@{
            StatusCode = 0
            Json = $null
            TransportError = $_.Exception.GetType().Name
            TransportMessage = $_.Exception.Message
        }
    }
}

function Get-ObjectPropertyValue {
    param([object] $Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Get-RpcJsonProperty {
    param([object] $Response, [string] $Name)
    if ($null -eq $Response) { return $null }
    return Get-ObjectPropertyValue -Object $Response.Json -Name $Name
}

function Test-RpcSucceeded {
    param([object] $Response)
    $result = Get-RpcJsonProperty -Response $Response -Name 'result'
    return ($null -ne $Response -and $Response.StatusCode -ge 200 -and $Response.StatusCode -lt 300 -and $null -ne $result)
}

function Test-RpcRejected {
    param([object] $Response)
    if ($null -eq $Response) { return $false }
    if ($Response.StatusCode -in @(400, 401, 403)) { return $true }
    $result = Get-RpcJsonProperty -Response $Response -Name 'result'
    $rpcError = Get-RpcJsonProperty -Response $Response -Name 'error'
    return ($Response.StatusCode -ge 200 -and $Response.StatusCode -lt 300 -and $null -ne $rpcError -and $null -eq $result)
}

function Get-RpcOutcome {
    param([object] $Response)
    if ($null -eq $Response) { return 'missing-response' }
    if ($Response.StatusCode -eq 0) { return "transport-$($Response.TransportError)" }
    $rpcError = Get-RpcJsonProperty -Response $Response -Name 'error'
    $result = Get-RpcJsonProperty -Response $Response -Name 'result'
    if ($null -ne $rpcError) { return "jsonrpc-error-http-$($Response.StatusCode)" }
    if ($null -ne $result) { return "result-http-$($Response.StatusCode)" }
    return "http-$($Response.StatusCode)-no-json-result"
}

function Get-RpcDiagnostic {
    param([object] $Response, [string] $Secret)
    if ($null -eq $Response) { return 'response=missing' }

    $errorObject = Get-RpcJsonProperty -Response $Response -Name 'error'
    $errorCode = Get-ObjectPropertyValue -Object $errorObject -Name 'code'
    $errorMessage = [string](Get-ObjectPropertyValue -Object $errorObject -Name 'message')
    if (-not [string]::IsNullOrEmpty($Secret) -and $errorMessage.Contains($Secret)) {
        $errorMessage = $errorMessage.Replace($Secret, '[REDACTED]')
    }
    if ([string]::IsNullOrWhiteSpace($errorMessage)) { $errorMessage = 'none' }
    $errorMessage = $errorMessage -replace '[\r\n]+', ' '

    $transportError = [string]$Response.TransportError
    if ([string]::IsNullOrWhiteSpace($transportError)) { $transportError = 'none' }
    $transportMessage = [string]$Response.TransportMessage
    if (-not [string]::IsNullOrEmpty($Secret) -and $transportMessage.Contains($Secret)) {
        $transportMessage = $transportMessage.Replace($Secret, '[REDACTED]')
    }
    if ([string]::IsNullOrWhiteSpace($transportMessage)) { $transportMessage = 'none' }
    $transportMessage = $transportMessage -replace '[\r\n]+', ' '

    $result = Get-RpcJsonProperty -Response $Response -Name 'result'
    $version = Get-ObjectPropertyValue -Object $result -Name 'version'
    return "http=$($Response.StatusCode); transport=$transportError; transportMessage=$transportMessage; rpcErrorCode=$errorCode; rpcErrorMessage=$errorMessage; resultPresent=$($null -ne $result); versionPresent=$([bool]$version)"
}

function Start-RpcServer {
    param([int] $Port, [string] $Secret, [string] $Label)
    $rpcDir = Join-Path $root $Label
    New-Item -ItemType Directory -Force -Path $rpcDir | Out-Null
    return Start-OwnedProcess -FilePath $aria2 -Label $Label -Arguments @(
        '--no-conf=true', '--enable-rpc=true', '--rpc-listen-all=false',
        "--rpc-listen-port=$Port", "--rpc-secret=$Secret",
        '--rpc-allow-origin-all=false', '--summary-interval=0',
        '--console-log-level=notice', '--file-allocation=none',
        "--dir=$rpcDir"
    )
}

function Wait-RpcReady {
    param([int] $Port, [string] $Secret, [object] $Process, [string] $Label)
    $lastDiagnostic = 'no RPC probe completed'
    for ($i = 0; $i -lt 80; $i++) {
        $Process.Refresh()
        if ($Process.HasExited) { throw "$Label exited before RPC became ready." }
        $response = Invoke-Rpc -Port $Port -Method 'aria2.getVersion' -Params @("token:$Secret")
        $lastDiagnostic = Get-RpcDiagnostic -Response $response -Secret $Secret
        $version = Get-ObjectPropertyValue -Object (Get-RpcJsonProperty -Response $response -Name 'result') -Name 'version'
        if ((Test-RpcSucceeded -Response $response) -and $version) {
            Write-Host "$Label RPC readiness PASS: $lastDiagnostic"
            return
        }
        Start-Sleep -Milliseconds 125
    }
    throw "$Label did not become ready on its loopback RPC port. Last probe: $lastDiagnostic"
}

function Stop-RpcServer {
    param([int] $Port, [string] $Secret, [object] $Process, [string] $Label)
    $Process.Refresh()
    if ($Process.HasExited) { throw "$Label exited before authenticated shutdown." }
    $response = Invoke-Rpc -Port $Port -Method 'aria2.shutdown' -Params @("token:$Secret")
    if (($response -and $response.StatusCode -ge 400) -or ($null -ne (Get-RpcJsonProperty -Response $response -Name 'error'))) {
        throw "$Label rejected authenticated shutdown."
    }
    # aria2 may close the HTTP connection while acknowledging shutdown; the
    # process-exit and listener-closure gates below remain mandatory.
    if (-not $Process.WaitForExit(10000)) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        throw "$Label did not stop after aria2.shutdown."
    }
    Start-Sleep -Milliseconds 250
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) { throw "$Label left an RPC listener after shutdown." }
}

try {
    # HTTP redirect and deterministic server error responses.
    $http = Start-HttpFixture -Label 'http-faults' -DelayMs 0
    $httpServer = $http.Process
    $baseUri = $http.BaseUri
    $redirectDir = Join-Path $root 'redirect'
    New-Item -ItemType Directory -Force -Path $redirectDir | Out-Null
    $redirectUrl = "$baseUri/redirect?task=aria2-redirect"
    $common = @(
        '--no-conf=true', '--console-log-level=notice', '--summary-interval=0',
        '--file-allocation=none', '--auto-file-renaming=false',
        '--max-tries=1', '--retry-wait=0', '--connect-timeout=5', '--timeout=15',
        '--enable-rpc=false'
    )
    $null = Invoke-Aria2 -Label 'http-redirect' -Arguments ($common + @(
        "--dir=$redirectDir", '--out=redirect.bin', $redirectUrl
    ))
    $redirectHash = Assert-ExactFile -Path (Join-Path $redirectDir 'redirect.bin') -Label 'HTTP redirect'
    Add-Result -Name 'HTTPRedirect' -Result 'PASS' -Evidence "bytes=$expectedBytes; SHA256=$redirectHash"

    foreach ($status in @(404, 429, 500)) {
        $dir = Join-Path $root "http-status-$status"
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $failed = Invoke-Aria2 -Label "http-status-$status" -AllowFailure -Arguments ($common + @(
            "--dir=$dir", "--out=status-$status.bin", "$baseUri/status/$($status)?task=status-$status"
        ))
        if ($failed.ExitCode -eq 0) { throw "HTTP $status unexpectedly returned success." }
        $outputPath = Join-Path $dir "status-$status.bin"
        if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
            $length = (Get-Item -LiteralPath $outputPath).Length
            if ($length -eq $expectedBytes) {
                $hash = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($hash -ne $expectedHash) { throw "HTTP $status left a full-length corrupt output." }
            }
        }
        Add-Result -Name "HTTP$status" -Result 'PASS' -Evidence 'nonzero aria2 exit; no full-length corrupt output accepted'
    }

    # An endpoint that deliberately ignores Range must either be recovered to
    # exact bytes or rejected without a full-length corrupt destination.
    $ignoreDir = Join-Path $root 'ignore-range'
    New-Item -ItemType Directory -Force -Path $ignoreDir | Out-Null
    $ignored = Invoke-Aria2 -Label 'http-ignore-range' -AllowFailure -Arguments ($common + @(
        '--split=4', '--max-connection-per-server=4', '--min-split-size=1M',
        "--dir=$ignoreDir", '--out=ignore-range.bin',
        "$baseUri/ignore-range?task=aria2-ignore-range"
    ))
    $ignoredPath = Join-Path $ignoreDir 'ignore-range.bin'
    if ($ignored.ExitCode -eq 0) {
        $ignoredHash = Assert-ExactFile -Path $ignoredPath -Label 'ignored-Range recovery'
        Add-Result -Name 'IgnoredRange' -Result 'PASS' -Evidence "safe exact-output fallback; SHA256=$ignoredHash"
    } else {
        if (Test-Path -LiteralPath $ignoredPath -PathType Leaf) {
            $length = (Get-Item -LiteralPath $ignoredPath).Length
            if ($length -eq $expectedBytes) {
                $hash = (Get-FileHash -LiteralPath $ignoredPath -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($hash -ne $expectedHash) { throw 'Ignored-Range failure left a full-length corrupt output.' }
            }
        }
        Add-Result -Name 'IgnoredRange' -Result 'PASS' -Evidence 'aria2 safely rejected ignored Range; no full-length corrupt output'
    }

    # True process interruption and byte-range resume. Reset fixture telemetry
    # after the first attempt so the observed nonzero offset belongs only to
    # the restarted aria2 process.
    if ($httpServer -and -not $httpServer.HasExited) {
        Stop-Process -Id $httpServer.Id -Force -ErrorAction SilentlyContinue
    }
    $slowHttp = Start-HttpFixture -Label 'http-resume' -DelayMs $ResumeChunkDelayMs
    $httpServer = $slowHttp.Process
    $resumeDir = Join-Path $root 'http-resume-output'
    New-Item -ItemType Directory -Force -Path $resumeDir | Out-Null
    $resumeTask = 'aria2-resume'
    $resumeUrl = "$($slowHttp.BaseUri)/fixture.bin?task=$resumeTask"
    $controlPath = Join-Path $resumeDir 'resume.bin.aria2'
    $resumeArgs = @(
        '--no-conf=true', '--console-log-level=notice', '--summary-interval=0',
        '--file-allocation=none', '--auto-file-renaming=false',
        '--continue=true', '--always-resume=true', '--auto-save-interval=1',
        '--split=1', '--max-connection-per-server=1',
        '--max-tries=3', '--connect-timeout=5', '--timeout=30', '--enable-rpc=false',
        "--dir=$resumeDir", '--out=resume.bin', $resumeUrl
    )
    $resumeProcess = Start-OwnedProcess -FilePath $aria2 -Arguments $resumeArgs -Label 'http-resume-first'
    $partialPath = Join-Path $resumeDir 'resume.bin'
    $partialDeadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $partialDeadline) {
        $resumeProcess.Refresh()
        if ($resumeProcess.HasExited) { throw 'aria2 completed or failed before interruption could be tested.' }
        if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
            $partial = (Get-Item -LiteralPath $partialPath).Length
            if ($partial -ge 2MB -and $partial -lt $expectedBytes -and (Test-Path -LiteralPath $controlPath -PathType Leaf)) { break }
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not (Test-Path -LiteralPath $partialPath -PathType Leaf)) { throw 'aria2 never created a partial resume file.' }
    if (-not (Test-Path -LiteralPath $controlPath -PathType Leaf)) { throw 'aria2 did not create a resume control file before interruption.' }
    $controlBefore = (Get-Item -LiteralPath $controlPath).LastWriteTimeUtc
    Start-Sleep -Milliseconds 1600
    if (-not (Test-Path -LiteralPath $controlPath -PathType Leaf)) { throw 'aria2 removed its resume control file before interruption.' }
    $controlAfter = (Get-Item -LiteralPath $controlPath).LastWriteTimeUtc
    if ($controlAfter -le $controlBefore) { throw 'aria2 did not refresh its resume control checkpoint before interruption.' }
    $controlFileBytes = (Get-Item -LiteralPath $controlPath).Length
    $partialBytes = (Get-Item -LiteralPath $partialPath).Length
    if ($partialBytes -lt 2MB -or $partialBytes -ge $expectedBytes) { throw "Resume interruption did not catch a checkpointed partial file ($partialBytes bytes)." }
    Stop-Process -Id $resumeProcess.Id -Force -ErrorAction SilentlyContinue
    $resumeProcess.WaitForExit(10000) | Out-Null
    $resumeProcess = $null
    $idle = $false
    for ($i = 0; $i -lt 100; $i++) {
        $snapshot = Get-HttpStats -BaseUri $slowHttp.BaseUri
        if ([int]$snapshot.active_requests -eq 0) { $idle = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $idle) { throw 'HTTP fixture did not observe socket closure after aria2 process interruption.' }
    if (-not (Test-Path -LiteralPath $controlPath -PathType Leaf)) {
        throw 'aria2 did not persist its resume control file after interruption.'
    }
    $firstResumeStats = Get-HttpStats -BaseUri $slowHttp.BaseUri
    $firstResumeStats | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $root 'http-resume-first-stats.json') -Encoding utf8
    $null = Get-HttpStats -BaseUri $slowHttp.BaseUri -Reset
    $resumeProcess = Start-OwnedProcess -FilePath $aria2 -Arguments $resumeArgs -Label 'http-resume-second'
    if (-not $resumeProcess.WaitForExit(240000)) {
        Stop-Process -Id $resumeProcess.Id -Force -ErrorAction SilentlyContinue
        throw 'aria2 resumed HTTP transfer timed out.'
    }
    $resumeProcess.Refresh()
    if ($resumeProcess.ExitCode -ne 0) { throw 'aria2 resume process returned nonzero exit.' }
    $resumeHash = Assert-ExactFile -Path $partialPath -Label 'HTTP restart/resume'
    $resumeStats = Get-HttpStats -BaseUri $slowHttp.BaseUri
    $resumeStats | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $root 'http-resume-restart-stats.json') -Encoding utf8
    $rangeProperty = $resumeStats.task_ranges.PSObject.Properties | Where-Object { $_.Name -eq $resumeTask } | Select-Object -First 1
    $requestedProperty = $resumeStats.task_requested_bytes.PSObject.Properties | Where-Object { $_.Name -eq $resumeTask } | Select-Object -First 1
    $servedProperty = $resumeStats.task_bytes_sent.PSObject.Properties | Where-Object { $_.Name -eq $resumeTask } | Select-Object -First 1
    if ($null -eq $rangeProperty -or $null -eq $requestedProperty -or $null -eq $servedProperty) { throw 'Resume range/byte telemetry is missing.' }
    $ranges = @($rangeProperty.Value)
    $resumedOffsets = @($ranges | Where-Object { [long]$_.start -gt 0 -and [bool]$_.range -and [bool]$_.honored })
    $requestedAfterRestart = [long]$requestedProperty.Value
    $servedAfterRestart = [long]$servedProperty.Value
    $rangeStarts = @($ranges | ForEach-Object { [string]$_.start }) -join ','
    $rangeDetails = @($ranges | ForEach-Object { '{0}-{1};range={2};honored={3}' -f $_.start, $_.end, $_.range, $_.honored }) -join '|'
    if ($resumedOffsets.Count -lt 1 -or $servedAfterRestart -ge $expectedBytes) {
        throw "Resume did not prove a nonzero honored byte offset and reduced actual transfer (interruptedBytes=$partialBytes; controlBytes=$controlFileBytes; requestedBytes=$requestedAfterRestart; servedBytes=$servedAfterRestart; rangeStarts=$rangeStarts; ranges=$rangeDetails)."
    }
    Add-Result -Name 'HTTPResumeRestart' -Result 'PASS' -Evidence "interruptedBytes=$partialBytes; controlBytes=$controlFileBytes; resumedRequestedBytes=$requestedAfterRestart; actualBytesServedAfterRestart=$servedAfterRestart; rangeStarts=$rangeStarts; SHA256=$resumeHash"

    # Authenticated FTP using the existing standard-library loopback fixture.
    $ftpDir = Join-Path $root 'ftp-output'
    New-Item -ItemType Directory -Force -Path $ftpDir | Out-Null
    $ftpPortFile = Join-Path $root 'ftp.port'
    $ftpStatsFile = Join-Path $root 'ftp-stats.json'
    $ftpPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $sensitiveValues.Add($ftpPassword) | Out-Null
    $previousFtpPassword = [Environment]::GetEnvironmentVariable('HYPERGET_ARIA2_FTP_PASSWORD', 'Process')
    $env:HYPERGET_ARIA2_FTP_PASSWORD = $ftpPassword
    try {
        $ftpServer = Start-OwnedProcess -FilePath 'python' -Label 'ftp-fixture' -Arguments @(
            (Join-Path $PSScriptRoot 'LocalFtpFixture.py'),
            '--file', $source, '--port-file', $ftpPortFile, '--stats-file', $ftpStatsFile,
            '--username', 'hypergettest', '--password-env', 'HYPERGET_ARIA2_FTP_PASSWORD',
            '--chunk-delay-ms', '0'
        )
    } finally {
        if ($null -eq $previousFtpPassword) {
            Remove-Item Env:HYPERGET_ARIA2_FTP_PASSWORD -ErrorAction SilentlyContinue
        } else {
            $env:HYPERGET_ARIA2_FTP_PASSWORD = $previousFtpPassword
        }
    }
    $ftpPort = Wait-PortFile -PortFile $ftpPortFile -Process $ftpServer -Label 'FTP fixture'
    $ftpStatsDeadline = [DateTime]::UtcNow.AddSeconds(5)
    while (-not (Test-Path -LiteralPath $ftpStatsFile -PathType Leaf) -and [DateTime]::UtcNow -lt $ftpStatsDeadline) {
        Start-Sleep -Milliseconds 100
    }
    if (-not (Test-Path -LiteralPath $ftpStatsFile -PathType Leaf)) { throw 'FTP fixture stats did not initialize.' }
    $ftpUrl = "ftp://127.0.0.1:$ftpPort/fixture.bin"
    $ftpProcess = Invoke-Aria2 -Label 'ftp-authenticated' -TimeoutSeconds 120 -Arguments ($common + @(
        '--ftp-type=binary', '--ftp-pasv=true', '--ftp-user=hypergettest', "--ftp-passwd=$ftpPassword",
        "--dir=$ftpDir", '--out=ftp.bin', $ftpUrl
    ))
    $ftpHash = Assert-ExactFile -Path (Join-Path $ftpDir 'ftp.bin') -Label 'authenticated FTP'
    $ftpStats = Get-Content -LiteralPath $ftpStatsFile -Raw | ConvertFrom-Json
    if ([int]$ftpStats.login_successes -lt 1 -or [int]$ftpStats.completed_retrievals -lt 1) {
        throw 'FTP fixture did not record a successful authenticated RETR.'
    }
    Add-Result -Name 'FTPAuthenticatedTransfer' -Result 'PASS' -Evidence "bytes=$expectedBytes; SHA256=$ftpHash; successfulLoginAndRETR=true"

    # Local-only JSON-RPC authentication, binding, malformed input and restart.
    $rpcPort = Get-FreeLoopbackPort
    $rpcSecretOne = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $rpcSecretTwo = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $sensitiveValues.Add($rpcSecretOne) | Out-Null
    $sensitiveValues.Add($rpcSecretTwo) | Out-Null
    $rpcProcessOne = Start-RpcServer -Port $rpcPort -Secret $rpcSecretOne -Label 'rpc-session-one'
    Wait-RpcReady -Port $rpcPort -Secret $rpcSecretOne -Process $rpcProcessOne -Label 'RPC session one'
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $rpcPort -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0 -or @($listeners | Where-Object { $_.LocalAddress -notin @('127.0.0.1', '::1') }).Count -gt 0) {
        throw 'aria2 RPC is not restricted to loopback-only listener addresses.'
    }
    $noToken = Invoke-Rpc -Port $rpcPort -Method 'aria2.getVersion' -Params @()
    $wrongToken = Invoke-Rpc -Port $rpcPort -Method 'aria2.getVersion' -Params @('token:invalid')
    $noTokenRejected = Test-RpcRejected -Response $noToken
    $wrongTokenRejected = Test-RpcRejected -Response $wrongToken
    $noTokenResult = Get-RpcJsonProperty -Response $noToken -Name 'result'
    $wrongTokenResult = Get-RpcJsonProperty -Response $wrongToken -Name 'result'
    if (-not $noTokenRejected -or -not $wrongTokenRejected -or
        $null -ne $noTokenResult -or $null -ne $wrongTokenResult) {
        throw "aria2 RPC auth rejection failed (missing-token=$(Get-RpcOutcome -Response $noToken); wrong-token=$(Get-RpcOutcome -Response $wrongToken))."
    }
    $malformed = $null
    try {
        $malformed = Invoke-WebRequest -Method Post -Uri "http://127.0.0.1:$rpcPort/jsonrpc" -ContentType 'application/json' -Body '{"jsonrpc":' -TimeoutSec 5 -SkipHttpErrorCheck
    } catch {
        # A rejected HTTP status is acceptable; a dead RPC process is not.
    }
    $rpcProcessOne.Refresh()
    if ($rpcProcessOne.HasExited) { throw 'Malformed JSON terminated the aria2 RPC process.' }
    $validAfterMalformed = Invoke-Rpc -Port $rpcPort -Method 'aria2.getVersion' -Params @("token:$rpcSecretOne")
    $validAfterMalformedVersion = Get-ObjectPropertyValue -Object (Get-RpcJsonProperty -Response $validAfterMalformed -Name 'result') -Name 'version'
    if (-not (Test-RpcSucceeded -Response $validAfterMalformed) -or -not $validAfterMalformedVersion) {
        throw "RPC did not recover after malformed JSON input ($(Get-RpcOutcome -Response $validAfterMalformed))."
    }
    Add-Result -Name 'RPCLoopbackAuthentication' -Result 'PASS' -Evidence 'missing/wrong token rejected; valid token accepted; wildcard bind absent'
    Add-Result -Name 'RPCMalformedInputRecovery' -Result 'PASS' -Evidence 'process stayed alive and valid JSON-RPC succeeded after malformed input'

    Stop-RpcServer -Port $rpcPort -Secret $rpcSecretOne -Process $rpcProcessOne -Label 'RPC session one'
    $rpcProcessOne = $null
    $rpcProcessTwo = Start-RpcServer -Port $rpcPort -Secret $rpcSecretTwo -Label 'rpc-session-two'
    Wait-RpcReady -Port $rpcPort -Secret $rpcSecretTwo -Process $rpcProcessTwo -Label 'RPC session two'
    $staleToken = Invoke-Rpc -Port $rpcPort -Method 'aria2.getVersion' -Params @("token:$rpcSecretOne")
    $freshToken = Invoke-Rpc -Port $rpcPort -Method 'aria2.getVersion' -Params @("token:$rpcSecretTwo")
    $staleTokenRejected = Test-RpcRejected -Response $staleToken
    $staleTokenResult = Get-RpcJsonProperty -Response $staleToken -Name 'result'
    $freshTokenVersion = Get-ObjectPropertyValue -Object (Get-RpcJsonProperty -Response $freshToken -Name 'result') -Name 'version'
    if (-not $staleTokenRejected -or $null -ne $staleTokenResult -or
        -not (Test-RpcSucceeded -Response $freshToken) -or -not $freshTokenVersion) {
        throw "RPC restart session check failed (stale-token=$(Get-RpcOutcome -Response $staleToken); fresh-token=$(Get-RpcOutcome -Response $freshToken))."
    }
    Stop-RpcServer -Port $rpcPort -Secret $rpcSecretTwo -Process $rpcProcessTwo -Label 'RPC session two'
    $rpcProcessTwo = $null
    Add-Result -Name 'RPCShutdownAndStaleSession' -Result 'PASS' -Evidence 'listener closed on shutdown; old secret rejected after restart'

    # Keep the evaluation report explicit about what is and is not proven.
    $results | Export-Csv -LiteralPath (Join-Path $root 'aria2-viability-results.csv') -NoTypeInformation -Encoding utf8
    $report = [System.Collections.Generic.List[string]]::new()
    $report.Add('# aria2 Stage 1 viability — Windows')
    $report.Add('')
    $report.Add("- HyperGet source SHA: $SourceSha")
    $report.Add('- aria2: official pinned 1.37.0 Windows x64 archive (archive hash is checked by the workflow before this script runs)')
    $report.Add("- Fixture bytes: $expectedBytes; expected SHA-256: $expectedHash")
    $report.Add("- BitTorrent feature in stock binary: $bitTorrentEnabled — evaluation only; this binary must not be bundled")
    $report.Add("- SFTP advertised by build feature list: $sftpAdvertised — no SFTP server E2E was run")
    $report.Add('')
    foreach ($item in $results) { $report.Add("- $($item.Name): $($item.Result) — $($item.Evidence)") }
    $report.Add('')
    $report.Add('Decision: Stage 1 viability evidence only; no production engine switch, no aria2 packaging, and no claim of performance superiority.')
    $report | Set-Content -LiteralPath (Join-Path $root 'aria2-viability-report.md') -Encoding utf8
    Write-Host "PASS: $($results.Count) aria2 Stage 1 checks; no production wiring or packaging change."
} finally {
    foreach ($process in $ownedProcesses) {
        try {
            $process.Refresh()
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {
            # Cleanup must continue for the remaining owned processes.
        }
    }
    # Never upload a credential if a native tool unexpectedly echoed it.
    foreach ($value in $sensitiveValues) {
        if ([string]::IsNullOrEmpty($value)) { continue }
        foreach ($log in @(Get-ChildItem -LiteralPath $root -File -Recurse -Filter '*.log' -ErrorAction SilentlyContinue)) {
            $text = Get-Content -LiteralPath $log.FullName -Raw -ErrorAction SilentlyContinue
            if ($null -ne $text -and $text.Contains($value)) {
                $script:SecretLeakDetected = $true
                $text = $text.Replace($value, '[REDACTED]')
                Set-Content -LiteralPath $log.FullName -Value $text -Encoding utf8
            }
        }
    }
}
if ($script:SecretLeakDetected) { throw 'A test secret appeared in a native log; the log was redacted and the viability gate fails.' }
