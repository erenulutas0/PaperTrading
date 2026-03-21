param(
    [string]$BaseUrl = "http://localhost:8080",
    [int[]]$FanoutStages = @(1000, 5000, 10000),
    [int]$SeedEvents = 200,
    [int]$Concurrency = 8,
    [int]$RequestsPerWorker = 120,
    [int]$Rounds = 3,
    [string]$ReportsGlob = "infra/load-test/reports/load-baseline-median-*.md",
    [double]$WarningMultiplier = 1.25,
    [double]$CriticalMultiplier = 1.60,
    [double]$Percentile = 0.90,
    [string]$OutputDir = "infra/load-test/reports",
    [switch]$SkipFanoutSuite,
    [switch]$SkipRecalibration,
    [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-LatestReport {
    param(
        [string]$Directory,
        [string]$Pattern
    )

    $report = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $report) {
        return ""
    }
    return $report.FullName
}

function Add-StepResult {
    param(
        [System.Collections.Generic.List[object]]$Results,
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$ReportPath = ""
    )

    $Results.Add([pscustomobject]@{
            Name = $Name
            Status = $Status
            Detail = $Detail
            ReportPath = $ReportPath
        }) | Out-Null
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$fanoutScript = Join-Path $scriptDir "run_follower_fanout_stress_suite.ps1"
$recalibrationScript = Join-Path $scriptDir "run_feed_latency_recalibration_checklist.ps1"

if (-not $SkipFanoutSuite -and -not (Test-Path $fanoutScript)) {
    throw "Missing dependency script: $fanoutScript"
}
if (-not $SkipRecalibration -and -not (Test-Path $recalibrationScript)) {
    throw "Missing dependency script: $recalibrationScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipFanoutSuite) {
    $beforeFanout = Get-LatestReport -Directory $resolvedOutputDir -Pattern "fanout-stress-suite-*.md"
    $fanoutArgs = @(
        "-ExecutionPolicy", "Bypass",
        "-File", $fanoutScript,
        "-BaseUrl", $BaseUrl,
        "-FanoutStages"
    ) + (@($FanoutStages) | ForEach-Object { [string]$_ }) + @(
        "-SeedEvents", [string]$SeedEvents,
        "-Concurrency", [string]$Concurrency,
        "-RequestsPerWorker", [string]$RequestsPerWorker,
        "-Rounds", [string]$Rounds
    )
    if ($NoFail) {
        $fanoutArgs += "-NoFail"
    }
    & powershell @fanoutArgs | Out-Null
    $fanoutExitCode = $LASTEXITCODE
    $fanoutReport = Get-LatestReport -Directory $resolvedOutputDir -Pattern "fanout-stress-suite-*.md"
    if ($fanoutReport -eq $beforeFanout) {
        $fanoutReport = ""
    }
    $fanoutStatus = if ($fanoutExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($fanoutReport)) { "PASSED" } else { "FAILED" }
    Add-StepResult -Results $results -Name "Follower Fanout Suite" -Status $fanoutStatus -Detail "exit_code=$fanoutExitCode" -ReportPath $fanoutReport
}

if (-not $SkipRecalibration) {
    $beforeRecalibration = Get-LatestReport -Directory $resolvedOutputDir -Pattern "feed-latency-recalibration-checklist-*.md"
    $recalibrationArgs = @(
        "-ExecutionPolicy", "Bypass",
        "-File", $recalibrationScript,
        "-ReportsGlob", $ReportsGlob,
        "-WarningMultiplier", [string]$WarningMultiplier,
        "-CriticalMultiplier", [string]$CriticalMultiplier,
        "-Percentile", [string]$Percentile
    )
    if ($NoFail) {
        $recalibrationArgs += "-NoFail"
    }
    & powershell @recalibrationArgs | Out-Null
    $recalibrationExitCode = $LASTEXITCODE
    $recalibrationReport = Get-LatestReport -Directory $resolvedOutputDir -Pattern "feed-latency-recalibration-checklist-*.md"
    if ($recalibrationReport -eq $beforeRecalibration) {
        $recalibrationReport = ""
    }
    $recalibrationStatus = if ($recalibrationExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($recalibrationReport)) { "PASSED" } else { "FAILED" }
    Add-StepResult -Results $results -Name "Feed Threshold Recalibration" -Status $recalibrationStatus -Detail "exit_code=$recalibrationExitCode" -ReportPath $recalibrationReport
}

$failedCount = (@($results | Where-Object { $_.Status -ne "PASSED" })).Count
$overallStatus = if ($failedCount -eq 0) { "PASSED" } else { "FAILED" }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "feed-scale-validation-suite-$timestamp.md"

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Feed Scale Validation Suite")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("- Fanout stages: $($FanoutStages -join ' -> ')")
$lines.Add("- Seed events: $SeedEvents")
$lines.Add("- Concurrency: $Concurrency")
$lines.Add("- Requests per worker: $RequestsPerWorker")
$lines.Add("- Rounds: $Rounds")
$lines.Add("- Reports glob: $ReportsGlob")
$lines.Add("")
$lines.Add("| Step | Status | Detail | Report |")
$lines.Add("|---|---|---|---|")
foreach ($result in $results) {
    $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
    $lines.Add("| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |")
}
$lines.Add("")
$lines.Add("## Notes")
$lines.Add("- This suite narrows feed-scale validation to one command and one summary report.")
$lines.Add("- The follower fanout suite captures median ladder behavior; the recalibration checklist turns the resulting median history into recommended `APP_FEED_OBSERVABILITY_*` overrides.")

$lines | Set-Content -Path $summaryPath

Write-Host "Feed scale validation suite report created: $summaryPath"
Write-Host "Status: $overallStatus | FailedSteps: $failedCount"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
    exit 1
}
