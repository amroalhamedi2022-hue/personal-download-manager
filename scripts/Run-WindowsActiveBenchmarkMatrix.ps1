<#
.SYNOPSIS
Measures the installed Windows downloader while 1, 4 and 16 real HTTP
transfers are active, three independent rounds per workload by default.
.DESCRIPTION
Uses the installed GUI/CLI and a deterministic loopback Range server. Each
download must pass SHA256. Captures actual app process-tree CPU/RAM; never
claims an IDM-vs-our-app comparison without matching reference measurements.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $GuiExecutable,
    [Parameter(Mandatory = $true)][string] $CliExecutable,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [ValidateRange(1, 5)][int] $Rounds = 3,
    [ValidateRange(5, 60)][int] $SampleSeconds = 8,
    [ValidateRange(10, 500)][int] $ServerChunkDelayMs = 500,
    [ValidateRange(8, 128)][int] $FileSizeMiB = 32,
    [ValidateRange(120, 600)][int] $CompletionTimeoutSeconds = 300
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath($OutputDirectory)
$guiPath = [IO.Path]::GetFullPath($GuiExecutable)
$cliPath = [IO.Path]::GetFullPath($CliExecutable)
$sampler = Join-Path $PSScriptRoot 'Measure-WindowsDownloadManager.ps1'
$fixtureScript = Join-Path $PSScriptRoot 'LocalHttpRangeFixture.py'
foreach ($required in @($guiPath, $cliPath, $sampler, $fixtureScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required program or test script missing: $required"
    }
}
if (Test-Path -LiteralPath $destination) {
    if ((@(Get-ChildItem -LiteralPath $destination -Force)).Count -gt 0) {
        throw "Benchmark destination must be empty: $destination"
    }
}
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$fixture = Join-Path $destination 'source-fixture.bin'
$portPath = Join-Path $destination 'server-port.txt'
$downloadFolder = Join-Path $destination 'downloads'
$metricFolder = Join-Path $destination 'measurements'
New-Item -ItemType Directory -Path $downloadFolder,$metricFolder -Force | Out-Null
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $fixture $FileSizeMiB
if ($LASTEXITCODE -ne 0) { throw 'Python failed to create the deterministic HTTP fixture.' }
$expectedBytes = [long]$FileSizeMiB * 1MB
if ((Get-Item -LiteralPath $fixture).Length -ne $expectedBytes) {
    throw 'HTTP fixture length differs from the requested workload.'
}
$expectedSha = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
$records = [System.Collections.Generic.List[object]]::new()
$server = $null
$gui = $null
$originalMaxConcurrentDownloads = $null
function Invoke-MaxConcurrentDownloadsSetting {
    param([Nullable[int]] $SetValue = $null)
    $arguments = @('settings', 'max-concurrent-downloads')
    if ($null -ne $SetValue) {
        $arguments += @('--set', "$SetValue")
    }
    $output = @(& $cliPath @arguments 2>&1)
    $exit = $LASTEXITCODE
    if ($exit -ne 0) {
        throw "Installed CLI concurrency setting failed ($exit): $($output -join ' ')"
    }
    $values = @($output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
    if ($values.Count -ne 1) {
        throw "Concurrency setting did not return exactly one integer: $($output -join ' ')"
    }
    return [int]$values[0]
}
try {
    $serverArgs = @(
        ('"' + $fixtureScript + '"'), '--file', ('"' + $fixture + '"'),
        '--port-file', ('"' + $portPath + '"'),
        '--chunk-delay-ms', "$ServerChunkDelayMs",
        # Aggregate per-task pacing makes a 32 MiB task last longer than
        # the 8s CPU/RAM window even when it uses many parallel Range sockets.
        # The gate still proves 16 live sockets; all 63 SHA checks remain strict.
        '--task-chunk-delay-ms', "180"
    )
    $server = Start-Process -FilePath 'python' -ArgumentList $serverArgs -PassThru -RedirectStandardOutput (Join-Path $destination 'server-stdout.txt') -RedirectStandardError (Join-Path $destination 'server-stderr.txt')
    for ($wait = 0; $wait -lt 80 -and -not (Test-Path -LiteralPath $portPath); $wait++) {
        if ($server.HasExited) { throw "Fixture server exited: $($server.ExitCode)" }
        Start-Sleep -Milliseconds 250
    }
    if (-not (Test-Path -LiteralPath $portPath)) { throw 'Fixture server did not start.' }
    $port = [int](Get-Content -LiteralPath $portPath -Raw)
    if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid fixture server port.' }
    $url = "http://127.0.0.1:$port/fixture.bin"
    $gui = Start-Process -FilePath $guiPath -PassThru
    $foundApp = $false
    for ($wait = 0; $wait -lt 40; $wait++) {
        Start-Sleep -Milliseconds 500
        $roots = @(Get-Process -Name 'DownloadManager' -ErrorAction SilentlyContinue)
        if ($roots.Count -gt 0 -and -not $gui.HasExited) {
            $foundApp = $true
            break
        }
    }
    if (-not $foundApp) { throw 'Installed GUI did not stay running.' }
    $originalMaxConcurrentDownloads = Invoke-MaxConcurrentDownloadsSetting
    # Measure each workload under its OWN persisted queue limit, not a
    # blanket 16-job setting. Otherwise the 1/4 scenarios do not verify
    # that the installed queue respects the user's configured cap.
    foreach ($jobs in @(1, 4, 16)) {
        $appliedMaxConcurrentDownloads = Invoke-MaxConcurrentDownloadsSetting -SetValue $jobs
        if ($appliedMaxConcurrentDownloads -ne $jobs) {
            throw "Concurrency cap for $jobs-task workload returned $appliedMaxConcurrentDownloads."
        }
        Start-Sleep -Milliseconds 750
        $verifiedMaxConcurrentDownloads = Invoke-MaxConcurrentDownloadsSetting
        if ($verifiedMaxConcurrentDownloads -ne $jobs) {
            throw "Concurrency cap for $jobs-task workload did not survive separate CLI read: $verifiedMaxConcurrentDownloads."
        }
        Write-Host "Installed max concurrent downloads verified at $jobs for the $jobs-task workload."
        for ($round = 1; $round -le $Rounds; $round++) {
            $scenario = "active-$jobs"
            $statsUrl = "http://127.0.0.1:$port/stats"
            # Count only long data GETs; short probes cannot satisfy the
            # overlap criterion. Do not block early sockets: a shared host
            # connection cap could prevent later jobs from reaching GET.
            $resetUri = if ($jobs -eq 16) { "${statsUrl}?reset=1&gate_tasks=16" } else { "${statsUrl}?reset=1" }
            $prior = Invoke-RestMethod -Uri $resetUri -TimeoutSec 10
            $expectedGate = if ($jobs -eq 16) { 16 } else { 0 }
            if ([int]$prior.gate_expected_tasks -ne $expectedGate) {
                throw 'Fixture admission gate configuration failed.'
            }
            if ([int]$prior.active_requests -ne 0 -or [int]$prior.total_requests -ne 0) {
                throw 'Fixture statistics reset failed before the workload.'
            }
            $taskFiles = [System.Collections.Generic.List[object]]::new()
            Write-Host "Running $scenario round $round/$Rounds on installed Windows app"
            for ($index = 1; $index -le $jobs; $index++) {
                $filename = "$scenario-round-$round-task-$index.bin"
                $outputPath = Join-Path $downloadFolder $filename
                if (Test-Path -LiteralPath $outputPath) {
                    throw "Destination existed before starting: $filename"
                }
                $taskUrl = "${url}?task=$filename"
                $cliOutput = @(& $cliPath download add http --link $taskUrl --folder $downloadFolder --name $filename 2>&1)
                $exit = $LASTEXITCODE
                if ($exit -ne 0) {
                    throw "Installed CLI add failed ($exit): $($cliOutput -join ' ')"
                }
                $ids = @($cliOutput | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
                if ($ids.Count -ne 1) {
                    throw "CLI did not return exactly one task ID: $($cliOutput -join ' ')"
                }
                $taskFiles.Add([pscustomobject]@{ Id = $ids[0]; Name = $filename; Path = $outputPath })
            }
            # Prepare all task records first, then start the whole set using
            # ONE installed CLI / IPC call; launching 16 separate CLI processes
            # with --start would serialize their starts and let early tasks
            # finish before later ones started (a false concurrency failure).
            $staged = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
            if ([int]$staged.active_requests -ne 0) {
                throw 'An Added-only task started HTTP without Resume.'
            }
            $startIds = @($taskFiles | ForEach-Object { [long]$_.Id })
            & $cliPath download resume @startIds
            if ($LASTEXITCODE -ne 0) {
                throw "Batch CLI Resume failed for $scenario round $round."
            }
            # Poll real on-wire requests, not queue state. Permit up to 75s
            # for 16 segmented jobs to start; the per-task 180ms/chunk pacing
            # keeps a 32MiB transfer alive for ~92s. Once all are live, the
            # original strict eight-second overlap + 63 SHA gate still apply.
            $allActive = $false
            for ($attempt = 0; $attempt -lt 300; $attempt++) {
                $ready = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
                if ([int]$ready.active_distinct_tasks -ge $jobs) {
                    $allActive = $true
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if ($allActive -and $jobs -eq 16 -and -not [bool]$ready.gate_released) {
                throw '16 live sockets reported but fixture admission gate was not released.'
            }
            if (-not $allActive) {
                # Preserve evidence before the finally block shuts down the GUI.
                # In particular distinguish a queue/admission failure from a
                # fixture that saw all tasks at some point but not concurrently.
                $diagnostics = [System.Collections.Generic.List[string]]::new()
                $diagnostics.Add("Scenario: $scenario; round: $round; expected live tasks: $jobs")
                $diagnostics.Add("Fixture stats: $($ready | ConvertTo-Json -Compress)")
                $diagnostics.Add("Configured max concurrent downloads: $(Invoke-MaxConcurrentDownloadsSetting)")
                $statusOutput = @(& $cliPath download show --status-lines @startIds 2>&1)
                $diagnostics.Add("Installed CLI task status (exit $LASTEXITCODE; ID<TAB>Status):")
                $diagnostics.AddRange([string[]]@($statusOutput | ForEach-Object { "$_" }))
                foreach ($task in $taskFiles) {
                    $length = if (Test-Path -LiteralPath $task.Path -PathType Leaf) {
                        (Get-Item -LiteralPath $task.Path).Length
                    } else { 'missing' }
                    $diagnostics.Add("Task $($task.Id) $($task.Name): output bytes=$length")
                }
                $diagnostics | Set-Content -LiteralPath (Join-Path $destination 'failure-diagnostics.txt') -Encoding utf8
                throw "Only $($ready.active_distinct_tasks) of $jobs tasks were active; cannot label this sample a $jobs-concurrent workload. See failure-diagnostics.txt."
            }
            # Slow loopback source ensures the test samples real in-flight transfers,
            # even with the downloader's parallel HTTP Range segments.
            $csvPath = Join-Path $metricFolder "$scenario-round-$round.csv"
            & $sampler -RootProcessName 'DownloadManager' -Scenario $scenario -SourceSha $SourceSha -OutputCsv $csvPath -DurationSeconds $SampleSeconds -WarmupSeconds 1 -SampleIntervalMs 1000 -RunLabel "round-$round"
            if (-not (Test-Path -LiteralPath $csvPath)) {
                throw "Missing Windows process-tree CSV for $scenario round $round"
            }
            $samples = @(Import-Csv -LiteralPath $csvPath)
            if ($samples.Count -lt 5 -or @($samples | Where-Object { [int]$_.ProcessCount -lt 1 }).Count -gt 0) {
                throw "Process-tree measurements are missing/invalid: $scenario round $round"
            }
            $during = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
            if ([int]$during.peak_distinct_tasks -lt $jobs -or [int]$during.active_distinct_tasks -lt $jobs) {
                # Preserve the exact synthetic task IDs and transferred bytes
                # at failure; a Range handoff gap is not a completed download.
                $diagnostics = [System.Collections.Generic.List[string]]::new()
                $diagnostics.Add("Scenario: $scenario; round: $round; expected: $jobs")
                $diagnostics.Add("Fixture stats: $($during | ConvertTo-Json -Compress -Depth 6)")
                $diagnostics.Add("Configured max concurrent downloads: $(Invoke-MaxConcurrentDownloadsSetting)")
                $statusOutput = @(& $cliPath download show --status-lines @startIds 2>&1)
                $diagnostics.Add("Installed CLI task status (exit $LASTEXITCODE; ID<TAB>Status):")
                $diagnostics.AddRange([string[]]@($statusOutput | ForEach-Object { "$_" }))
                foreach ($task in $taskFiles) {
                    $length = if (Test-Path -LiteralPath $task.Path -PathType Leaf) {
                        (Get-Item -LiteralPath $task.Path).Length
                    } else { 'missing' }
                    $diagnostics.Add("Task $($task.Id) $($task.Name): output bytes=$length")
                }
                $diagnostics | Set-Content -LiteralPath (Join-Path $destination 'failure-diagnostics.txt') -Encoding utf8
                throw "$scenario did not keep all $jobs tasks transferring throughout the measurement window: active=$($during.active_distinct_tasks), peak=$($during.peak_distinct_tasks). See failure-diagnostics.txt."
            }
            $pending = [System.Collections.Generic.HashSet[string]]::new([string[]]@($taskFiles | ForEach-Object { $_.Path }), [System.StringComparer]::OrdinalIgnoreCase)
            $deadline = [DateTime]::UtcNow.AddSeconds($CompletionTimeoutSeconds)
            while ($pending.Count -gt 0 -and [DateTime]::UtcNow -lt $deadline) {
                foreach ($path in @($pending)) {
                    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
                    if ((Get-Item -LiteralPath $path).Length -ne $expectedBytes) { continue }
                    # A full-length destination can still be held by an active
                    # downloader flush; retry transient sharing violations.
                    try {
                        $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
                    } catch {
                        # Get-FileHash may surface a PowerShell error record
                        # wrapping Win32 sharing violations, not IOException.
                        # Strict SHA256 + overall completion deadline remain.
                        continue
                    }
                    if ($actual -eq $expectedSha) { [void]$pending.Remove($path) }
                }
                if ($pending.Count -gt 0) { Start-Sleep -Milliseconds 500 }
            }
            if ($pending.Count -ne 0) {
                $diagnostics = [System.Collections.Generic.List[string]]::new()
                $diagnostics.Add("Scenario: $scenario; round: $round; timeout seconds: $CompletionTimeoutSeconds")
                $diagnostics.Add("Fixture stats: $($during | ConvertTo-Json -Compress)")
                foreach ($path in @($pending)) {
                    if (Test-Path -LiteralPath $path -PathType Leaf) {
                        $file = Get-Item -LiteralPath $path
                        $diagnostics.Add("Pending file: $path; bytes=$($file.Length)")
                    } else {
                        $diagnostics.Add("Pending file missing: $path")
                    }
                }
                $statusOutput = @(& $cliPath download show --status-lines @startIds 2>&1)
                $diagnostics.Add('Installed CLI task status (ID<TAB>Status):')
                $diagnostics.AddRange([string[]]@($statusOutput | ForEach-Object { "$_" }))
                $diagnostics | Set-Content -LiteralPath (Join-Path $destination 'failure-diagnostics.txt') -Encoding utf8
                throw "Timed out or SHA mismatch for $scenario round ${round}: $($pending -join ', ')"
            }
            Start-Sleep -Seconds 2
            $after = Invoke-RestMethod -Uri $statsUrl -TimeoutSec 10
            if ([int]$after.active_requests -ne 0 -or [int]$after.peak_distinct_tasks -lt $jobs -or [int]$after.unique_tasks_seen -ne $jobs) {
                throw "HTTP concurrency counters invalid for $scenario round $round."
            }
            foreach ($task in $taskFiles) {
                $actualLength = (Get-Item -LiteralPath $task.Path).Length
                $actualSha = (Get-FileHash -LiteralPath $task.Path -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($actualLength -ne $expectedBytes -or $actualSha -ne $expectedSha) {
                    throw "Output mutated after completion: $($task.Name)"
                }
                $records.Add([pscustomobject]@{
                    SourceSha = $SourceSha; Scenario = $scenario; Round = $round
                    TaskId = $task.Id; OutputName = $task.Name
                    Bytes = $actualLength; SHA256 = $actualSha; Verified = $true
                    PeakDistinctTasks = [int]$after.peak_distinct_tasks
                    PeakHttpConnections = [int]$after.peak_requests
                })
            }
            # Preserve a useful, strictly verified partial manifest even if a
            # later workload fails. The final audit still requires all rows.
            $records | Export-Csv -LiteralPath (Join-Path $destination 'verified-transfers.csv') -NoTypeInformation -Encoding utf8
            Write-Host "$scenario round ${round}: $jobs/$jobs installed HTTP downloads verified."
        }
    }
    $expectedTaskCount = $Rounds * (1 + 4 + 16)
    if ($records.Count -ne $expectedTaskCount) {
        throw "Expected $expectedTaskCount SHA256-verified tasks, observed $($records.Count)"
    }
    $metricFiles = @(Get-ChildItem -LiteralPath $metricFolder -Filter '*.csv' -File)
    if ($metricFiles.Count -ne $Rounds * 3) {
        throw 'Missing one or more active Windows CPU/RAM benchmark rounds.'
    }
    Write-Host "SUCCESS: $expectedTaskCount verified HTTP downloads; $($metricFiles.Count) CPU/RAM CSVs."
} finally {
    if ($null -ne $originalMaxConcurrentDownloads -and $gui -and -not $gui.HasExited) {
        try {
            $restored = Invoke-MaxConcurrentDownloadsSetting -SetValue $originalMaxConcurrentDownloads
            if ($restored -ne $originalMaxConcurrentDownloads) {
                Write-Warning "Concurrency setting restore returned $restored instead of $originalMaxConcurrentDownloads."
            } else {
                # The existing settings store intentionally debounces writes.
                # Keep the real GUI alive long enough for the restored value to
                # reach both the live queue and its normal persistent store.
                Start-Sleep -Milliseconds 750
                Write-Host "Restored max concurrent downloads to $restored."
            }
        } catch {
            Write-Warning "Failed to restore max concurrent downloads: $($_.Exception.Message)"
        }
    }
    if ($gui -and -not $gui.HasExited) {
        Stop-Process -Id $gui.Id -Force -ErrorAction SilentlyContinue
    }
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
