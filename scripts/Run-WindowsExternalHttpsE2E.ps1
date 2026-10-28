<#
.SYNOPSIS
Installed Windows HyperGet external HTTPS + Range acceptance.
.DESCRIPTION
Uses a public HTTP test endpoint over normal HTTPS certificate validation.
Compares the installed application's complete 10 MiB output against an
independent reference fetch and verifies a query-bearing Range request.
This is not a provider-specific signed-token expiry test; the local verified-TLS
JUnit test remains the controlled old-token/new-token acceptance.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $InstallerPath,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [Parameter(Mandatory = $true)][string] $SourceSha
)

$ErrorActionPreference = 'Stop'
$stage = 'prepare'
$expectedBytes = 10485760
$installerSha = 'NOT_AVAILABLE'
$expectedHash = 'NOT_AVAILABLE'
$actualHash = 'NOT_AVAILABLE'
$rangeHash = 'NOT_AVAILABLE'
$rangePrefixHash = 'NOT_AVAILABLE'
$rangeStatus = 'NOT_AVAILABLE'
$rangeBytes = 0
$guiProcess = $null
$canonicalUri = [Uri]'https://httpbin.io/range/10485760'
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$reportPath = Join-Path $outputRoot 'external-https-e2e.txt'
$runRoot = Join-Path $env:RUNNER_TEMP ("hyperget-external-https-" + [Guid]::NewGuid().ToString('N'))
$appDir = Join-Path $runRoot 'application'
$downloadsDir = Join-Path $runRoot 'downloads'
$referencePath = Join-Path $runRoot 'reference-10MiB.bin'
$rangePath = Join-Path $runRoot 'range-1KiB.bin'
$prefixPath = Join-Path $runRoot 'reference-prefix-1KiB.bin'
$downloadPath = Join-Path $downloadsDir 'external-https.bin'
$externalUrl = $canonicalUri.AbsoluteUri + '?hg_external_https_e2e=1'

function Write-ExternalHttpsEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $Result,
        [string] $FailureType = 'none'
    )
    @(
        "Result=$Result"
        "SourceSha=$SourceSha"
        "Stage=$stage"
        "EndpointHost=$($canonicalUri.Host)"
        "EndpointScheme=$($canonicalUri.Scheme)"
        "ExpectedBytes=$expectedBytes"
        "ExpectedSHA256=$expectedHash"
        "ActualSHA256=$actualHash"
        "RangeStatus=$rangeStatus"
        "RangeBytes=$rangeBytes"
        "RangePrefixSHA256=$rangePrefixHash"
        "RangeActualSHA256=$rangeHash"
        "InstallerSHA256=$installerSha"
        "SignedTokenExpiry=NOT_TESTED"
        "FailureType=$FailureType"
    ) | Set-Content -LiteralPath $reportPath -Encoding utf8
}

try {
    $stage = 'validate-installer-and-endpoint'
    if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) { throw 'Test installer not found.' }
    if ($canonicalUri.Scheme -ne 'https' -or $canonicalUri.Host -ne 'httpbin.io' -or $canonicalUri.UserInfo) {
        throw 'External endpoint failed HTTPS host preflight.'
    }
    $installerPathResolved = (Resolve-Path -LiteralPath $InstallerPath).Path
    $installerSha = (Get-FileHash -LiteralPath $installerPathResolved -Algorithm SHA256).Hash.ToLowerInvariant()

    New-Item -ItemType Directory -Path $runRoot,$downloadsDir -Force | Out-Null

    $stage = 'fetch-external-reference-with-default-tls-validation'
    # Deliberately do not use -SkipCertificateCheck or a custom trust bypass.
    Invoke-WebRequest -Uri $canonicalUri.AbsoluteUri -OutFile $referencePath -TimeoutSec 180 -MaximumRedirection 5 -ErrorAction Stop | Out-Null
    $referenceLength = (Get-Item -LiteralPath $referencePath).Length
    if ($referenceLength -ne $expectedBytes) {
        throw "External HTTPS reference length mismatch: expected $expectedBytes bytes, got $referenceLength."
    }
    $expectedHash = (Get-FileHash -LiteralPath $referencePath -Algorithm SHA256).Hash.ToLowerInvariant()

    $stage = 'verify-external-range-response'
    $rangeResponse = Invoke-WebRequest -Uri $externalUrl -Headers @{ Range = 'bytes=0-1023' } -OutFile $rangePath -PassThru -TimeoutSec 90 -MaximumRedirection 5 -ErrorAction Stop
    $rangeStatus = [int]$rangeResponse.StatusCode
    $rangeBytes = (Get-Item -LiteralPath $rangePath).Length
    if ($rangeStatus -ne 206 -or $rangeBytes -ne 1024) {
        throw "External HTTPS Range probe failed: status=$rangeStatus bytes=$rangeBytes."
    }
    $contentRange = [string]$rangeResponse.Headers['Content-Range']
    if ($contentRange -notmatch '^bytes 0-1023/10485760$') {
        throw 'External HTTPS Range response did not report the exact expected Content-Range.'
    }
    $referenceBytes = [IO.File]::ReadAllBytes($referencePath)
    $prefixBytes = New-Object byte[] 1024
    [Array]::Copy($referenceBytes, $prefixBytes, 1024)
    [IO.File]::WriteAllBytes($prefixPath, $prefixBytes)
    $rangePrefixHash = (Get-FileHash -LiteralPath $prefixPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $rangeHash = (Get-FileHash -LiteralPath $rangePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($rangeHash -ne $rangePrefixHash) {
        throw 'External HTTPS Range bytes do not match the prefix of the full reference resource.'
    }

    $stage = 'install-hyperget'
    $installerProcess = Start-Process -FilePath $installerPathResolved -ArgumentList @('/S', "/D=$appDir") -PassThru
    if (-not $installerProcess.WaitForExit(180000)) {
        Stop-Process -Id $installerProcess.Id -Force -ErrorAction SilentlyContinue
        throw 'Silent HyperGet test installation timed out.'
    }
    if ($installerProcess.ExitCode -ne 0) { throw "Silent HyperGet test installer exited with code $($installerProcess.ExitCode)." }

    $executables = @(Get-ChildItem -LiteralPath $appDir -Filter '*.exe' -Recurse -File)
    $gui = @($executables | Where-Object { $_.BaseName -eq 'HyperGet' })
    $legacyGui = @($executables | Where-Object { $_.BaseName -eq 'DownloadManager' })
    $cli = @($executables | Where-Object { $_.BaseName -eq 'DownloadManagerCli' })
    if ($gui.Count -ne 1 -or $legacyGui.Count -ne 0 -or $cli.Count -ne 1) {
        throw "Installed executable identity mismatch: HyperGet=$($gui.Count), legacy GUI=$($legacyGui.Count), CLI=$($cli.Count)."
    }

    $stage = 'launch-installed-gui'
    $guiProcess = Start-Process -FilePath $gui[0].FullName -PassThru
    Start-Sleep -Seconds 5
    if ($guiProcess.HasExited) { throw "Installed HyperGet GUI exited early with code $($guiProcess.ExitCode)." }

    $stage = 'download-external-https-through-hyperget'
    # The query value is synthetic, not a secret or a real signed token.
    $cliOutput = @(& $cli[0].FullName download add http --link $externalUrl --folder $downloadsDir --name 'external-https.bin' --start 2>&1)
    $cliExit = $LASTEXITCODE
    if ($cliExit -ne 0) { throw "Installed HyperGet CLI rejected external HTTPS download (exit=$cliExit)." }

    $deadline = (Get-Date).AddMinutes(3)
    $success = $false
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $downloadPath -PathType Leaf) {
            try {
                $length = (Get-Item -LiteralPath $downloadPath -ErrorAction Stop).Length
                if ($length -eq $expectedBytes) {
                    $candidateHash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
                    if ($candidateHash -eq $expectedHash) {
                        $actualHash = $candidateHash
                        $success = $true
                        break
                    }
                }
            } catch {
                # The destination can be briefly held open at the final write.
            }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $success) { throw 'Installed HyperGet external HTTPS output did not reach the reference size and SHA-256 before timeout.' }

    Start-Sleep -Seconds 2
    $finalLength = (Get-Item -LiteralPath $downloadPath).Length
    $finalHash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($finalLength -ne $expectedBytes -or $finalHash -ne $expectedHash) {
        throw 'Installed HyperGet external HTTPS output changed after the initial hash match.'
    }
    $actualHash = $finalHash
    $stage = 'accepted'
    Write-ExternalHttpsEvidence -Result 'PASS'
    Write-Host "PASS: installed HyperGet external HTTPS bytes and Range verified; bytes=$finalLength; SHA256=$actualHash; installerSHA256=$installerSha."
    Write-Host "Evidence=$reportPath"
}
catch {
    $failureType = $_.Exception.GetType().Name
    Write-ExternalHttpsEvidence -Result 'FAIL' -FailureType $failureType
    throw "External HTTPS acceptance failed at stage '$stage' (exception type '$failureType'). See sanitized evidence at '$reportPath'."
}
finally {
    if ($guiProcess -and -not $guiProcess.HasExited) {
        Stop-Process -Id $guiProcess.Id -Force -ErrorAction SilentlyContinue
    }
    try {
        $prefix = [IO.Path]::GetFullPath($appDir).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    } catch {
        # The isolated hosted runner is discarded after the workflow.
    }
}
