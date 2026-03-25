param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$HeaderMismatchAttempts = 40,
  [int]$CanaryProbeAttempts = 4,
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

function Parse-HeaderMismatchRow {
  param([string]$ReportPath)

  if (-not (Test-Path $ReportPath)) {
    return $null
  }

  $content = Get-Content -Path $ReportPath -Raw
  $match = [regex]::Match(
    $content,
    "\|\s*bearer-header-mismatch-flood\s*\|\s*(\d+)\s*\|\s*([^\|]+)\|\s*(\d+)\s*\|\s*(\d+)\s*\|",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)

  if (-not $match.Success -or $match.Groups.Count -lt 5) {
    return $null
  }

  return [pscustomobject]@{
    Attempts        = [int]$match.Groups[1].Value
    ExpectedStatus  = $match.Groups[2].Value.Trim()
    ExpectedCount   = [int]$match.Groups[3].Value
    UnexpectedCount = [int]$match.Groups[4].Value
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$attackScript = Join-Path $scriptDir "run_auth_attack_scenarios.ps1"
if (-not (Test-Path $attackScript)) {
  throw "Auth attack script not found: $attackScript"
}

$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "auth-spoof-regression-check-$timestamp.md"

$before = Get-LatestReport -Directory $ReportsDir -Pattern "auth-attack-scenarios-*.md"
$childStatus = "FAILED"
$childReport = ""
$detail = ""

try {
  & powershell -ExecutionPolicy Bypass -File $attackScript `
    -BaseUrl $BaseUrl `
    -InvalidJwtAttempts 0 `
    -HeaderMismatchAttempts $HeaderMismatchAttempts `
    -InvalidRefreshAttempts 0 `
    -CanaryProbeAttempts $CanaryProbeAttempts `
    -NoFail | Out-Null

  if ($LASTEXITCODE -ne 0) {
    $detail = "exit_code=$LASTEXITCODE"
  }

  $childReport = Get-LatestReport -Directory $ReportsDir -Pattern "auth-attack-scenarios-*.md"
  if ([string]::IsNullOrWhiteSpace($childReport) -or $childReport -eq $before) {
    $detail = if ([string]::IsNullOrWhiteSpace($detail)) { "attack_report_not_found" } else { $detail }
  } else {
    $childStatus = Parse-MarkdownStatus -ReportPath $childReport
    if ([string]::IsNullOrWhiteSpace($childStatus)) {
      $childStatus = "FAILED"
    }
  }
} catch {
  $detail = $_.Exception.Message
  $childReport = Get-LatestReport -Directory $ReportsDir -Pattern "auth-attack-scenarios-*.md"
}

$mismatchRow = if (-not [string]::IsNullOrWhiteSpace($childReport)) { Parse-HeaderMismatchRow -ReportPath $childReport } else { $null }
$mismatchPassed = ($null -ne $mismatchRow -and $mismatchRow.Attempts -eq $HeaderMismatchAttempts -and $mismatchRow.ExpectedCount -eq $HeaderMismatchAttempts -and $mismatchRow.UnexpectedCount -eq 0)
$overallStatus = if ($childStatus -eq "PASSED" -and $mismatchPassed) { "PASSED" } else { "FAILED" }

if ([string]::IsNullOrWhiteSpace($detail)) {
  if ($null -eq $mismatchRow) {
    $detail = "header_mismatch_row_not_found"
  } else {
    $detail = "expected=$($mismatchRow.ExpectedCount)/$HeaderMismatchAttempts unexpected=$($mismatchRow.UnexpectedCount)"
  }
}

$lines = @()
$lines += "# Auth Spoof Regression Check"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Header mismatch attempts: $HeaderMismatchAttempts"
$lines += "- Canary probe attempts: $CanaryProbeAttempts"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
$lines += "| Attack report produced | $(if (-not [string]::IsNullOrWhiteSpace($childReport)) { 'PASS' } else { 'FAIL' }) | $(if ([string]::IsNullOrWhiteSpace($childReport)) { 'n/a' } else { $childReport.Replace('|', '/') }) |"
$lines += "| Child attack status | $(if ($childStatus -eq 'PASSED') { 'PASS' } else { 'FAIL' }) | $childStatus |"
$lines += "| Header mismatch scenario | $(if ($mismatchPassed) { 'PASS' } else { 'FAIL' }) | $($detail.Replace('|', '/')) |"

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Auth spoof regression report created: $reportPath"
Write-Host "Status: $overallStatus"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
