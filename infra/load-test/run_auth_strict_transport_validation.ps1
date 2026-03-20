param(
  [string]$BaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$BaselineSeedEvents = 80,
  [int]$BaselineConcurrency = 6,
  [int]$BaselineRequestsPerWorker = 40,
  [string]$RelayBrokerRestartCommand = "",
  [switch]$SkipBaseline,
  [switch]$SkipRelay,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for strict transport validation."
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

function Invoke-ScriptStep {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [string[]]$Arguments,
    [string]$ReportPattern
  )

  $before = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
  try {
    & powershell -ExecutionPolicy Bypass -File $ScriptPath @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) {
      $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
      if ($report -eq $before) {
        $report = ""
      }
      return New-StepResult -Name $Name -Status "FAILED" -Detail "exit_code=$LASTEXITCODE" -ReportPath $report
    }
    $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
    if ([string]::IsNullOrWhiteSpace($report)) {
      return New-StepResult -Name $Name -Status "FAILED" -Detail "report_not_found"
    }
    return New-StepResult -Name $Name -Status "PASSED" -Detail "ok" -ReportPath $report
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
$reportPath = Join-Path $ReportsDir "auth-strict-transport-validation-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipBaseline) {
  $results.Add((Invoke-ScriptStep `
        -Name "Baseline" `
        -ScriptPath (Join-Path $scriptDir "lightweight_baseline.ps1") `
        -Arguments @(
          "-BaseUrl", $BaseUrl,
          "-SeedEvents", "$BaselineSeedEvents",
          "-Concurrency", "$BaselineConcurrency",
          "-RequestsPerWorker", "$BaselineRequestsPerWorker"
        ) `
        -ReportPattern "load-baseline-*.md")) | Out-Null
}

if (-not $SkipRelay) {
  $relayArgs = @("-SkipAppStart", "-BaseUrl", $BaseUrl)
  if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $relayArgs += @("-BrokerRestartCommand", $RelayBrokerRestartCommand)
  }
  $results.Add((Invoke-ScriptStep `
        -Name "Relay Smoke" `
        -ScriptPath (Join-Path $scriptDir "validate_websocket_relay_smoke.ps1") `
        -Arguments ($relayArgs + @()) `
        -ReportPattern "websocket-relay-smoke-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Auth Strict Transport Validation"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Baseline config: seed=$BaselineSeedEvents concurrency=$BaselineConcurrency requestsPerWorker=$BaselineRequestsPerWorker"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "Bearer-only transport focus:"
$lines += '- `lightweight_baseline.ps1` already authenticates personalized/write traffic with `Authorization: Bearer ...`'
$lines += '- `validate_websocket_relay_smoke.ps1` already authenticates REST follow/join paths and STOMP `CONNECT` with Bearer tokens'
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Auth strict transport validation report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
