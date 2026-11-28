# Shared readiness gate for the local HTTP fixture's port file.
# The fixture binds its socket before publishing the port, but the file can be
# visible while Path.write_text is still opening/writing it. Existence alone
# is not readiness; accept only a complete decimal TCP port in 1..65535.
function Wait-HyperGetLoopbackHttpPort {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string] $PortFile,
        [Parameter(Mandatory = $true)][System.Diagnostics.Process] $Server,
        [Parameter(Mandatory = $true)][string] $FixtureName,
        [ValidateRange(1, 600)][int] $Attempts = 80,
        [ValidateRange(10, 5000)][int] $DelayMilliseconds = 250,
        [string] $ServerErrorPath
    )

    Set-StrictMode -Version Latest
    $path = [IO.Path]::GetFullPath($PortFile)
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if ($Server.HasExited) {
            $detail = ''
            if (-not [string]::IsNullOrWhiteSpace($ServerErrorPath) -and
                (Test-Path -LiteralPath $ServerErrorPath -PathType Leaf)) {
                try { $detail = [IO.File]::ReadAllText($ServerErrorPath).Trim() } catch { $detail = '' }
            }
            $message = "$FixtureName exited before publishing a port (exit=$($Server.ExitCode))."
            if (-not [string]::IsNullOrWhiteSpace($detail)) { $message += " Fixture stderr: $detail" }
            throw $message
        }

        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $portText = ''
            try { $portText = [IO.File]::ReadAllText($path).Trim() } catch { $portText = '' }
            if ($portText -match '^\d{1,5}$') {
                $parsedPort = 0
                if ([int]::TryParse($portText, [ref]$parsedPort) -and
                    $parsedPort -ge 1 -and $parsedPort -le 65535) {
                    Write-Host "$FixtureName published a complete valid loopback port ($parsedPort)."
                    return $parsedPort
                }
            }
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    }
    throw "$FixtureName did not publish a complete valid port file (1..65535) within $Attempts attempts."
}
