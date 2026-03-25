param(
  [string]$BaseUrl,
  [string]$FrontendOrigin,
  [string]$ReportsDir = "infra/load-test/reports",
  [string]$RelayBrokerRestartCommand = "",
  [int]$CanaryIterations = 6,
  [int]$CanaryIntervalSec = 20,
  [switch]$IncludeCanaryWebSocketSnapshot,
  [switch]$AllowAlreadyProbedCanary,
  [switch]$SkipOrigin,
  [switch]$SkipRelay,
  [switch]$SkipCanary,
  [switch]$SkipSseFallback,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the websocket staging resilience suite."
}
if (-not $SkipOrigin -and [string]::IsNullOrWhiteSpace($FrontendOrigin)) {
  throw "FrontendOrigin is required unless -SkipOrigin is supplied."
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
$reportPath = Join-Path $ReportsDir "websocket-staging-resilience-suite-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipOrigin) {
  $originParams = @{
    BaseUrl        = $BaseUrl
    FrontendOrigin = $FrontendOrigin
    NoFail         = $true
  }
  if ($SkipRelay) {
    $originParams["SkipRelay"] = $true
  } elseif (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $originParams["RelayBrokerRestartCommand"] = $RelayBrokerRestartCommand
  }

  $results.Add((Invoke-ScriptStep `
        -Name "Browser Origin" `
        -ScriptPath (Join-Path $scriptDir "run_browser_origin_staging_checklist.ps1") `
        -Parameters $originParams `
        -ReportPattern "browser-origin-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipRelay) {
  $relayParams = @{
    SkipAppStart = $true
    BaseUrl      = $BaseUrl
    NoFail       = $true
  }
  if (-not [string]::IsNullOrWhiteSpace($FrontendOrigin)) {
    $relayParams["OriginHeader"] = $FrontendOrigin
  }
  if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $relayParams["BrokerRestartCommand"] = $RelayBrokerRestartCommand
  }

  $results.Add((Invoke-ScriptStep `
        -Name "Relay Continuity" `
        -ScriptPath (Join-Path $scriptDir "validate_websocket_relay_smoke.ps1") `
        -Parameters $relayParams `
        -ReportPattern "websocket-relay-smoke-*.md")) | Out-Null
}

if (-not $SkipCanary) {
  $canaryParams = @{
    BaseUrl     = $BaseUrl
    Iterations  = $CanaryIterations
    IntervalSec = $CanaryIntervalSec
    NoFail      = $true
  }
  if ($IncludeCanaryWebSocketSnapshot) {
    $canaryParams["IncludeWebSocketSnapshot"] = $true
  }
  if ($AllowAlreadyProbedCanary) {
    $canaryParams["AllowAlreadyProbed"] = $true
  }

  $results.Add((Invoke-ScriptStep `
        -Name "Canary Transition" `
        -ScriptPath (Join-Path $scriptDir "run_websocket_canary_staging_checklist.ps1") `
        -Parameters $canaryParams `
        -ReportPattern "websocket-canary-staging-checklist-*.md")) | Out-Null
}

if (-not $SkipSseFallback) {
  $results.Add((Invoke-ScriptStep `
        -Name "Notification SSE Fallback" `
        -ScriptPath (Join-Path $scriptDir "run_notification_sse_fallback_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl = $BaseUrl
          NoFail  = $true
        } `
        -ReportPattern "notification-sse-fallback-staging-checklist-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# WebSocket Staging Resilience Suite"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Frontend Origin: $(if ([string]::IsNullOrWhiteSpace($FrontendOrigin)) { 'n/a' } else { $FrontendOrigin })"
$lines += "- Status: **$overallStatus**"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "Coverage:"
$lines += "- browser-origin REST and optional websocket origin validation"
$lines += "- websocket relay continuity / broker restart validation"
$lines += "- websocket canary latest-snapshot transition validation"
$lines += "- direct notification SSE fallback delivery validation"
$lines += ""
$lines += "Known gap:"
$lines += "- this suite validates transport-level SSE fallback delivery, not full browser-side toast/render dedupe semantics"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Websocket staging resilience suite report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
