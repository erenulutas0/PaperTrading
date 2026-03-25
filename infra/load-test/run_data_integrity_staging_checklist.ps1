param(
  [string]$BaseUrl,
  [string]$PsqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe",
  [string]$DbHost = "localhost",
  [int]$DbPort = 5433,
  [string]$DbName = "finance_db",
  [string]$DbUser = "postgres",
  [string]$DbPassword = "password",
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$SkipMarketPrice,
  [switch]$SkipFlywayData,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl) -and -not $SkipMarketPrice) {
  throw "BaseUrl is required unless -SkipMarketPrice is supplied."
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

function Invoke-ScriptStep {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [hashtable]$Parameters,
    [string]$ReportPattern
  )

  $before = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
  try {
    & $ScriptPath @Parameters | Out-Null

    $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
    if ([string]::IsNullOrWhiteSpace($report)) {
      return New-StepResult -Name $Name -Status "FAILED" -Detail "report_not_found" -ReportPath ""
    }

    $status = Parse-MarkdownStatus -ReportPath $report
    if ([string]::IsNullOrWhiteSpace($status)) {
      $status = "PASSED"
    }

    return New-StepResult -Name $Name -Status $status -Detail "ok" -ReportPath $report
  } catch {
    $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
    if ($report -eq $before) {
      $report = ""
    }
    return New-StepResult -Name $Name -Status "FAILED" -Detail $_.Exception.Message -ReportPath $report
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "data-integrity-staging-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipMarketPrice) {
  $results.Add((Invoke-ScriptStep `
        -Name "Market Price False-Loss Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_market_price_false_loss_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl    = $BaseUrl
          ReportsDir = $ReportsDir
          NoFail     = $true
        } `
        -ReportPattern "market-price-false-loss-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipFlywayData) {
  $results.Add((Invoke-ScriptStep `
        -Name "Flyway Data Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_flyway_data_staging_checklist.ps1") `
        -Parameters @{
          PsqlPath   = $PsqlPath
          DbHost     = $DbHost
          DbPort     = $DbPort
          DbName     = $DbName
          DbUser     = $DbUser
          DbPassword = $DbPassword
          ReportsDir = $ReportsDir
          NoFail     = $true
        } `
        -ReportPattern "flyway-data-staging-checklist-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -notin @("PASSED", "READY", "CONDITIONAL_READY", "SKIPPED") })
$overallStatus = if ($results.Count -gt 0 -and $failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Data Integrity Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $(if ([string]::IsNullOrWhiteSpace($BaseUrl)) { 'n/a' } else { $BaseUrl })"
$lines += "- DB Host: $DbHost"
$lines += "- DB Port: $DbPort"
$lines += "- DB Name: $DbName"
$lines += "- Status: **$overallStatus**"
$lines += ""
$lines += "Coverage:"
$lines += "- market-price false-loss regression on small crypto positions"
$lines += "- Flyway/data integrity for V3/V9/V10/V27 plus legacy zeroed crypto quantity inspection"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Data integrity staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
