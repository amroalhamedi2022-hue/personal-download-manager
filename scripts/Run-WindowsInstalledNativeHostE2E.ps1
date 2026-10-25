<#
.SYNOPSIS
Verify installed HyperGet's native-messaging registration and real stdio host.
.DESCRIPTION
Runs the installed GUI and the installed native host on Windows, validates
Chrome/Firefox HKCU manifests and sends real length-prefixed ping/error frames.
This does NOT claim a real Chrome/Firefox extension was launched.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$GuiExecutable,
    [Parameter(Mandatory=$true)][string]$HostExecutable,
    [Parameter(Mandatory=$true)][string]$SourceSha,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$hostPath = [IO.Path]::GetFullPath($HostExecutable)
$root = [IO.Path]::GetFullPath($OutputDirectory)
foreach ($path in @($guiPath, $hostPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Installed executable missing: $path" }
}
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) { throw 'Evidence directory must be empty' }
}
New-Item -ItemType Directory -Path $root -Force | Out-Null
$hostName = 'app.personaldownloadmanager'
$gui = $null
$native = $null

function Read-Exact([IO.Stream]$stream, [int]$count) {
    $buffer = [byte[]]::new($count)
    $offset = 0
    while ($offset -lt $count) {
        $read = $stream.ReadAsync($buffer, $offset, $count - $offset)
        if (-not $read.Wait(30000)) { throw 'Installed native host response timed out' }
        if ($read.Result -le 0) { throw 'Installed native host closed stdout mid-frame' }
        $offset += $read.Result
    }
    return ,$buffer
}
function Invoke-NativeRequest([IO.Stream]$inputStream, [IO.Stream]$outputStream, [string]$id, [string]$action) {
    $request = @{
        id = $id
        content = @{ action = $action; isError = $false; payload = 'null' }
    } | ConvertTo-Json -Depth 5 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($request)
    $length = [BitConverter]::GetBytes([int]$bytes.Length)
    if (-not [BitConverter]::IsLittleEndian) { [Array]::Reverse($length) }
    $inputStream.Write($length, 0, 4)
    $inputStream.Write($bytes, 0, $bytes.Length)
    $inputStream.Flush()
    $prefix = Read-Exact $outputStream 4
    if (-not [BitConverter]::IsLittleEndian) { [Array]::Reverse($prefix) }
    $size = [BitConverter]::ToInt32($prefix, 0)
    if ($size -lt 1 -or $size -gt 1048576) { throw "Native host invalid frame size: $size" }
    $body = Read-Exact $outputStream $size
    $response = [Text.Encoding]::UTF8.GetString($body) | ConvertFrom-Json
    if ($response.id -cne $id) { throw 'Native host did not preserve browser request correlation ID' }
    return $response
}

try {
    $gui = Start-Process -FilePath $guiPath -PassThru
    $manifestInfo = @{}
    foreach ($browser in @('Google\Chrome', 'Mozilla')) {
        $keyPath = if ($browser -eq 'Mozilla') {
            "Registry::HKEY_CURRENT_USER\SOFTWARE\Mozilla\NativeMessagingHosts\$hostName"
        } else {
            "Registry::HKEY_CURRENT_USER\SOFTWARE\Google\Chrome\NativeMessagingHosts\$hostName"
        }
        $manifestPath = $null
        for ($attempt = 0; $attempt -lt 100; $attempt++) {
            if ($gui.HasExited) { throw 'Installed GUI exited before registering native host' }
            $key = Get-Item -LiteralPath $keyPath -ErrorAction SilentlyContinue
            if ($null -ne $key) {
                $manifestPath = [string]$key.GetValue('')
                if ($manifestPath -and (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { break }
            }
            Start-Sleep -Milliseconds 300
        }
        if (-not $manifestPath -or -not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            throw "Native host HKCU registration missing for $browser"
        }
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ($manifest.name -cne $hostName -or $manifest.type -cne 'stdio') {
            throw "Invalid $browser native host name/type"
        }
        if (-not [string]::Equals([IO.Path]::GetFullPath([string]$manifest.path), $hostPath, [StringComparison]::OrdinalIgnoreCase)) {
            throw "$browser manifest does not target installed native host"
        }
        if ($browser -eq 'Mozilla') {
            if (@($manifest.allowed_extensions).Count -lt 1) { throw 'Firefox native host has no allowed extension' }
            $manifestInfo.Firefox = $true
        } else {
            if (@($manifest.allowed_origins).Count -lt 1 -or
                @($manifest.allowed_origins | Where-Object { $_ -match '^chrome-extension://[a-p]{32}/$' }).Count -lt 1) {
                throw 'Chrome native host origin allowlist invalid'
            }
            $manifestInfo.Chrome = $true
        }
    }

    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $hostPath
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $native = [Diagnostics.Process]::new()
    $native.StartInfo = $psi
    if (-not $native.Start()) { throw 'Could not start installed native host' }
    $inputStream = $native.StandardInput.BaseStream
    $outputStream = $native.StandardOutput.BaseStream
    foreach ($id in @('B_hyperget_ping_1', 'B_hyperget_ping_2')) {
        $reply = Invoke-NativeRequest $inputStream $outputStream $id 'ping'
        if ($reply.content.isError -ne $false -or $reply.content.payload -cne 'true') {
            throw "Installed native host ping failed: $id"
        }
    }
    $errorReply = Invoke-NativeRequest $inputStream $outputStream 'B_hyperget_unknown' 'hyperget-nonexistent-action'
    if ($errorReply.content.isError -ne $true) { throw 'Unknown native action did not return an error' }
    $safeError = $errorReply.content.payload | ConvertFrom-Json
    if ($safeError.errorType -cne 'REQUEST_FAILED' -or
        $safeError.message -cne 'HyperGet could not complete the browser request.') {
        throw 'Native host error reply violated generic error policy'
    }
    $native.StandardInput.Close()
    if (-not $native.WaitForExit(15000)) { throw 'Native host did not exit after browser EOF' }
    if ($native.ExitCode -ne 0) { throw "Native host EOF exit code: $($native.ExitCode)" }

    [pscustomobject]@{
        SourceSHA = $SourceSha
        HostName = $hostName
        ChromeManifestRegistered = $manifestInfo.Chrome
        FirefoxManifestRegistered = $manifestInfo.Firefox
        ManifestExecutableMatches = $true
        FramedPingReplies = 2
        ErrorCorrelationAndSanitization = $true
        BrowserEOFExitCode = 0
        BrowserExtensionActuallyLaunched = $false
        Verified = $true
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'native-host-report.json') -Encoding utf8
    Write-Host 'PASS: installed Windows native host registration, 2 framed ping requests, correlated safe error, and clean EOF.'
} finally {
    if ($native -and -not $native.HasExited) { Stop-Process -Id $native.Id -Force -ErrorAction SilentlyContinue }
    if ($gui -and -not $gui.HasExited) { Stop-Process -Id $gui.Id -Force -ErrorAction SilentlyContinue }
    if ($native) { $native.Dispose() }
}
