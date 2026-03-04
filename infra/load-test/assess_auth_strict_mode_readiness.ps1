param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$RequestTimeoutSec = 15,
  [double]$MaxLegacyAccepted = 0,
  [int]$CalibrationIterations = 12,
  [int]$CalibrationIntervalSec = 20,
  [switch]$FailOnNotReady,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function To-DoubleSafe {
  param([string]$Value)
  if ([string]::IsNullOrWhiteSpace($Value)) {
    return 0.0
  }
  try {
    return [double]$Value
  } catch {
    return 0.0
  }
}

function To-IntSafe {
  param([string]$Value)
  if ([string]::IsNullOrWhiteSpace($Value)) {
    return 0
  }
  try {
    return [int]$Value
  } catch {
    return 0
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$legacyScript = Join-Path $scriptDir "check_auth_legacy_usage.ps1"
$calibrationScript = Join-Path $scriptDir "calibrate_auth_observability_thresholds.ps1"
$reportsDir = Join-Path $scriptDir "reports"
$null = New-Item -ItemType Directory -Path $reportsDir -Force

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $reportsDir "auth-strict-readiness-$timestamp.md"

if (-not (Test-Path $legacyScript)) {
  throw "Missing dependency script: $legacyScript"
}
if (-not (Test-Path $calibrationScript)) {
  throw "Missing dependency script: $calibrationScript"
}

$notes = New-Object System.Collections.Generic.List[string]

Write-Output "Running legacy usage check..."
& $legacyScript `
  -BaseUrl $BaseUrl `
  -RequestTimeoutSec $RequestTimeoutSec `
  -MaxLegacyAccepted $MaxLegacyAccepted `
  -NoFail:$true | Out-Null

$legacyReport = Get-ChildItem $reportsDir -Filter "auth-legacy-usage-*.md" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if ($null -eq $legacyReport) {
  throw "Legacy usage report was not generated."
}

$legacyContent = Get-Content $legacyReport.FullName -Raw
$legacyStatus = Parse-ReportField -Content $legacyContent -Pattern "- Status:\s+\*\*(.+?)\*\*" -DefaultValue "UNAVAILABLE"
$legacyAcceptedValue = Parse-ReportField -Content $legacyContent -Pattern "Legacy accepted .*:\s+([0-9.]+)" -DefaultValue "0"
$legacyAccepted = To-DoubleSafe -Value $legacyAcceptedValue

Write-Output "Running auth observability calibration..."
& $calibrationScript `
  -BaseUrl $BaseUrl `
  -Iterations $CalibrationIterations `
  -IntervalSec $CalibrationIntervalSec `
  -RequestTimeoutSec $RequestTimeoutSec `
  -NoFail:$true | Out-Null

$calibrationReport = Get-ChildItem $reportsDir -Filter "auth-observability-calibration-*.md" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if ($null -eq $calibrationReport) {
  throw "Auth observability calibration report was not generated."
}

$calibrationContent = Get-Content $calibrationReport.FullName -Raw
$calibrationStatus = Parse-ReportField -Content $calibrationContent -Pattern "- Status:\s+\*\*(.+?)\*\*" -DefaultValue "UNAVAILABLE"
$warningSamples = To-IntSafe -Value (Parse-ReportField -Content $calibrationContent -Pattern "WARNING state samples:\s+([0-9]+)" -DefaultValue "0")
$criticalSamples = To-IntSafe -Value (Parse-ReportField -Content $calibrationContent -Pattern "CRITICAL state samples:\s+([0-9]+)" -DefaultValue "0")

$recommendedOverrides = New-Object System.Collections.Generic.List[string]
$inRecommendations = $false
foreach ($line in ($calibrationContent -split "`r?`n")) {
  if ($line -match "^## Recommended Env Overrides") {
    $inRecommendations = $true
    continue
  }
  if ($inRecommendations -and $line -match "^## ") {
    break
  }
  if ($inRecommendations -and $line -match "^- APP_AUTH_OBSERVABILITY_") {
    $recommendedOverrides.Add($line.Substring(2).Trim())
  }
}

if ($legacyStatus -eq "UNAVAILABLE") {
  $notes.Add("Legacy readiness metric endpoint unavailable.")
}
if ($calibrationStatus -eq "UNAVAILABLE") {
  $notes.Add("Auth observability calibration endpoint unavailable.")
}
if ($criticalSamples -gt 0) {
  $notes.Add("Calibration observed CRITICAL churn samples: $criticalSamples")
}

$readinessStatus = "NOT_READY"
$decisionReason = "Insufficient readiness conditions."

if ($legacyStatus -eq "READY" -and $calibrationStatus -eq "STABLE") {
  $readinessStatus = "READY"
  $decisionReason = "Legacy usage is below threshold and auth churn telemetry is stable."
} elseif ($legacyStatus -eq "READY" -and $calibrationStatus -eq "OBSERVE_WARNING" -and $criticalSamples -eq 0) {
  $readinessStatus = "CONDITIONAL_READY"
  $decisionReason = "Legacy usage is ready; churn has warning-only samples. Tune thresholds before cutover."
} elseif ($legacyStatus -ne "READY") {
  $decisionReason = "Legacy header usage is not below threshold."
} elseif ($criticalSamples -gt 0) {
  $decisionReason = "Critical auth refresh churn samples detected."
} elseif ($calibrationStatus -eq "UNAVAILABLE") {
  $decisionReason = "Auth churn telemetry unavailable."
}

$nextActions = @()
if ($readinessStatus -eq "READY") {
  $nextActions += "- Set `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false` in staging."
  $nextActions += "- Keep `APP_AUTH_ENFORCE_HEADER_TOKEN_MATCH=true`."
  $nextActions += "- Re-run this script after rollout and confirm status remains READY."
} elseif ($readinessStatus -eq "CONDITIONAL_READY") {
  $nextActions += "- Apply recommended `APP_AUTH_OBSERVABILITY_*` overrides first."
  $nextActions += "- Re-run calibration and ensure CRITICAL samples remain zero."
  $nextActions += "- Then proceed with strict-mode toggle in staging."
} else {
  $nextActions += "- Resolve blocking status from legacy usage and/or auth churn telemetry."
  $nextActions += "- Re-run `check_auth_legacy_usage.ps1` and `calibrate_auth_observability_thresholds.ps1` after fixes."
}

$notesBlock = if ($notes.Count -eq 0) {
  "- none"
} else {
  ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine
}

$recommendedOverridesBlock = if ($recommendedOverrides.Count -eq 0) {
  "- unavailable"
} else {
  ($recommendedOverrides | ForEach-Object { "- $_" }) -join [Environment]::NewLine
}

$reportLines = @(
  "# Auth Strict-Mode Readiness Assessment",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- Max legacy accepted threshold: $MaxLegacyAccepted",
  "- Calibration iterations: $CalibrationIterations",
  "- Calibration interval seconds: $CalibrationIntervalSec",
  "- Request timeout seconds: $RequestTimeoutSec",
  "",
  "## Inputs",
  "- Legacy report: $($legacyReport.FullName)",
  "- Calibration report: $($calibrationReport.FullName)",
  "",
  "## Decision",
  "- Readiness status: **$readinessStatus**",
  "- Reason: $decisionReason",
  "",
  "## Observed Signals",
  "- Legacy status: $legacyStatus",
  "- Legacy accepted count: $legacyAccepted",
  "- Calibration status: $calibrationStatus",
  "- Calibration warning samples: $warningSamples",
  "- Calibration critical samples: $criticalSamples",
  "",
  "## Recommended Auth Observability Overrides",
  $recommendedOverridesBlock,
  "",
  "## Next Actions",
  ($nextActions -join [Environment]::NewLine),
  "",
  "## Notes",
  $notesBlock
)

$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "Auth strict-mode readiness report created: $reportPath"
Write-Output "Readiness: $readinessStatus | Legacy: $legacyStatus | Calibration: $calibrationStatus"

if ($FailOnNotReady -and $readinessStatus -eq "NOT_READY" -and -not $NoFail) {
  exit 1
}
if (($legacyStatus -eq "UNAVAILABLE" -or $calibrationStatus -eq "UNAVAILABLE") -and -not $NoFail) {
  exit 2
}
