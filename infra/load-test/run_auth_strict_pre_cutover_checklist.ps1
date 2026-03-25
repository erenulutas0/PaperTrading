param(
  [string]$BaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [double]$MaxLegacyAccepted = 0,
  [int]$CalibrationIterations = 12,
  [int]$CalibrationIntervalSec = 20,
  [int]$RequestTimeoutSec = 15,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the auth strict pre-cutover checklist."
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

function Parse-MarkdownField {
  param(
    [string]$ReportPath,
    [string]$Pattern,
    [string]$DefaultValue = ""
  )

  if (-not (Test-Path $ReportPath)) {
    return $DefaultValue
  }

  $content = Get-Content -Path $ReportPath -Raw
  $match = [regex]::Match($content, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
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
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "auth-strict-pre-cutover-checklist-$timestamp.md"
$assessmentScript = Join-Path $scriptDir "assess_auth_strict_mode_readiness.ps1"

if (-not (Test-Path $assessmentScript)) {
  throw "Missing dependency script: $assessmentScript"
}

$beforeAssessment = Get-LatestReport -Directory $ReportsDir -Pattern "auth-strict-readiness-*.md"
try {
  & $assessmentScript `
    -BaseUrl $BaseUrl `
    -RequestTimeoutSec $RequestTimeoutSec `
    -MaxLegacyAccepted $MaxLegacyAccepted `
    -CalibrationIterations $CalibrationIterations `
    -CalibrationIntervalSec $CalibrationIntervalSec `
    -NoFail | Out-Null
  $assessmentDetail = "ok"
} catch {
  $assessmentDetail = $_.Exception.Message
}
$assessmentReport = Get-LatestReport -Directory $ReportsDir -Pattern "auth-strict-readiness-*.md"
if ($assessmentReport -eq $beforeAssessment) {
  $assessmentReport = ""
}

$results = New-Object 'System.Collections.Generic.List[object]'

if ([string]::IsNullOrWhiteSpace($assessmentReport)) {
  $results.Add((New-StepResult -Name "Readiness Assessment" -Status "FAILED" -Detail "report_not_found")) | Out-Null
} else {
  $reportStatus = Parse-MarkdownStatus -ReportPath $assessmentReport
  $readinessStatus = Parse-MarkdownField -ReportPath $assessmentReport -Pattern "Readiness status:\s+\*\*(.+?)\*\*" -DefaultValue "UNKNOWN"
  $legacyStatus = Parse-MarkdownField -ReportPath $assessmentReport -Pattern "Legacy status:\s+(.+)" -DefaultValue "UNKNOWN"
  $calibrationStatus = Parse-MarkdownField -ReportPath $assessmentReport -Pattern "Calibration status:\s+(.+)" -DefaultValue "UNKNOWN"

  if (-not [string]::IsNullOrWhiteSpace($reportStatus)) {
    $assessmentStepStatus = $reportStatus
  } else {
    $assessmentStepStatus = switch ($readinessStatus) {
      "READY" { "PASSED" }
      "CONDITIONAL_READY" { "PASSED" }
      default { "FAILED" }
    }
  }
  $assessmentSummary = "readiness=$readinessStatus, legacy=$legacyStatus, calibration=$calibrationStatus, detail=$assessmentDetail"
  $results.Add((New-StepResult -Name "Readiness Assessment" -Status $assessmentStepStatus -Detail $assessmentSummary -ReportPath $assessmentReport)) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += '# Auth Strict Pre-Cutover Checklist'
$lines += ''
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Max legacy accepted threshold: $MaxLegacyAccepted"
$lines += "- Calibration iterations: $CalibrationIterations"
$lines += "- Calibration interval seconds: $CalibrationIntervalSec"
$lines += ''
$lines += 'Coverage:'
$lines += '- legacy header acceptance readiness'
$lines += '- auth refresh churn calibration'
$lines += '- strict-mode readiness decision before toggling `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false`'
$lines += ''
$lines += '| Step | Status | Detail | Report |'
$lines += '|---|---|---|---|'
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Auth strict pre-cutover checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
