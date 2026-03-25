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
    [switch]$SkipScaleChecklist,
    [switch]$SkipTelemetryRecalibration,
    [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-LatestReportInfo {
    param(
        [string]$Directory,
        [string]$Pattern
    )

    $report = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $report) {
        return $null
    }
    return [pscustomobject]@{
        Path          = $report.FullName
        LastWriteTime = $report.LastWriteTimeUtc
    }
}

function Parse-MarkdownStatus {
    param([string]$ReportPath)

    if (-not (Test-Path $ReportPath)) {
        return ""
    }

    $content = Get-Content -Path $ReportPath -Raw
    $patternFlags = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline
    $match = [regex]::Match($content, "^\s*-\s+Status:\s+\*\*(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\*\*\s*$", $patternFlags)
    if ($match.Success -and $match.Groups.Count -ge 2) {
        return $match.Groups[1].Value.Trim()
    }

    $lineMatch = [regex]::Match($content, "^\s*-\s+Status:\s+(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\b", $patternFlags)
    if ($lineMatch.Success -and $lineMatch.Groups.Count -ge 2) {
        return $lineMatch.Groups[1].Value.Trim()
    }

    return ""
}

function New-StepResult {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$ReportPath = ""
    )

    return [pscustomobject]@{
        Name       = $Name
        Status     = $Status
        Detail     = $Detail
        ReportPath = $ReportPath
    }
}

function Invoke-ScriptStep {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [hashtable]$Parameters,
        [string]$ReportPattern,
        [string]$ReportsDirectory
    )

    $before = Get-LatestReportInfo -Directory $ReportsDirectory -Pattern $ReportPattern
    try {
        & $ScriptPath @Parameters | Out-Null
        $reportInfo = Get-LatestReportInfo -Directory $ReportsDirectory -Pattern $ReportPattern
        $report = if ($null -eq $reportInfo) { "" } else { $reportInfo.Path }
        if ([string]::IsNullOrWhiteSpace($report)) {
            return New-StepResult -Name $Name -Status "FAILED" -Detail "report_not_found" -ReportPath ""
        }

        $status = Parse-MarkdownStatus -ReportPath $report
        if ([string]::IsNullOrWhiteSpace($status)) {
            $status = "PASSED"
        }

        return New-StepResult -Name $Name -Status $status -Detail "ok" -ReportPath $report
    } catch {
        $reportInfo = Get-LatestReportInfo -Directory $ReportsDirectory -Pattern $ReportPattern
        if ($null -ne $reportInfo -and
            $null -ne $before -and
            $reportInfo.Path -eq $before.Path -and
            $reportInfo.LastWriteTime -le $before.LastWriteTime) {
            $reportInfo = $null
        }
        $report = if ($null -eq $reportInfo) { "" } else { $reportInfo.Path }
        return New-StepResult -Name $Name -Status "FAILED" -Detail $_.Exception.Message -ReportPath $report
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scaleScript = Join-Path $scriptDir "run_feed_scale_staging_checklist.ps1"
$recalibrationScript = Join-Path $scriptDir "run_feed_latency_recalibration_checklist.ps1"

if (-not $SkipScaleChecklist -and -not (Test-Path $scaleScript)) {
    throw "Missing dependency script: $scaleScript"
}
if (-not $SkipTelemetryRecalibration -and -not (Test-Path $recalibrationScript)) {
    throw "Missing dependency script: $recalibrationScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipScaleChecklist) {
    $scaleParams = @{
        BaseUrl           = $BaseUrl
        FanoutStages      = @($FanoutStages)
        SeedEvents        = $SeedEvents
        Concurrency       = $Concurrency
        RequestsPerWorker = $RequestsPerWorker
        Rounds            = $Rounds
        ReportsGlob       = $ReportsGlob
        WarningMultiplier = $WarningMultiplier
        CriticalMultiplier = $CriticalMultiplier
        Percentile        = $Percentile
        OutputDir         = $OutputDir
        NoFail            = $true
    }

    $results.Add((Invoke-ScriptStep `
            -Name "Feed Scale Staging Checklist" `
            -ScriptPath $scaleScript `
            -Parameters $scaleParams `
            -ReportPattern "feed-scale-staging-checklist-*.md" `
            -ReportsDirectory $resolvedOutputDir)) | Out-Null
}

if (-not $SkipTelemetryRecalibration) {
    $recalibrationParams = @{
        ReportsGlob        = $ReportsGlob
        WarningMultiplier  = $WarningMultiplier
        CriticalMultiplier = $CriticalMultiplier
        Percentile         = $Percentile
        OutputDir          = $OutputDir
        NoFail             = $true
    }

    $results.Add((Invoke-ScriptStep `
            -Name "Feed Telemetry Recalibration" `
            -ScriptPath $recalibrationScript `
            -Parameters $recalibrationParams `
            -ReportPattern "feed-latency-recalibration-checklist-*.md" `
            -ReportsDirectory $resolvedOutputDir)) | Out-Null
}

$failedCount = (@($results | Where-Object { $_.Status -ne "PASSED" })).Count
$overallStatus = if ($results.Count -gt 0 -and $failedCount -eq 0) { "PASSED" } else { "FAILED" }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "feed-observability-rollout-checklist-$timestamp.md"

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Feed Observability Rollout Checklist")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("- Fanout stages: $($FanoutStages -join ' -> ')")
$lines.Add("- Reports glob: $ReportsGlob")
$lines.Add("")
$lines.Add("Coverage:")
$lines.Add("- feed-scale staging evidence with the high-follower ladder")
$lines.Add("- linked threshold guidance from the same or newer telemetry history")
$lines.Add("- one parent report for the remaining feed observability backlog")
$lines.Add("")
$lines.Add("| Step | Status | Detail | Report |")
$lines.Add("|---|---|---|---|")
foreach ($result in $results) {
    $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
    $lines.Add("| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |")
}
$lines.Add("")
$lines.Add("## Notes")
$lines.Add("- Use the scale checklist against staging with the `1000 -> 5000 -> 10000` follower ladder unless you have a better production-like profile.")
$lines.Add("- Re-run the telemetry recalibration step after one sprint of production reports or other meaningful median history.")
$lines.Add("- `-SkipScaleChecklist` or `-SkipTelemetryRecalibration` can narrow the parent wrapper for partial phases.")

$lines | Set-Content -Path $summaryPath

Write-Host "Feed observability rollout checklist report created: $summaryPath"
Write-Host "Status: $overallStatus | FailedSteps: $failedCount"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
    exit 1
}
