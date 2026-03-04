param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Iterations = 12,
  [int]$IntervalSec = 20,
  [int]$RequestTimeoutSec = 15,
  [double]$WarningCountMultiplier = 1.25,
  [double]$CriticalCountMultiplier = 1.75,
  [double]$WarningRatioMultiplier = 1.20,
  [double]$CriticalRatioMultiplier = 1.60,
  [int]$MinSamplesFloor = 20,
  [switch]$FailOnCritical,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-JsonGet {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  return Invoke-RestMethod -Method Get -Uri $Url -TimeoutSec $TimeoutSec -ErrorAction Stop
}

function To-DoubleSafe {
  param([object]$Value)
  if ($null -eq $Value) {
    return 0.0
  }
  try {
    return [double]$Value
  } catch {
    return 0.0
  }
}

function To-IntSafe {
  param([object]$Value)
  if ($null -eq $Value) {
    return 0
  }
  try {
    return [int]$Value
  } catch {
    return 0
  }
}

function Get-Percentile {
  param(
    [double[]]$Values,
    [double]$Percentile
  )

  if ($null -eq $Values -or $Values.Count -eq 0) {
    return 0.0
  }

  $sorted = $Values | Sort-Object
  $rank = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
  if ($rank -lt 0) { $rank = 0 }
  if ($rank -ge $sorted.Count) { $rank = $sorted.Count - 1 }
  return [double]$sorted[$rank]
}

function Clamp-Ratio {
  param([double]$Value)
  if ([double]::IsNaN($Value) -or [double]::IsInfinity($Value)) {
    return 0.0
  }
  if ($Value -lt 0.0) { return 0.0 }
  if ($Value -gt 1.0) { return 1.0 }
  return $Value
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Path $reportsDir -Force
$reportPath = Join-Path $reportsDir "auth-observability-calibration-$timestamp.md"

$endpoint = "$BaseUrl/actuator/authsessions"

$rows = New-Object System.Collections.Generic.List[object]
$errors = New-Object System.Collections.Generic.List[string]
$refreshValues = New-Object System.Collections.Generic.List[double]
$invalidValues = New-Object System.Collections.Generic.List[double]
$totalValues = New-Object System.Collections.Generic.List[double]
$ratioValues = New-Object System.Collections.Generic.List[double]

$warningStates = 0
$criticalStates = 0
$successRuns = 0

$currentThresholds = $null

for ($i = 1; $i -le $Iterations; $i++) {
  $checkedAt = (Get-Date).ToString("s")
  $status = "ok"
  $alertState = "UNKNOWN"
  $windowSeconds = 0
  $refreshSuccessCount = 0
  $invalidRefreshCount = 0
  $totalRefreshAttempts = 0
  $invalidRefreshRatio = 0.0
  $error = ""

  try {
    $snapshot = Invoke-JsonGet -Url $endpoint -TimeoutSec $RequestTimeoutSec
    if ($null -eq $snapshot) {
      $status = "empty-response"
      $error = "empty response from endpoint"
    } else {
      $successRuns++
      $alertState = [string]($snapshot.alertState ?? "UNKNOWN")
      $windowSeconds = To-IntSafe -Value $snapshot.windowSeconds
      $refreshSuccessCount = To-IntSafe -Value $snapshot.refreshSuccessCount
      $invalidRefreshCount = To-IntSafe -Value $snapshot.invalidRefreshCount
      $totalRefreshAttempts = To-IntSafe -Value $snapshot.totalRefreshAttempts
      $invalidRefreshRatio = To-DoubleSafe -Value $snapshot.invalidRefreshRatio

      if ($null -eq $currentThresholds) {
        $currentThresholds = [pscustomobject]@{
          minSamples           = To-IntSafe -Value $snapshot.minSamples
          warningRefreshCount  = To-IntSafe -Value $snapshot.warningRefreshCount
          criticalRefreshCount = To-IntSafe -Value $snapshot.criticalRefreshCount
          warningInvalidCount  = To-IntSafe -Value $snapshot.warningInvalidCount
          criticalInvalidCount = To-IntSafe -Value $snapshot.criticalInvalidCount
          warningInvalidRatio  = To-DoubleSafe -Value $snapshot.warningInvalidRatio
          criticalInvalidRatio = To-DoubleSafe -Value $snapshot.criticalInvalidRatio
        }
      }

      $refreshValues.Add($refreshSuccessCount)
      $invalidValues.Add($invalidRefreshCount)
      $totalValues.Add($totalRefreshAttempts)
      $ratioValues.Add($invalidRefreshRatio)

      if ($alertState -eq "WARNING") {
        $warningStates++
      } elseif ($alertState -eq "CRITICAL") {
        $criticalStates++
      }
    }
  } catch {
    $status = "error"
    $error = $_.Exception.Message
    $errors.Add("Iteration $i failed: $error")
  }

  $rows.Add([pscustomobject]@{
      run                  = $i
      checkedAt            = $checkedAt
      status               = $status
      alertState           = $alertState
      windowSeconds        = $windowSeconds
      refreshSuccessCount  = $refreshSuccessCount
      invalidRefreshCount  = $invalidRefreshCount
      totalRefreshAttempts = $totalRefreshAttempts
      invalidRefreshRatio  = [Math]::Round($invalidRefreshRatio, 4)
      error                = $error
    })

  if ($i -lt $Iterations -and $IntervalSec -gt 0) {
    Start-Sleep -Seconds $IntervalSec
  }
}

$p95Refresh = 0.0
$p95Invalid = 0.0
$p95Total = 0.0
$p95Ratio = 0.0
$recommendedWarningRefresh = $null
$recommendedCriticalRefresh = $null
$recommendedWarningInvalidCount = $null
$recommendedCriticalInvalidCount = $null
$recommendedWarningInvalidRatio = $null
$recommendedCriticalInvalidRatio = $null
$recommendedMinSamples = $null

if ($successRuns -gt 0) {
  $p95Refresh = Get-Percentile -Values $refreshValues.ToArray() -Percentile 95
  $p95Invalid = Get-Percentile -Values $invalidValues.ToArray() -Percentile 95
  $p95Total = Get-Percentile -Values $totalValues.ToArray() -Percentile 95
  $p95Ratio = Get-Percentile -Values $ratioValues.ToArray() -Percentile 95

  $recommendedWarningRefresh = [Math]::Max(1, [int][Math]::Ceiling($p95Refresh * $WarningCountMultiplier))
  $recommendedCriticalRefresh = [Math]::Max($recommendedWarningRefresh, [int][Math]::Ceiling($p95Refresh * $CriticalCountMultiplier))
  $recommendedWarningInvalidCount = [Math]::Max(1, [int][Math]::Ceiling($p95Invalid * $WarningCountMultiplier))
  $recommendedCriticalInvalidCount = [Math]::Max($recommendedWarningInvalidCount, [int][Math]::Ceiling($p95Invalid * $CriticalCountMultiplier))
  $recommendedWarningInvalidRatio = [Math]::Round((Clamp-Ratio -Value ($p95Ratio * $WarningRatioMultiplier)), 4)
  $recommendedCriticalInvalidRatio = [Math]::Round((Clamp-Ratio -Value ($p95Ratio * $CriticalRatioMultiplier)), 4)
  if ($recommendedCriticalInvalidRatio -lt $recommendedWarningInvalidRatio) {
    $recommendedCriticalInvalidRatio = $recommendedWarningInvalidRatio
  }
  $recommendedMinSamples = [Math]::Max($MinSamplesFloor, [int][Math]::Ceiling($p95Total * 0.5))
}

if ($successRuns -eq 0) {
  $status = "UNAVAILABLE"
} elseif ($criticalStates -gt 0) {
  $status = "OBSERVE_CRITICAL"
} elseif ($warningStates -gt 0) {
  $status = "OBSERVE_WARNING"
} else {
  $status = "STABLE"
}

$rowsMarkdown = @()
$rowsMarkdown += "| Run | CheckedAt | Status | AlertState | WindowSec | RefreshSuccess | InvalidRefresh | TotalAttempts | InvalidRatio | Error |"
$rowsMarkdown += "|-----|-----------|--------|------------|-----------|----------------|----------------|---------------|--------------|-------|"
foreach ($row in $rows) {
  $rowsMarkdown += "| $($row.run) | $($row.checkedAt) | $($row.status) | $($row.alertState) | $($row.windowSeconds) | $($row.refreshSuccessCount) | $($row.invalidRefreshCount) | $($row.totalRefreshAttempts) | $($row.invalidRefreshRatio) | $($row.error) |"
}

$notesBlock = if ($errors.Count -eq 0) {
  "- none"
} else {
  ($errors | ForEach-Object { "- $_" }) -join [Environment]::NewLine
}

$currentThresholdLines = @()
if ($null -eq $currentThresholds) {
  $currentThresholdLines += "- unavailable"
} else {
  $currentThresholdLines += "- minSamples: $($currentThresholds.minSamples)"
  $currentThresholdLines += "- warningRefreshCount: $($currentThresholds.warningRefreshCount)"
  $currentThresholdLines += "- criticalRefreshCount: $($currentThresholds.criticalRefreshCount)"
  $currentThresholdLines += "- warningInvalidCount: $($currentThresholds.warningInvalidCount)"
  $currentThresholdLines += "- criticalInvalidCount: $($currentThresholds.criticalInvalidCount)"
  $currentThresholdLines += "- warningInvalidRatio: $([Math]::Round($currentThresholds.warningInvalidRatio, 4))"
  $currentThresholdLines += "- criticalInvalidRatio: $([Math]::Round($currentThresholds.criticalInvalidRatio, 4))"
}

$baselineLines = @()
if ($successRuns -eq 0) {
  $baselineLines += "- unavailable"
} else {
  $baselineLines += "- p95 refreshSuccessCount: $([Math]::Round($p95Refresh, 2))"
  $baselineLines += "- p95 invalidRefreshCount: $([Math]::Round($p95Invalid, 2))"
  $baselineLines += "- p95 totalRefreshAttempts: $([Math]::Round($p95Total, 2))"
  $baselineLines += "- p95 invalidRefreshRatio: $([Math]::Round($p95Ratio, 4))"
}

$recommendedLines = @()
if ($successRuns -eq 0) {
  $recommendedLines += "- unavailable"
} else {
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_MIN_SAMPLES=$recommendedMinSamples"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_WARNING_REFRESH_COUNT=$recommendedWarningRefresh"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_CRITICAL_REFRESH_COUNT=$recommendedCriticalRefresh"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_WARNING_INVALID_COUNT=$recommendedWarningInvalidCount"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_CRITICAL_INVALID_COUNT=$recommendedCriticalInvalidCount"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_WARNING_INVALID_RATIO=$recommendedWarningInvalidRatio"
  $recommendedLines += "- APP_AUTH_OBSERVABILITY_CRITICAL_INVALID_RATIO=$recommendedCriticalInvalidRatio"
}

$reportLines = @(
  "# Auth Observability Threshold Calibration",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- Endpoint: $endpoint",
  "- Iterations: $Iterations",
  "- Interval seconds: $IntervalSec",
  "- Request timeout seconds: $RequestTimeoutSec",
  "- Warning count multiplier: $WarningCountMultiplier",
  "- Critical count multiplier: $CriticalCountMultiplier",
  "- Warning ratio multiplier: $WarningRatioMultiplier",
  "- Critical ratio multiplier: $CriticalRatioMultiplier",
  "- Min samples floor: $MinSamplesFloor",
  "",
  "## Result",
  "- Status: **$status**",
  "- Successful samples: $successRuns / $Iterations",
  "- WARNING state samples: $warningStates",
  "- CRITICAL state samples: $criticalStates",
  "",
  "## Current Runtime Thresholds",
  ($currentThresholdLines -join [Environment]::NewLine),
  "",
  "## P95 Baseline (Sampled Window Values)",
  ($baselineLines -join [Environment]::NewLine),
  "",
  "## Recommended Env Overrides",
  ($recommendedLines -join [Environment]::NewLine),
  "",
  "## Run Details",
  ($rowsMarkdown -join [Environment]::NewLine),
  "",
  "## Notes",
  $notesBlock
)

$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "Auth observability calibration report created: $reportPath"
Write-Output "Status: $status | Samples: $successRuns/$Iterations | WARNING: $warningStates | CRITICAL: $criticalStates"

if ($status -eq "UNAVAILABLE" -and -not $NoFail) {
  exit 2
}
if ($FailOnCritical -and $criticalStates -gt 0 -and -not $NoFail) {
  exit 1
}
