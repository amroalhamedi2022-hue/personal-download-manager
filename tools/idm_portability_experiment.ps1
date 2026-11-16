$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$url = 'https://mirror2.internetdownloadmanager.com/idman643build18.exe'
$expectedHash = '7346128fab42957b4254eabdb012bfdbd751e9069361e98a6916f26bb03d62ee'
$installer = Join-Path $env:RUNNER_TEMP 'idm-original.exe'
$staging = Join-Path $env:RUNNER_TEMP 'idm-experiment'
$resultDir = Join-Path $env:GITHUB_WORKSPACE 'encrypted_result'
New-Item -ItemType Directory -Force -Path $staging, $resultDir | Out-Null
$report = [ordered]@{
    source=$url; sha256_expected=$expectedHash; sha256_actual=$null
    installer_signature=$null; executable_signature=$null; version=$null
    files_copied=$false; launches_on_installed_system=$false
    portable_on_clean_windows_verified=$false
    note='Clean-Windows standalone operation and browser integration were NOT tested.'
    error=$null
}
try {
    Write-Host "Retrieving IDM from official Tonec mirror"
    Invoke-WebRequest -Uri $url -OutFile $installer -MaximumRedirection 3
    $hash = (Get-FileHash $installer -Algorithm SHA256).Hash.ToLowerInvariant()
    $report.sha256_actual = $hash
    if ($hash -ne $expectedHash) { throw "Pinned official-installer SHA256 mismatch: $hash" }
    $signature = Get-AuthenticodeSignature $installer
    $report.installer_signature = "$($signature.Status): $($signature.SignerCertificate.Subject)"
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Tonec') {
        throw "Installer signature is not valid Tonec: $($report.installer_signature)"
    }
    Write-Host "Verified official installer. Starting silent installation."
    $setup = Start-Process -FilePath $installer -ArgumentList '/skipdlgs' -PassThru -Wait
    Write-Host "Installer exit code: $($setup.ExitCode)"
    $possible = @(
        (Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Internet Download Manager\IDMan.exe'),
        (Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'Internet Download Manager\IDMan.exe')
    )
    $exe = $null
    for ($i=0; $i -lt 30; $i++) {
        $exe = $possible | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($exe) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $exe) { throw 'Installer did not create IDMan.exe in standard Program Files directories' }
    $appSig = Get-AuthenticodeSignature $exe
    $report.executable_signature = "$($appSig.Status): $($appSig.SignerCertificate.Subject)"
    if ($appSig.Status -ne 'Valid' -or $appSig.SignerCertificate.Subject -notmatch 'Tonec') {
        throw "Installed EXE signature not valid Tonec: $($report.executable_signature)"
    }
    $report.version = (Get-Item $exe).VersionInfo.FileVersion
    Copy-Item -LiteralPath (Split-Path $exe -Parent) -Destination (Join-Path $staging 'IDM') -Recurse
    $report.files_copied = $true
    @'
@echo off
setlocal
set "PROGRAM=%~dp0IDM\IDMan.exe"
if not exist "%PROGRAM%" (
  echo IDMan.exe was not found
  pause
  exit /b 1
)
start "" "%PROGRAM%"
'@ | Set-Content -Path (Join-Path $staging 'Start_IDM.cmd') -Encoding Ascii
    try {
        $process = Start-Process -FilePath $exe -PassThru
        Start-Sleep -Seconds 5
        $report.launches_on_installed_system = [bool](Get-Process -Id $process.Id -ErrorAction SilentlyContinue)
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } catch { Write-Host "Launch on installed system inconclusive: $_" }
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $staging 'TEST_REPORT.json') -Encoding UTF8
    $archive = Join-Path $env:RUNNER_TEMP 'IDM_Installed_Experimental.zip'
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $archive -CompressionLevel Optimal -Force

    # Never upload the commercial application unencrypted to a public repository.
    $publicKey = [Convert]::FromBase64String($env:IDM_PUBLIC_KEY_DER_B64)
    $rsa = [Security.Cryptography.RSA]::Create()
    $bytesRead = 0
    $rsa.ImportSubjectPublicKeyInfo($publicKey, [ref]$bytesRead)
    $key = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($key)
    $nonce = [byte[]]::new(12); [Security.Cryptography.RandomNumberGenerator]::Fill($nonce)
    $plaintext = [IO.File]::ReadAllBytes($archive)
    $ciphertext = [byte[]]::new($plaintext.Length)
    $tag = [byte[]]::new(16)
    $aes = [Security.Cryptography.AesGcm]::new($key, 16)
    try { $aes.Encrypt($nonce, $plaintext, $ciphertext, $tag) } finally { $aes.Dispose() }
    [IO.File]::WriteAllBytes((Join-Path $resultDir 'ciphertext.bin'), $ciphertext)
    [IO.File]::WriteAllBytes((Join-Path $resultDir 'nonce.bin'), $nonce)
    [IO.File]::WriteAllBytes((Join-Path $resultDir 'tag.bin'), $tag)
    [IO.File]::WriteAllBytes((Join-Path $resultDir 'encrypted-key.bin'), $rsa.Encrypt($key, [Security.Cryptography.RSAEncryptionPadding]::OaepSHA256))
    $rsa.Dispose(); [Array]::Clear($key, 0, $key.Length)
    Remove-Item -LiteralPath $archive -Force
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $resultDir 'report.json') -Encoding UTF8
    Write-Host "Encrypted-only archive produced; separate clean-Windows verification remains necessary."
} catch {
    $report.error = "$_"
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $resultDir 'report.json') -Encoding UTF8
    Write-Error "IDM experimental Windows build FAILED: $_"
    exit 1
}
