param(
  [string]$ReportsGlob = "infra/load-test/reports/load-baseline-median-*.md",
  [double]$WarningMultiplier = 1.25,
  [double]$CriticalMultiplier = 1.60,
  [double]$Percentile = 0.90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Percentile -lt 0 -or $Percentile -gt 1) {
  throw "Percentile must be in range [0,1]."
}

function Get-MetricValue {
  param(
    [string]$Content,
    [string]$MetricName
  )

  $pattern = "\|\s*$([regex]::Escape($MetricName))\s*\|\s*([0-9]+(?:\.[0-9]+)?)\s*\|"
  $m = [regex]::Match($Content, $pattern)
  if (-not $m.Success) {
    return $null
  }
  return [double]$m.Groups[1].Value
}

function Get-Percentile {
  param(
    [double[]]$Values,
    [double]$Q
  )

  if ($Values.Count -eq 0) {
    return 0.0
  }
  $sorted = $Values | Sort-Object
  $index = [int][math]::Floor(($sorted.Count - 1) * $Q)
  return [double]$sorted[$index]
}

$reportFiles = Get-ChildItem -Path $ReportsGlob | Sort-Object LastWriteTime
if ($reportFiles.Count -eq 0) {
  throw "No reports found for pattern: $ReportsGlob"
}

$rows = @()
foreach ($file in $reportFiles) {
  $content = Get-Content -Raw -Path $file.FullName
  $p95 = Get-MetricValue -Content $content -MetricName "p95_latency_ms"
  $p99 = Get-MetricValue -Content $content -MetricName "p99_latency_ms"
  if ($null -eq $p95 -or $null -eq $p99) {
    continue
  }
  $rows += [pscustomobject]@{
    report = $file.FullName
    p95 = [double]$p95
    p99 = [double]$p99
  }
}

if ($rows.Count -eq 0) {
  throw "No parseable median reports found under pattern: $ReportsGlob"
}

$p95Values = $rows | ForEach-Object { [double]$_.p95 }
$p99Values = $rows | ForEach-Object { [double]$_.p99 }

$baseP95 = Get-Percentile -Values $p95Values -Q $Percentile
$baseP99 = Get-Percentile -Values $p99Values -Q $Percentile

$warningP95 = [math]::Round([math]::Max(10.0, $baseP95 * $WarningMultiplier), 2)
$warningP99 = [math]::Round([math]::Max($warningP95, $baseP99 * $WarningMultiplier), 2)
$criticalP99 = [math]::Round([math]::Max($warningP99 + 1.0, $baseP99 * $CriticalMultiplier), 2)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = "infra/load-test/reports/feed-threshold-calibration-$timestamp.md"

$lineItems = $rows | ForEach-Object {
  "| $($_.p95) | $($_.p99) | $($_.report) |"
}

$output = @"
# Feed Latency Threshold Calibration

Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## Inputs
- Reports glob: $ReportsGlob
- Reports parsed: $($rows.Count)
- Percentile used: $Percentile
- Warning multiplier: $WarningMultiplier
- Critical multiplier: $CriticalMultiplier

## Parsed Median Inputs
| p95_latency_ms | p99_latency_ms | report |
|---:|---:|---|
$($lineItems -join [Environment]::NewLine)

## Calibration Result
- Baseline p95 (`p$([int]($Percentile * 100))`): $baseP95 ms
- Baseline p99 (`p$([int]($Percentile * 100))`): $baseP99 ms

Recommended config:
- `APP_FEED_OBSERVABILITY_WARNING_P95_MS=$warningP95`
- `APP_FEED_OBSERVABILITY_WARNING_P99_MS=$warningP99`
- `APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=$criticalP99`

## Notes
- This script calibrates from synthetic load-test medians.
- Final production defaults should be re-calibrated after one sprint of real traffic telemetry.
"@

$null = New-Item -ItemType Directory -Force -Path "infra/load-test/reports"
Set-Content -Path $reportPath -Value $output -Encoding UTF8

Write-Output "Calibration report created: $reportPath"
Write-Output "APP_FEED_OBSERVABILITY_WARNING_P95_MS=$warningP95"
Write-Output "APP_FEED_OBSERVABILITY_WARNING_P99_MS=$warningP99"
Write-Output "APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=$criticalP99"
