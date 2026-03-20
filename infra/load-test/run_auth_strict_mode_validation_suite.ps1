param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$StrictSmokeBaseUrl = "",
  [string]$ReportsDir = "infra/load-test/reports",
  [double]$MaxLegacyAccepted = 0,
  [int]$BaselineSeedEvents = 40,
  [int]$BaselineConcurrency = 4,
  [int]$BaselineRequestsPerWorker = 20,
  [string]$RelayBrokerRestartCommand = "",
  [switch]$SkipLegacyUsage,
  [switch]$SkipStrictSmoke,
  [switch]$SkipAuthAttack,
  [switch]$SkipBaseline,
  [switch]$SkipRelay,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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
  $content = Get-Content $ReportPath -Raw
  $match = [regex]::Match($content, "- Status:\s+\*\*(.+?)\*\*", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if ($match.Success -and $match.Groups.Count -ge 2) {
    return $match.Groups[1].Value.Trim()
  }
  $lineMatch = [regex]::Match($content, "Status:\s+([A-Z_]+)", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if ($lineMatch.Success -and $lineMatch.Groups.Count -ge 2) {
    return $lineMatch.Groups[1].Value.Trim()
  }
  return ""
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
    $detail = $_.Exception.Message
    if ($report -eq $before) {
      $report = ""
    }
    return New-StepResult -Name $Name -Status "FAILED" -Detail $detail -ReportPath $report
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "auth-strict-mode-validation-suite-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

$strictBase = if ([string]::IsNullOrWhiteSpace($StrictSmokeBaseUrl)) { $BaseUrl } else { $StrictSmokeBaseUrl }

if (-not $SkipLegacyUsage) {
  $results.Add((Invoke-ScriptStep `
        -Name "Legacy Usage" `
        -ScriptPath (Join-Path $scriptDir "check_auth_legacy_usage.ps1") `
        -Arguments @("-BaseUrl", $BaseUrl, "-MaxLegacyAccepted", "$MaxLegacyAccepted", "-NoFail") `
        -ReportPattern "auth-legacy-usage-*.md")) | Out-Null
}

if (-not $SkipStrictSmoke) {
  $results.Add((Invoke-ScriptStep `
        -Name "Strict Smoke" `
        -ScriptPath (Join-Path $scriptDir "run_auth_strict_mode_smoke.ps1") `
        -Arguments @("-BaseUrl", $strictBase, "-NoFail") `
        -ReportPattern "auth-strict-mode-smoke-*.md")) | Out-Null
}

if (-not $SkipAuthAttack) {
  $results.Add((Invoke-ScriptStep `
        -Name "Auth Attack" `
        -ScriptPath (Join-Path $scriptDir "run_auth_attack_scenarios.ps1") `
        -Arguments @("-BaseUrl", $BaseUrl, "-NoFail") `
        -ReportPattern "auth-attack-scenarios-*.md")) | Out-Null
}

if (-not $SkipBaseline) {
  $results.Add((Invoke-ScriptStep `
        -Name "Baseline" `
        -ScriptPath (Join-Path $scriptDir "lightweight_baseline.ps1") `
        -Arguments @("-BaseUrl", $BaseUrl, "-SeedEvents", "$BaselineSeedEvents", "-Concurrency", "$BaselineConcurrency", "-RequestsPerWorker", "$BaselineRequestsPerWorker") `
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

$failed = @($results | Where-Object { $_.Status -notin @("PASSED", "READY", "CONDITIONAL_READY", "STABLE", "OBSERVE_WARNING") })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Auth Strict Mode Validation Suite"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Strict Smoke Base URL: $strictBase"
$lines += "- Status: **$overallStatus**"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Auth strict-mode validation suite report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
