param(
    [string]$ReportsGlob = "infra/load-test/reports/load-baseline-median-*.md",
    [double]$WarningMultiplier = 1.25,
    [double]$CriticalMultiplier = 1.60,
    [double]$Percentile = 0.90,
    [string]$OutputDir = "infra/load-test/reports",
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

function Parse-ReportField {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$DefaultValue = ""
    )

    $match = [regex]::Match($Content, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($match.Success -and $match.Groups.Count -ge 2) {
        return $match.Groups[1].Value.Trim()
    }
    return $DefaultValue
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

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$calibrationScript = Join-Path $scriptDir "calibrate_feed_thresholds.ps1"
if (-not (Test-Path $calibrationScript)) {
    throw "Missing dependency script: $calibrationScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$beforeCalibration = Get-LatestReport -Directory $resolvedOutputDir -Pattern "feed-threshold-calibration-*.md"
try {
    & $calibrationScript `
        -ReportsGlob $ReportsGlob `
        -WarningMultiplier $WarningMultiplier `
        -CriticalMultiplier $CriticalMultiplier `
        -Percentile $Percentile | Out-Null
    $calibrationDetail = "ok"
} catch {
    $calibrationDetail = $_.Exception.Message
}
$calibrationReport = Get-LatestReport -Directory $resolvedOutputDir -Pattern "feed-threshold-calibration-*.md"
if ($calibrationDetail -ne "ok" -and $calibrationReport -eq $beforeCalibration) {
    $calibrationReport = ""
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "feed-latency-recalibration-checklist-$timestamp.md"

$overallStatus = "FAILED"
$detail = "report_not_found"
$warningP95 = ""
$warningP99 = ""
$criticalP99 = ""
$reportsParsed = ""

if (-not [string]::IsNullOrWhiteSpace($calibrationReport) -and (Test-Path $calibrationReport)) {
    $content = Get-Content -Path $calibrationReport -Raw
    $warningP95 = Parse-ReportField -Content $content -Pattern "APP_FEED_OBSERVABILITY_WARNING_P95_MS=([0-9]+(?:\.[0-9]+)?)"
    $warningP99 = Parse-ReportField -Content $content -Pattern "APP_FEED_OBSERVABILITY_WARNING_P99_MS=([0-9]+(?:\.[0-9]+)?)"
    $criticalP99 = Parse-ReportField -Content $content -Pattern "APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=([0-9]+(?:\.[0-9]+)?)"
    $reportsParsed = Parse-ReportField -Content $content -Pattern "- Reports parsed:\s+([0-9]+)" -DefaultValue "0"

    if (-not [string]::IsNullOrWhiteSpace($warningP95) -and
        -not [string]::IsNullOrWhiteSpace($warningP99) -and
        -not [string]::IsNullOrWhiteSpace($criticalP99)) {
        $parsedStatus = Parse-MarkdownStatus -ReportPath $calibrationReport
        $overallStatus = if ([string]::IsNullOrWhiteSpace($parsedStatus)) { "PASSED" } else { $parsedStatus }
        $detail = $calibrationDetail
    } else {
        $detail = "calibration_values_missing"
    }
}

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Feed Latency Recalibration Checklist")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("- Reports glob: $ReportsGlob")
$lines.Add("- Warning multiplier: $WarningMultiplier")
$lines.Add("- Critical multiplier: $CriticalMultiplier")
$lines.Add("- Percentile: $Percentile")
$lines.Add("")
$lines.Add("| Step | Status | Detail | Report |")
$lines.Add("|---|---|---|---|")
$lines.Add("| Feed threshold calibration | $overallStatus | $detail / reports_parsed=$reportsParsed | $(if ([string]::IsNullOrWhiteSpace($calibrationReport)) { 'n/a' } else { $calibrationReport.Replace('|','/') }) |")
$lines.Add("")
$lines.Add("## Recommended Env Overrides")
$lines.Add("- APP_FEED_OBSERVABILITY_WARNING_P95_MS=$warningP95")
$lines.Add("- APP_FEED_OBSERVABILITY_WARNING_P99_MS=$warningP99")
$lines.Add("- APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=$criticalP99")
$lines.Add("")
$lines.Add("## Notes")
$lines.Add("- Use this checklist after collecting a meaningful telemetry window or refreshed median reports.")
$lines.Add("- This wrapper narrows the operator flow to one command and one summary report; it does not replace product judgement on rollout timing.")

$lines | Set-Content -Path $summaryPath

Write-Host "Feed latency recalibration checklist report created: $summaryPath"
Write-Host "Status: $overallStatus | Detail: $detail"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
    exit 1
}
