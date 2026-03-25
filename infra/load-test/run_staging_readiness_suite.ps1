param(
  [string]$BaseUrl,
  [string]$FrontendOrigin = "",
  [string]$ReportsDir = "infra/load-test/reports",
  [string]$RelayBrokerRestartCommand = "",
  [double]$MaxLegacyAccepted = 0,
  [int]$CalibrationIterations = 12,
  [int]$CalibrationIntervalSec = 20,
  [int]$RequestTimeoutSec = 15,
  [string]$OpsAlertComponent = "ops-alert-validation",
  [string]$OpsAlertSeverity = "warning",
  [string]$OpsAlertMessage = "Ops webhook staging checklist",
  [int]$OpsAlertPollAttempts = 8,
  [int]$OpsAlertPollIntervalSec = 2,
  [int]$CanaryIterations = 6,
  [int]$CanaryIntervalSec = 20,
  [int]$StrategyBotForwardTestTimeoutSec = 25,
  [int]$StrategyBotForwardTestPollAttempts = 12,
  [int]$StrategyBotForwardTestPollIntervalSec = 8,
  [int]$StrategyBotForwardTestRecoveryWaitSec = 20,
  [int]$StrategyBotSummaryTimeoutSec = 20,
  [int]$StrategyBotSummaryPollAttempts = 16,
  [int]$StrategyBotSummaryPollIntervalSec = 60,
  [int]$StrategyBotSummaryRecoveryWaitSec = 30,
  [switch]$IncludeCanaryWebSocketSnapshot,
  [switch]$AllowAlreadyProbedCanary,
  [switch]$SkipAudit,
  [switch]$SkipDataIntegrity,
  [switch]$SkipErrorContracts,
  [switch]$SkipLeaderboardPeriods,
  [switch]$SkipAuthReadiness,
  [switch]$SkipWebsocket,
  [switch]$SkipStrategyBotForwardTest,
  [switch]$SkipStrategyBotSummaries,
  [switch]$SkipOpsWebhook,
  [switch]$SkipOrigin,
  [switch]$SkipRelay,
  [switch]$SkipCanary,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the staging readiness suite."
}
if (-not $SkipWebsocket -and -not $SkipOrigin -and [string]::IsNullOrWhiteSpace($FrontendOrigin)) {
  throw "FrontendOrigin is required unless websocket origin checks are skipped."
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
$reportPath = Join-Path $ReportsDir "staging-readiness-suite-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipAudit) {
  $results.Add((Invoke-ScriptStep `
        -Name "Audit Staging Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_audit_staging_checklist.ps1") `
        -Parameters @{ BaseUrl = $BaseUrl; NoFail = $true } `
        -ReportPattern "audit-validation-suite-*.md")) | Out-Null
}

if (-not $SkipDataIntegrity) {
  $results.Add((Invoke-ScriptStep `
        -Name "Data Integrity Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_data_integrity_staging_checklist.ps1") `
        -Parameters @{ BaseUrl = $BaseUrl; NoFail = $true } `
        -ReportPattern "data-integrity-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipErrorContracts) {
  $results.Add((Invoke-ScriptStep `
        -Name "Error Contract Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_error_contract_staging_checklist.ps1") `
        -Parameters @{ BaseUrl = $BaseUrl; NoFail = $true } `
        -ReportPattern "error-contract-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipLeaderboardPeriods) {
  $results.Add((Invoke-ScriptStep `
        -Name "Leaderboard Period Validation Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_leaderboard_period_validation_staging_checklist.ps1") `
        -Parameters @{ BaseUrl = $BaseUrl; NoFail = $true } `
        -ReportPattern "leaderboard-period-validation-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipAuthReadiness) {
  $results.Add((Invoke-ScriptStep `
        -Name "Auth Pre-Cutover Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_auth_strict_pre_cutover_checklist.ps1") `
        -Parameters @{
          BaseUrl                = $BaseUrl
          MaxLegacyAccepted      = $MaxLegacyAccepted
          CalibrationIterations  = $CalibrationIterations
          CalibrationIntervalSec = $CalibrationIntervalSec
          RequestTimeoutSec      = $RequestTimeoutSec
          NoFail                 = $true
        } `
        -ReportPattern "auth-strict-pre-cutover-checklist-*.md")) | Out-Null
}

if (-not $SkipWebsocket) {
  $websocketParams = @{
    BaseUrl           = $BaseUrl
    CanaryIterations  = $CanaryIterations
    CanaryIntervalSec = $CanaryIntervalSec
    NoFail            = $true
  }
  if (-not [string]::IsNullOrWhiteSpace($FrontendOrigin)) {
    $websocketParams["FrontendOrigin"] = $FrontendOrigin
  }
  if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $websocketParams["RelayBrokerRestartCommand"] = $RelayBrokerRestartCommand
  }
  if ($IncludeCanaryWebSocketSnapshot) { $websocketParams["IncludeCanaryWebSocketSnapshot"] = $true }
  if ($AllowAlreadyProbedCanary) { $websocketParams["AllowAlreadyProbedCanary"] = $true }
  if ($SkipOrigin) { $websocketParams["SkipOrigin"] = $true }
  if ($SkipRelay) { $websocketParams["SkipRelay"] = $true }
  if ($SkipCanary) { $websocketParams["SkipCanary"] = $true }

  $results.Add((Invoke-ScriptStep `
        -Name "WebSocket Resilience Suite" `
        -ScriptPath (Join-Path $scriptDir "run_websocket_staging_resilience_suite.ps1") `
        -Parameters $websocketParams `
        -ReportPattern "websocket-staging-resilience-suite-*.md")) | Out-Null
}

if (-not $SkipStrategyBotForwardTest) {
  $results.Add((Invoke-ScriptStep `
        -Name "Strategy Bot Forward-Test Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_strategy_bot_forward_test_scheduler_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl         = $BaseUrl
          TimeoutSec      = $StrategyBotForwardTestTimeoutSec
          PollAttempts    = $StrategyBotForwardTestPollAttempts
          PollIntervalSec = $StrategyBotForwardTestPollIntervalSec
          RecoveryWaitSec = $StrategyBotForwardTestRecoveryWaitSec
          NoFail          = $true
        } `
        -ReportPattern "strategy-bot-forward-test-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipStrategyBotSummaries) {
  $results.Add((Invoke-ScriptStep `
        -Name "Strategy Bot Summary Precompute Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_strategy_bot_summary_precompute_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl         = $BaseUrl
          TimeoutSec      = $StrategyBotSummaryTimeoutSec
          PollAttempts    = $StrategyBotSummaryPollAttempts
          PollIntervalSec = $StrategyBotSummaryPollIntervalSec
          RecoveryWaitSec = $StrategyBotSummaryRecoveryWaitSec
          NoFail          = $true
        } `
        -ReportPattern "strategy-bot-summary-precompute-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipOpsWebhook) {
  $results.Add((Invoke-ScriptStep `
        -Name "Ops Webhook Checklist" `
        -ScriptPath (Join-Path $scriptDir "run_ops_alert_webhook_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl         = $BaseUrl
          Component       = $OpsAlertComponent
          Severity        = $OpsAlertSeverity
          Message         = $OpsAlertMessage
          PollAttempts    = $OpsAlertPollAttempts
          PollIntervalSec = $OpsAlertPollIntervalSec
          NoFail          = $true
        } `
        -ReportPattern "ops-alert-webhook-staging-checklist-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Staging Readiness Suite"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Frontend Origin: $(if ([string]::IsNullOrWhiteSpace($FrontendOrigin)) { 'n/a' } else { $FrontendOrigin })"
$lines += "- Status: **$overallStatus**"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "Coverage:"
$lines += "- audit inspection + write-capture staging checklist"
$lines += "- data-integrity checklist (market false-loss + Flyway/history integrity)"
$lines += "- unified error-contract checklist"
$lines += "- leaderboard period-slice validation checklist"
$lines += "- auth strict pre-cutover readiness checklist"
$lines += "- websocket/browser-origin/relay/canary/SSE-fallback resilience checklist"
$lines += "- strategy-bot forward-test scheduler checklist"
$lines += "- strategy-bot summary precompute checklist"
$lines += "- ops webhook delivery checklist"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Staging readiness suite report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
