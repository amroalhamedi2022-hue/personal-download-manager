#requires -Version 7.0
<#
.SYNOPSIS
Runs a real Windows aria2 HTTP workload matrix with exact-byte and process-resource evidence.
.DESCRIPTION
Evaluation-only. Uses the same deterministic loopback fixture, pacing, task counts and
resource sampler as HyperGet's active benchmark. The stock upstream binary is never
copied into HyperGet; this script never invokes Torrent and explicitly disables RPC.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Aria2Executable,
    [Parameter(Mandatory = $true)][string] $SourceSha,
    [Parameter(Mandatory = $true)][string] $HarnessSha,
    [Parameter(Mandatory = $true)][string] $EngineArchiveSha256,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [ValidateRange(1, 3)][int] $Rounds = 3,
    [ValidateRange(1, 3)][int] $RoundStart = 1,
    [int[]] $Workloads = @(1, 4, 16),
    [ValidateRange(1, 8)][int] $Segments = 4,
    [ValidateRange(5, 60)][int] $SampleSeconds = 8,
    [ValidateRange(10, 500)][int] $ServerChunkDelayMs = 500,
    [ValidateRange(8, 128)][int] $FileSizeMiB = 32,
    [ValidateRange(120, 600)][int] $CompletionTimeoutSeconds = 300
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($SourceSha -notmatch '^[0-9a-fA-F]{40}$' -or $HarnessSha -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'SourceSha and HarnessSha must be full 40-character commits.'
}
if ($EngineArchiveSha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'EngineArchiveSha256 must be a full SHA-256.' }
if ($Segments -notin @(1, 4, 8)) { throw 'Segments must be one of 1, 4, or 8.' }
if ($RoundStart + $Rounds - 1 -gt 3) { throw 'RoundStart + Rounds must not exceed the three paired rounds.' }
if (@($Workloads | Where-Object { $_ -notin @(1, 4, 16) }).Count -gt 0 -or $Workloads.Count -eq 0) {
    throw 'Workloads must be a non-empty subset of 1, 4, and 16.'
}
if (@($Workloads | Sort-Object -Unique).Count -ne $Workloads.Count) { throw 'Duplicate workload values are not allowed.' }

$aria2 = [IO.Path]::GetFullPath($Aria2Executable)
if (-not (Test-Path -LiteralPath $aria2 -PathType Leaf)) { throw 'Pinned aria2c.exe is missing.' }
$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) {
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -gt 0) {
        throw 'aria2 benchmark output directory must be absent or empty.'
    }
} else {
    New-Item -ItemType Directory -Force -Path $root | Out-Null
}
$fixtureScript = Join-Path $PSScriptRoot 'LocalHttpRangeFixture.py'
$waitScript = Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1'
$sampler = Join-Path $PSScriptRoot 'Measure-WindowsDownloadManager.ps1'
foreach ($required in @($fixtureScript, $waitScript, $sampler)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required benchmark helper missing: $required" }
}
. $waitScript

$fixture = Join-Path $root 'source-fixture.bin'
$portPath = Join-Path $root 'server-port.txt'
$downloadFolder = Join-Path $root 'downloads'
$metricFolder = Join-Path $root 'measurements'
New-Item -ItemType Directory -Force -Path $downloadFolder,$metricFolder | Out-Null
& python -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(bytes(range(256))*(int(sys.argv[2])*4096))' $fixture $FileSizeMiB
if ($LASTEXITCODE -ne 0) { throw 'Python failed to create the deterministic aria2 fixture.' }
$expectedBytes = [long]$FileSizeMiB * 1MB
if ((Get-Item -LiteralPath $fixture).Length -ne $expectedBytes) { throw 'HTTP fixture size mismatch.' }
$expectedSha = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
$versionText = (& $aria2 --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'aria2 version 1\.37\.0') {
    throw 'The executable is not the pinned aria2 1.37.0 binary.'
}
$bitTorrentEnabled = $versionText -match '(?i)\bBitTorrent\b'
Write-Host "ARIA2_VERSION=1.37.0"
Write-Host "ARIA2_BINARY_SHA256=$EngineArchiveSha256"
Write-Host "ARIA2_BITTORRENT_ENABLED=$bitTorrentEnabled; evaluation-only, no Torrent input and never bundled."
Write-Host 'ARIA2_RPC_ENABLED=False; all requests use the local deterministic HTTP fixture.'
Write-Host "FIXTURE_BYTES=$expectedBytes; FIXTURE_SHA256=$expectedSha; SEGMENTS=$Segments"

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
    param([string] $FilePath, [string[]] $Arguments, [string] $Label)
    $stdout = Join-Path $root "$Label.stdout.log"
    $stderr = Join-Path $root "$Label.stderr.log"
    $line = ConvertTo-ProcessArgumentLine -Values $Arguments
    $process = Start-Process -FilePath $FilePath -ArgumentList $line -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $script:OwnedProcesses.Add($process) | Out-Null
    return $process
}
function Get-HttpStats {
    param([string] $BaseUri, [switch] $Reset, [int] $GateTasks = 0)
    if ($Reset) {
        $uri = if ($GateTasks -gt 0) { "$($BaseUri)/stats?reset=1&gate_tasks=$GateTasks" } else { "$($BaseUri)/stats?reset=1" }
    } else {
        $uri = "$($BaseUri)/stats"
    }
    return Invoke-RestMethod -Uri $uri -TimeoutSec 10
}
function Wait-HttpIdle {
    param([string] $BaseUri, [ValidateRange(1, 60)][int] $TimeoutSeconds = 20)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $latest = $null
    do {
        $latest = Get-HttpStats -BaseUri $BaseUri
        if ([int]$latest.active_requests -eq 0 -and [int]$latest.active_distinct_tasks -eq 0) {
            return $latest
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    return $latest
}
function Write-FailureDiagnostics {
    param([string] $Scenario, [int] $Round, [object] $Stats, [object[]] $Tasks, [string] $Reason)
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("Reason: $Reason")
    $lines.Add("Scenario: $Scenario; round: $Round; segments: $Segments")
    if ($null -ne $Stats) { $lines.Add("Fixture stats: $($Stats | ConvertTo-Json -Compress -Depth 6)") }
    foreach ($task in $Tasks) {
        $task.Process.Refresh()
        $exit = if ($task.Process.HasExited) { [string]$task.Process.ExitCode } else { 'running' }
        $bytes = if (Test-Path -LiteralPath $task.Path) { (Get-Item -LiteralPath $task.Path).Length } else { 'missing' }
        $lines.Add("Task $($task.Name): pid=$($task.Process.Id); exit=$exit; output=$bytes")
        foreach ($logPath in @($task.Stdout, $task.Stderr)) {
            if (Test-Path -LiteralPath $logPath -PathType Leaf) {
                $lines.Add("Log $([IO.Path]::GetFileName($logPath)):")
                $lines.AddRange([string[]]@(Get-Content -LiteralPath $logPath -Tail 30 -ErrorAction SilentlyContinue))
            }
        }
    }
    $lines | Set-Content -LiteralPath (Join-Path $root 'failure-diagnostics.txt') -Encoding utf8
}
$script:OwnedProcesses = [System.Collections.Generic.List[object]]::new()
$server = $null
$completedSuccessfully = $false
$records = [System.Collections.Generic.List[object]]::new()
$summaryRecords = [System.Collections.Generic.List[object]]::new()
$originalProxyValues = @{}
$proxyNames = @('http_proxy','https_proxy','ftp_proxy','all_proxy','HTTP_PROXY','HTTPS_PROXY','FTP_PROXY','ALL_PROXY')
try {
    foreach ($name in $proxyNames) {
        $item = Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        if ($null -ne $item) {
            $originalProxyValues[$name] = $item.Value
            Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        }
    }
    $serverArgs = @(
        $fixtureScript, '--file', $fixture, '--port-file', $portPath,
        '--chunk-delay-ms', "$ServerChunkDelayMs", '--task-chunk-delay-ms', '180'
    )
    $server = Start-OwnedProcess -FilePath 'python' -Arguments $serverArgs -Label 'loopback-http-fixture'
    $port = Wait-HyperGetLoopbackHttpPort -PortFile $portPath -Server $server -FixtureName 'aria2 paired HTTP benchmark fixture' -Attempts 80 -ServerErrorPath (Join-Path $root 'loopback-http-fixture.stderr.log')
    $baseUri = "http://127.0.0.1:$port"
    $baseUrl = "$($baseUri)/fixture.bin"

    for ($round = $RoundStart; $round -lt ($RoundStart + $Rounds); $round++) {
        foreach ($jobs in $Workloads) {
            $scenario = "active-$jobs"
            $scenarioDir = Join-Path $downloadFolder $scenario
            New-Item -ItemType Directory -Force -Path $scenarioDir | Out-Null
            $prior = Get-HttpStats -BaseUri $baseUri -Reset -GateTasks $(if ($jobs -eq 16) { 16 } else { 0 })
            if ([int]$prior.active_requests -ne 0 -or [int]$prior.total_requests -ne 0) {
                throw "Fixture statistics did not reset for $scenario round $round."
            }
            if ($jobs -eq 16 -and [int]$prior.gate_expected_tasks -ne 16) { throw 'The 16-task fixture gate was not configured.' }

            $taskFiles = [System.Collections.Generic.List[object]]::new()
            $transferStartedUtc = [DateTime]::UtcNow
            for ($index = 1; $index -le $jobs; $index++) {
                $filename = "aria2-$scenario-round-$round-task-$index.bin"
                $outputPath = Join-Path $scenarioDir $filename
                if (Test-Path -LiteralPath $outputPath) { throw "aria2 destination already exists: $filename" }
                $taskUrl = "$($baseUrl)?task=$filename"
                $arguments = @(
                    '--no-conf=true', '--enable-rpc=false', '--no-proxy=127.0.0.1',
                    '--console-log-level=warn', '--summary-interval=0',
                    '--file-allocation=none', '--allow-overwrite=true',
                    '--auto-file-renaming=false', '--continue=false',
                    "--split=$Segments", "--max-connection-per-server=$Segments",
                    '--min-split-size=1M', '--connect-timeout=10', '--timeout=60',
                    '--max-tries=3', '--retry-wait=1',
                    "--dir=$scenarioDir", "--out=$filename", $taskUrl
                )
                if (@($arguments | Where-Object { $_ -match '(?i)\.torrent|--bt-|torrent://' }).Count -gt 0) {
                    throw 'Torrent-related argument detected in the aria2 HTTP-only benchmark.'
                }
                $label = "$scenario-round-$round-task-$index"
                $stdout = Join-Path $root "$label.stdout.log"
                $stderr = Join-Path $root "$label.stderr.log"
                $process = Start-OwnedProcess -FilePath $aria2 -Arguments $arguments -Label $label
                $taskFiles.Add([pscustomobject]@{
                    Id = [string]$process.Id; Name = $filename; Path = $outputPath
                    Process = $process; Stdout = $stdout; Stderr = $stderr
                }) | Out-Null
            }

            $allActive = $false
            $ready = $null
            for ($attempt = 0; $attempt -lt 300; $attempt++) {
                $ready = Get-HttpStats -BaseUri $baseUri
                if ([int]$ready.active_distinct_tasks -ge $jobs) {
                    $allActive = $true
                    break
                }
                foreach ($task in $taskFiles) {
                    $task.Process.Refresh()
                    if ($task.Process.HasExited) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $ready -Tasks @($taskFiles) -Reason 'aria2 task exited before the real-socket overlap gate.'
                        throw "aria2 task exited before $jobs distinct HTTP tasks were active."
                    }
                }
                Start-Sleep -Milliseconds 250
            }
            if (-not $allActive) {
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $ready -Tasks @($taskFiles) -Reason 'Timed out waiting for all distinct task IDs to transfer concurrently.'
                throw "Only $($ready.active_distinct_tasks) of $jobs aria2 tasks became active."
            }
            if ($jobs -eq 16 -and -not [bool]$ready.gate_released) {
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $ready -Tasks @($taskFiles) -Reason 'The 16-task fixture admission gate was not released.'
                throw '16 aria2 task IDs were seen but the strict data-transfer gate did not release.'
            }

            $metricPath = Join-Path $metricFolder "$scenario-round-$round.csv"
            & $sampler -RootProcessName 'aria2c' -Scenario $scenario -SourceSha $SourceSha -HarnessSha $HarnessSha -OutputCsv $metricPath -DurationSeconds $SampleSeconds -WarmupSeconds 1 -SampleIntervalMs 1000 -RunLabel "round-$round" | Out-Null
            if (-not (Test-Path -LiteralPath $metricPath -PathType Leaf)) { throw "Missing aria2 process-tree measurements: $scenario round $round" }
            $metricRows = @(Import-Csv -LiteralPath $metricPath)
            if ($metricRows.Count -lt 5 -or @($metricRows | Where-Object { [int]$_.ProcessCount -lt 1 }).Count -gt 0) {
                throw "aria2 process-tree measurements are incomplete for $scenario round $round."
            }
            $during = Get-HttpStats -BaseUri $baseUri
            if ([int]$during.peak_distinct_tasks -lt $jobs -or [int]$during.active_distinct_tasks -lt $jobs) {
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $during -Tasks @($taskFiles) -Reason 'Distinct live tasks dropped during the resource sampling window.'
                throw "$scenario did not keep all $jobs aria2 tasks active during the resource window."
            }

            $deadline = [DateTime]::UtcNow.AddSeconds($CompletionTimeoutSeconds)
            while ([DateTime]::UtcNow -lt $deadline) {
                $pending = @()
                foreach ($task in $taskFiles) {
                    $task.Process.Refresh()
                    if (-not $task.Process.HasExited) { $pending += $task }
                }
                if ($pending.Count -eq 0) { break }
                Start-Sleep -Milliseconds 500
            }
            $pending = @()
            foreach ($task in $taskFiles) {
                $task.Process.Refresh()
                if (-not $task.Process.HasExited) { $pending += $task }
            }
            if ($pending.Count -gt 0) {
                foreach ($task in $pending) { Stop-Process -Id $task.Process.Id -Force -ErrorAction SilentlyContinue }
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats (Get-HttpStats -BaseUri $baseUri) -Tasks @($taskFiles) -Reason 'Completion timeout.'
                throw "aria2 $scenario round $round exceeded $CompletionTimeoutSeconds seconds."
            }
            foreach ($task in $taskFiles) {
                if ([int]$task.Process.ExitCode -ne 0) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats (Get-HttpStats -BaseUri $baseUri) -Tasks @($taskFiles) -Reason "aria2 exit code $($task.Process.ExitCode)."
                    throw "aria2 task $($task.Name) exited with code $($task.Process.ExitCode)."
                }
            }

            # aria2 may exit after consuming the final byte while the loopback
            # server threads are still unwinding their final writes. Do not sample
            # final counters until every real GET handler has settled.
            $after = Wait-HttpIdle -BaseUri $baseUri -TimeoutSeconds 20
            if ([int]$after.active_requests -ne 0 -or [int]$after.active_distinct_tasks -ne 0) {
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason 'Loopback GET handlers did not settle after aria2 exited.'
                throw "HTTP fixture still has $($after.active_requests) active GET handler(s) after aria2 $scenario round $round."
            }
            if ([int]$after.peak_distinct_tasks -lt $jobs -or [int]$after.unique_tasks_seen -ne $jobs) {
                Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason 'Final fixture counters do not prove the requested workload.'
                throw "HTTP fixture counters invalid for aria2 $scenario round $round."
            }
            foreach ($task in $taskFiles) {
                $startedProperty = $after.task_get_started.PSObject.Properties[$task.Name]
                $completedProperty = $after.task_get_completed.PSObject.Properties[$task.Name]
                $interruptedProperty = $after.task_get_interrupted.PSObject.Properties[$task.Name]
                $bytesProperty = $after.task_bytes_sent.PSObject.Properties[$task.Name]
                $rangesProperty = $after.task_ranges.PSObject.Properties[$task.Name]
                $started = if ($null -ne $startedProperty) { [int]$startedProperty.Value } else { 0 }
                $completed = if ($null -ne $completedProperty) { [int]$completedProperty.Value } else { 0 }
                $interrupted = if ($null -ne $interruptedProperty) { [int]$interruptedProperty.Value } else { 0 }
                $bytesSent = if ($null -ne $bytesProperty) { [long]$bytesProperty.Value } else { 0L }
                $ranges = if ($null -ne $rangesProperty) { @($rangesProperty.Value) } else { @() }
                # Exclude tiny metadata probes: only full data Range responses
                # count toward the segmentation acceptance gate.
                $dataRangeRequests = @($ranges | Where-Object { [bool]$_.range -and [bool]$_.data_transfer })
                $completedDataRanges = @($dataRangeRequests | Where-Object { [bool]$_.honored -and [bool]$_.completed })
                $honoredRangeCount = $completedDataRanges.Count
                $minimumCompletedGets = [Math]::Max(1, $Segments - 1)
                if ($started -lt $Segments -or ($completed + $interrupted) -ne $started -or $completed -lt $minimumCompletedGets) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Task GET lifecycle incomplete: started=$started completed=$completed interrupted=$interrupted segments=$Segments."
                    throw "aria2 task $($task.Name) did not complete the required range GET lifecycle."
                }

                # aria2 can cancel an in-flight segment after its peers finish and
                # request only the remaining tail. Treat that as recovered only
                # when a later honored Range response reaches the same end offset.
                # The old blanket $incompleteDataRanges.Count -gt 0 check rejected
                # this valid retry pattern even when the final file was byte-exact.
                $unrecoveredDataRanges = [System.Collections.Generic.List[object]]::new()
                for ($rangeIndex = 0; $rangeIndex -lt $ranges.Count; $rangeIndex++) {
                    $range = $ranges[$rangeIndex]
                    if (-not [bool]$range.range -or -not [bool]$range.data_transfer -or [bool]$range.completed) {
                        continue
                    }
                    if (-not [bool]$range.honored) {
                        $unrecoveredDataRanges.Add($range)
                        continue
                    }
                    $recoveredByTailRetry = $false
                    for ($retryIndex = $rangeIndex + 1; $retryIndex -lt $ranges.Count; $retryIndex++) {
                        $retry = $ranges[$retryIndex]
                        if (
                            [bool]$retry.range -and
                            [bool]$retry.honored -and
                            [bool]$retry.completed -and
                            [long]$retry.start -ge [long]$range.start -and
                            [long]$retry.start -le [long]$range.end -and
                            [long]$retry.end -eq [long]$range.end
                        ) {
                            $recoveredByTailRetry = $true
                            break
                        }
                    }
                    if (-not $recoveredByTailRetry) {
                        $unrecoveredDataRanges.Add($range)
                    }
                }
                if ($unrecoveredDataRanges.Count -gt 0) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Incomplete data Range response(s) lacked a later completed honored tail retry; completed=$($completedDataRanges.Count) unrecovered=$($unrecoveredDataRanges.Count)."
                    throw "aria2 task $($task.Name) had an incomplete data Range without a completed tail retry."
                }

                # Reconcile socket-writer counters against exact byte intervals. aria2 can
                # start an un-ranged response before splitting, and a later same-end
                # tail retry can overlap bytes already accepted by the socket writer.
                # Do not widen a byte threshold blindly: prove complete unique coverage,
                # classify each overlap, cap it at one 64-KiB chunk, and retain final SHA-256.
                $socketWriteSum = [long]0
                $metadataProbeBytes = [long]0
                $metadataProbeCount = 0
                $servedIntervals = [System.Collections.Generic.List[object]]::new()
                foreach ($response in $ranges) {
                    $written = [long]$response.socket_write_bytes
                    if ($written -lt 0) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Negative socket-write count for $($task.Name)."
                        throw "aria2 task $($task.Name) has a negative socket-write count."
                    }
                    $socketWriteSum += $written
                    $isHonoredRange = [bool]$response.range -and [bool]$response.honored
                    $responseStart = if ($isHonoredRange) { [long]$response.start } else { 0L }
                    $responseLength = if ($isHonoredRange) { [long]$response.end - [long]$response.start + 1 } else { $expectedBytes }
                    if ($written -gt $responseLength -or ([bool]$response.completed -and $written -ne $responseLength)) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Socket-write length disagrees with the HTTP response interval for $($task.Name)."
                        throw "aria2 task $($task.Name) has an impossible response byte count."
                    }
                    if (-not [bool]$response.data_transfer) {
                        if ($written -gt 0) {
                            $metadataProbeCount++
                            $metadataProbeBytes += $written
                        }
                        continue
                    }
                    if ($written -gt 0) {
                        $servedIntervals.Add([pscustomobject]@{
                            Start = $responseStart
                            End = $responseStart + $written - 1
                            IsRange = $isHonoredRange
                            IsUnrangedInterrupted = (-not [bool]$response.range -and -not [bool]$response.completed)
                            RequestedStart = $responseStart
                            RequestedEnd = if ($isHonoredRange) { [long]$response.end } else { [long]$expectedBytes - 1 }
                        }) | Out-Null
                    }
                }
                if ($socketWriteSum -ne $bytesSent) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Per-response socket writes total $socketWriteSum but fixture task counter reports $bytesSent."
                    throw "aria2 task $($task.Name) socket-write counters do not reconcile."
                }
                if ($metadataProbeCount -gt 2) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Unexpected metadata-probe count $metadataProbeCount."
                    throw "aria2 task $($task.Name) issued more than two tiny metadata probes."
                }

                $incompleteDataRangeCount = @($ranges | Where-Object {
                    [bool]$_.range -and [bool]$_.honored -and [bool]$_.data_transfer -and -not [bool]$_.completed
                }).Count
                $tailRetryCount = 0
                for ($retryIndex = 0; $retryIndex -lt $ranges.Count; $retryIndex++) {
                    $retry = $ranges[$retryIndex]
                    if (-not [bool]$retry.range -or -not [bool]$retry.honored -or -not [bool]$retry.completed -or -not [bool]$retry.data_transfer) {
                        continue
                    }
                    for ($priorIndex = 0; $priorIndex -lt $retryIndex; $priorIndex++) {
                        $prior = $ranges[$priorIndex]
                        if (-not [bool]$prior.range -or -not [bool]$prior.honored -or -not [bool]$prior.data_transfer) {
                            continue
                        }
                        if ([long]$prior.end -eq [long]$retry.end -and [long]$retry.start -gt [long]$prior.start -and [long]$retry.start -le [long]$prior.end) {
                            $retryOverlap = [long]$prior.end - [long]$retry.start + 1
                            if ($retryOverlap -gt 0 -and $retryOverlap -le 65536) {
                                $tailRetryCount++
                                break
                            }
                        }
                    }
                }
                if ($tailRetryCount -gt ($incompleteDataRangeCount + 1)) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Tail retry count $tailRetryCount exceeds incomplete data ranges $incompleteDataRangeCount plus one bounded completed-response retry."
                    throw "aria2 task $($task.Name) issued too many tail retries."
                }

                $orderedIntervals = @($servedIntervals | Sort-Object -Property Start, End)
                $coveredEnd = [long](-1)
                $coveredBytes = [long]0
                $duplicateBytes = [long]0
                $overlapCount = 0
                $unrangedOverlapCount = 0
                $tailRetryOverlapCount = 0
                foreach ($interval in $orderedIntervals) {
                    if ([long]$interval.Start -gt ($coveredEnd + 1)) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Served HTTP byte intervals leave a gap before offset $($interval.Start)."
                        throw "aria2 task $($task.Name) has a gap in fixture-served byte coverage."
                    }
                    if ([long]$interval.End -ge $expectedBytes) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Served HTTP interval extends past expected payload end $($expectedBytes - 1)."
                        throw "aria2 task $($task.Name) served bytes beyond the expected payload."
                    }
                    $overlap = if ([long]$interval.Start -le $coveredEnd) {
                        [Math]::Min([long]$interval.End, $coveredEnd) - [long]$interval.Start + 1
                    } else { 0L }
                    if ($overlap -gt 65536) {
                        Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "A served interval overlaps prior data by $overlap bytes; maximum permitted overlap is one 64-KiB chunk."
                        throw "aria2 task $($task.Name) has an unbounded HTTP range overlap."
                    }
                    if ($overlap -gt 0) {
                        $priorUnranged = @($orderedIntervals | Where-Object {
                            $_.IsUnrangedInterrupted -and
                            [long]$_.Start -lt [long]$interval.Start -and
                            [long]$_.End -ge [long]$interval.Start
                        }).Count -gt 0
                        $priorTail = @($orderedIntervals | Where-Object {
                            $_.IsRange -and
                            [long]$_.RequestedEnd -eq [long]$interval.RequestedEnd -and
                            [long]$_.RequestedStart -lt [long]$interval.RequestedStart -and
                            [long]$_.End -ge [long]$interval.Start
                        }).Count -gt 0
                        if ($priorUnranged -eq $priorTail) {
                            Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "An overlapping interval is not uniquely attributable to an interrupted un-ranged response or a same-end tail retry."
                            throw "aria2 task $($task.Name) has an unclassified HTTP range overlap."
                        }
                        $overlapCount++
                        $duplicateBytes += $overlap
                        if ($priorUnranged) { $unrangedOverlapCount++ } else { $tailRetryOverlapCount++ }
                    }
                    if ([long]$interval.End -gt $coveredEnd) {
                        $newStart = [Math]::Max([long]$interval.Start, $coveredEnd + 1)
                        $coveredBytes += [long]$interval.End - $newStart + 1
                        $coveredEnd = [long]$interval.End
                    }
                }
                if ($coveredBytes -ne $expectedBytes -or $coveredEnd -ne ($expectedBytes - 1)) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Unique served coverage is $coveredBytes bytes ending at $coveredEnd; expected $expectedBytes bytes."
                    throw "aria2 task $($task.Name) does not prove complete unique fixture coverage."
                }
                $unrangedInterruptedCount = @($ranges | Where-Object {
                    -not [bool]$_.range -and -not [bool]$_.completed -and [bool]$_.data_transfer -and [long]$_.socket_write_bytes -gt 0
                }).Count
                $maxDuplicateBytes = [long]65536 * [long]($unrangedInterruptedCount + $tailRetryCount)
                if (
                    $unrangedOverlapCount -gt $unrangedInterruptedCount -or
                    $tailRetryOverlapCount -gt $tailRetryCount -or
                    $overlapCount -gt ($unrangedInterruptedCount + $tailRetryCount) -or
                    $duplicateBytes -gt $maxDuplicateBytes
                ) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Duplicate bytes $duplicateBytes across $overlapCount overlap(s) exceed bounded interrupted-response/tail-retry accounting $maxDuplicateBytes."
                    throw "aria2 task $($task.Name) has unexplained duplicate socket bytes."
                }
                $reconciledBytes = $coveredBytes + $duplicateBytes + $metadataProbeBytes
                if ($socketWriteSum -ne $reconciledBytes) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Socket writes $socketWriteSum do not reconcile to unique coverage $coveredBytes + overlap $duplicateBytes + metadata probes $metadataProbeBytes."
                    throw "aria2 task $($task.Name) socket bytes do not reconcile to served intervals."
                }
                if ($honoredRangeCount -lt ($Segments - 1)) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Only $honoredRangeCount completed, honored data Range GETs were observed; expected at least $($Segments - 1)."
                    throw "aria2 task $($task.Name) did not prove $Segments-way HTTP segmentation."
                }
            }
            $finishedUtc = [DateTime]::UtcNow
            $elapsed = [math]::Round(($finishedUtc - $transferStartedUtc).TotalSeconds, 3)
            if ($elapsed -le 0) { throw 'Measured aria2 transfer duration must be positive.' }
            $aggregateMiBPerSecond = [math]::Round(($FileSizeMiB * $jobs) / $elapsed, 3)
            foreach ($task in $taskFiles) {
                if (-not (Test-Path -LiteralPath $task.Path -PathType Leaf)) { throw "aria2 output missing: $($task.Name)" }
                $actualBytes = (Get-Item -LiteralPath $task.Path).Length
                $actualHash = (Get-FileHash -LiteralPath $task.Path -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($actualBytes -ne $expectedBytes -or $actualHash -ne $expectedSha) {
                    Write-FailureDiagnostics -Scenario $scenario -Round $round -Stats $after -Tasks @($taskFiles) -Reason "Output size/SHA-256 mismatch for $($task.Name)."
                    throw "aria2 output failed exact size/SHA-256 verification: $($task.Name)"
                }
                $records.Add([pscustomobject]@{
                    SourceSha = $SourceSha; HarnessSha = $HarnessSha; Scenario = $scenario; Round = $round
                    TaskId = $task.Id; OutputName = $task.Name
                    Bytes = $actualBytes; ExpectedBytes = $expectedBytes
                    SHA256 = $actualHash; ExpectedSHA256 = $expectedSha; Verified = $true
                    ElapsedSeconds = $elapsed; AggregateMiBPerSecond = $aggregateMiBPerSecond
                    PeakDistinctTasks = [int]$after.peak_distinct_tasks; PeakHttpConnections = [int]$after.peak_requests
                    Engine = 'aria2'; EngineVersion = '1.37.0'; EngineArchiveSHA256 = $EngineArchiveSha256; Segments = $Segments
                }) | Out-Null
            }
            $records | Export-Csv -LiteralPath (Join-Path $root 'verified-transfers.csv') -NoTypeInformation -Encoding utf8

            $cpuStat = $metricRows | Measure-Object -Property CpuMachinePercent -Average -Maximum
            $wsStat = $metricRows | Measure-Object -Property WorkingSetMiB -Average -Maximum
            $privateStat = $metricRows | Measure-Object -Property PrivateBytesMiB -Average -Maximum
            $summaryRecords.Add([pscustomobject]@{
                SourceSha = $SourceSha; HarnessSha = $HarnessSha
                OS = [string]$metricRows[0].OS; LogicalCpuCount = [int]$metricRows[0].LogicalCpuCount
                Scenario = $scenario; Round = $round; ConcurrentTasks = $jobs
                VerifiedFiles = $taskFiles.Count; Samples = $metricRows.Count
                AverageCpuMachinePercent = [math]::Round([double]$cpuStat.Average, 3)
                PeakCpuMachinePercent = [math]::Round([double]$cpuStat.Maximum, 3)
                AverageWorkingSetMiB = [math]::Round([double]$wsStat.Average, 3)
                PeakWorkingSetMiB = [math]::Round([double]$wsStat.Maximum, 3)
                AveragePrivateBytesMiB = [math]::Round([double]$privateStat.Average, 3)
                PeakPrivateBytesMiB = [math]::Round([double]$privateStat.Maximum, 3)
                PeakDistinctTasks = [int]$after.peak_distinct_tasks
                PeakHttpConnections = [int]$after.peak_requests
            }) | Out-Null
            $summaryRecords | Export-Csv -LiteralPath (Join-Path $root 'round-summary.csv') -NoTypeInformation -Encoding utf8
            Write-Host "PASS: aria2 $scenario round $round; $jobs/$jobs tasks active; bytes=$($jobs * $expectedBytes); SHA256=$expectedSha; throughput=$aggregateMiBPerSecond MiB/s."
        }
    }
    $expectedTasks = $Rounds * ($Workloads | Measure-Object -Sum).Sum
    if ($records.Count -ne $expectedTasks) { throw "Expected $expectedTasks verified aria2 transfers; found $($records.Count)." }
    if ($summaryRecords.Count -ne ($Rounds * $Workloads.Count)) { throw 'aria2 round summary is incomplete.' }
    $metadata = [ordered]@{
        Engine = 'aria2'; Version = '1.37.0'; SourceSha = $SourceSha; HarnessSha = $HarnessSha
        EngineArchiveSHA256 = $EngineArchiveSha256; BitTorrentEnabledInStockBinary = $bitTorrentEnabled
        TorrentWorkloadInvoked = $false; RpcEnabled = $false; FileSizeMiB = $FileSizeMiB
        FixtureSHA256 = $expectedSha; SegmentsPerTask = $Segments; Workloads = @($Workloads)
        RoundStart = $RoundStart; Rounds = $Rounds; ServerChunkDelayMs = $ServerChunkDelayMs
        TaskChunkDelayMs = 180; VerifiedTransfers = $records.Count
    }
    $metadata | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'matrix-metadata.json') -Encoding utf8
    $completedSuccessfully = $true
    Write-Host "SUCCESS: $($records.Count) aria2 downloads verified; $($summaryRecords.Count) CPU/RAM measurement sets."
} finally {
    foreach ($process in $script:OwnedProcesses) {
        try {
            $process.Refresh()
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        } catch { }
    }
    foreach ($name in $proxyNames) {
        if ($originalProxyValues.ContainsKey($name)) {
            Set-Item -Path "Env:$name" -Value $originalProxyValues[$name]
        }
    }
    if ($completedSuccessfully) {
        # Payloads are test-owned. Their byte counts and hashes remain in the manifest.
        if (Test-Path -LiteralPath $downloadFolder) { Remove-Item -LiteralPath $downloadFolder -Recurse -Force }
        if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Force }
        Write-Host 'Removed verified test payloads after recording exact sizes and SHA-256.'
    }
}
