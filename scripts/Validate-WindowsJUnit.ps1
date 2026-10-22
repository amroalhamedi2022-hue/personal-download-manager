<#
.SYNOPSIS
Fails Windows packaging when the expected JUnit XML is absent, skipped,
incomplete, or reports failures/errors.
.DESCRIPTION
Reads test reports produced by the THREE Gradle desktopTest tasks used by the
Windows workflow. A successful compile with no executed tests is not enough.
No network access or user-state changes are performed.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int] $MinimumTests = 150
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$relativeResults = @(
    'downloader/core/build/test-results/desktopTest',
    'downloader/monitor/build/test-results/desktopTest',
    'shared/app/build/test-results/desktopTest'
)

$totals = [ordered]@{
    Suites = 0
    Tests = 0
    Failures = 0
    Errors = 0
    Skipped = 0
}

foreach ($relative in $relativeResults) {
    $folder = Join-Path $repositoryRoot $relative
    if (-not (Test-Path -LiteralPath $folder -PathType Container)) {
        throw "JUnit result folder is missing: $relative"
    }
    $reports = @(Get-ChildItem -LiteralPath $folder -Filter 'TEST-*.xml' -File -Recurse)
    if ($reports.Count -eq 0) {
        throw "No JUnit test XML in: $relative"
    }
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $suites = @($document.SelectNodes('//testsuite'))
        if ($suites.Count -eq 0) {
            throw "Unexpected JUnit XML with no testsuite: $($report.Name)"
        }
        foreach ($suite in $suites) {
            $totals.Suites += 1
            $totals.Tests += [int]$suite.GetAttribute('tests')
            $totals.Failures += [int]$suite.GetAttribute('failures')
            $totals.Errors += [int]$suite.GetAttribute('errors')
            $totals.Skipped += [int]$suite.GetAttribute('skipped')
        }
    }
}

Write-Host "Windows JUnit gate: suites=$($totals.Suites), tests=$($totals.Tests), failures=$($totals.Failures), errors=$($totals.Errors), skipped=$($totals.Skipped), minimum=$MinimumTests"

if ($totals.Tests -lt $MinimumTests) {
    throw "JUnit gate failed: only $($totals.Tests) tests executed; at least $MinimumTests required."
}
if ($totals.Failures -gt 0 -or $totals.Errors -gt 0 -or $totals.Skipped -gt 0) {
    throw "JUnit gate failed: no failed, errored or skipped tests are allowed."
}
Write-Host 'JUnit gate passed: coverage floor met, zero failures/errors/skips.'
