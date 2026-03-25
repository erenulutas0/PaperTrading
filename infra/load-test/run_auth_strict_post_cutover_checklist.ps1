param(
  [string]$BaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$BaselineSeedEvents = 80,
  [int]$BaselineConcurrency = 6,
  [int]$BaselineRequestsPerWorker = 40,
  [string]$RelayBrokerRestartCommand = "",
  [int]$HeaderMismatchAttempts = 40,
  [int]$CanaryProbeAttempts = 4,
  [string]$ForwardedFor = "198.51.100.42",
  [int]$CommentAttempts = 18,
  [int]$ReplyAttempts = 18,
  [int]$FollowAttempts = 28,
  [int]$RefreshAttempts = 28,
  [int]$ReadProbeCount = 5,
  [switch]$SkipBaseline,
  [switch]$SkipRelay,
  [switch]$SkipSpoof,
  [switch]$SkipRateLimit,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the strict post-cutover checklist."
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
$reportPath = Join-Path $ReportsDir "auth-strict-post-cutover-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

if (-not ($SkipBaseline -and $SkipRelay)) {
  $transportParams = @{
    BaseUrl                   = $BaseUrl
    BaselineSeedEvents        = $BaselineSeedEvents
    BaselineConcurrency       = $BaselineConcurrency
    BaselineRequestsPerWorker = $BaselineRequestsPerWorker
    NoFail                    = $true
  }
  if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $transportParams["RelayBrokerRestartCommand"] = $RelayBrokerRestartCommand
  }
  if ($SkipBaseline) { $transportParams["SkipBaseline"] = $true }
  if ($SkipRelay) { $transportParams["SkipRelay"] = $true }

  $results.Add((Invoke-ScriptStep `
        -Name "Bearer Transport" `
        -ScriptPath (Join-Path $scriptDir "run_auth_strict_transport_validation.ps1") `
        -Parameters $transportParams `
        -ReportPattern "auth-strict-transport-validation-*.md")) | Out-Null
}

if (-not $SkipSpoof) {
  $results.Add((Invoke-ScriptStep `
        -Name "Spoof Regression" `
        -ScriptPath (Join-Path $scriptDir "run_auth_spoof_regression_check.ps1") `
        -Parameters @{
          BaseUrl                = $BaseUrl
          HeaderMismatchAttempts = $HeaderMismatchAttempts
          CanaryProbeAttempts    = $CanaryProbeAttempts
          NoFail                 = $true
        } `
        -ReportPattern "auth-spoof-regression-check-*.md")) | Out-Null
}

if (-not $SkipRateLimit) {
  $results.Add((Invoke-ScriptStep `
        -Name "Rate-Limit Isolation" `
        -ScriptPath (Join-Path $scriptDir "run_rate_limit_staging_checklist.ps1") `
        -Parameters @{
          BaseUrl         = $BaseUrl
          ForwardedFor    = $ForwardedFor
          CommentAttempts = $CommentAttempts
          ReplyAttempts   = $ReplyAttempts
          FollowAttempts  = $FollowAttempts
          RefreshAttempts = $RefreshAttempts
          ReadProbeCount  = $ReadProbeCount
          NoFail          = $true
        } `
        -ReportPattern "rate-limit-profile-smoke-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Auth Strict Post-Cutover Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "Coverage:"
$lines += "- Bearer-only browser/feed + relay transport"
$lines += "- spoof regression (`Bearer` + mismatched `X-User-Id`)"
$lines += "- endpoint-aware rate-limit isolation"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Auth strict post-cutover checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
