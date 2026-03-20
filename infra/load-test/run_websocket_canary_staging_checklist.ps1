param(
  [string]$BaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$Iterations = 6,
  [int]$IntervalSec = 20,
  [int]$RequestTimeoutSec = 20,
  [switch]$IncludeWebSocketSnapshot,
  [switch]$AllowAlreadyProbed,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the websocket canary staging checklist."
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

function Get-ReportValue {
  param(
    [string]$Content,
    [string]$Label
  )

  $pattern = "(?im)^- " + [regex]::Escape($Label) + ":\s+(.+)$"
  $match = [regex]::Match($Content, $pattern)
  if ($match.Success -and $match.Groups.Count -ge 2) {
    return $match.Groups[1].Value.Trim()
  }
  return ""
}

function Get-StatusFromReport {
  param([string]$ReportPath)

  if (-not (Test-Path $ReportPath)) {
    return ""
  }

  $content = Get-Content -Path $ReportPath -Raw
  $match = [regex]::Match($content, "- Status:\s+\*\*(.+?)\*\*", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if ($match.Success -and $match.Groups.Count -ge 2) {
    return $match.Groups[1].Value.Trim()
  }
  return ""
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "websocket-canary-staging-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

$runnerScript = Join-Path $scriptDir "run_websocket_canary_external.ps1"
$runnerArgs = @(
  "-BaseUrl", $BaseUrl,
  "-Iterations", "$Iterations",
  "-IntervalSec", "$IntervalSec",
  "-RequestTimeoutSec", "$RequestTimeoutSec"
)
if ($IncludeWebSocketSnapshot) {
  $runnerArgs += "-IncludeWebSocketSnapshot"
}

$before = Get-LatestReport -Directory $ReportsDir -Pattern "websocket-canary-external-*.md"
& powershell -ExecutionPolicy Bypass -File $runnerScript @runnerArgs | Out-Null
$runnerExitCode = $LASTEXITCODE
$runnerReport = Get-LatestReport -Directory $ReportsDir -Pattern "websocket-canary-external-*.md"
if ($runnerReport -eq $before) {
  $runnerReport = ""
}

if ([string]::IsNullOrWhiteSpace($runnerReport)) {
  $results.Add((New-StepResult -Name "External Canary Run" -Status "FAILED" -Detail "report_not_found")) | Out-Null
} else {
  $runnerContent = Get-Content -Path $runnerReport -Raw
  $runnerStatus = Get-StatusFromReport -ReportPath $runnerReport
  if ([string]::IsNullOrWhiteSpace($runnerStatus)) {
    $runnerStatus = if ($runnerExitCode -eq 0) { "PASSED" } else { "FAILED" }
  }

  $initialState = Get-ReportValue -Content $runnerContent -Label "Initial latest state"
  $finalState = Get-ReportValue -Content $runnerContent -Label "Final latest state"
  $finalSuccess = Get-ReportValue -Content $runnerContent -Label "Final success"
  $finalAlertState = Get-ReportValue -Content $runnerContent -Label "Final alertState"

  $initialStatus = if ($initialState -eq "not-run-yet") {
    "PASSED"
  } elseif ($AllowAlreadyProbed -and $initialState -eq "snapshot-available") {
    "PASSED"
  } else {
    "FAILED"
  }
  $initialDetail = if ($initialState -eq "not-run-yet") {
    "initial_latest_state=not-run-yet"
  } elseif ($AllowAlreadyProbed -and $initialState -eq "snapshot-available") {
    "initial_latest_state=already-probed"
  } else {
    "initial_latest_state=$initialState"
  }
  $results.Add((New-StepResult -Name "Initial Canary State" -Status $initialStatus -Detail $initialDetail -ReportPath $runnerReport)) | Out-Null

  $results.Add((New-StepResult -Name "External Canary Run" -Status $runnerStatus -Detail "runner_exit_code=$runnerExitCode" -ReportPath $runnerReport)) | Out-Null

  $finalHealthy = $finalState -eq "snapshot-available" -and $finalSuccess -eq "True" -and $finalAlertState -ne "CRITICAL"
  $finalDetail = "final_state=$finalState, final_success=$finalSuccess, final_alert_state=$finalAlertState"
  $results.Add((New-StepResult -Name "Post-Run Canary State" -Status $(if ($finalHealthy) { "PASSED" } else { "FAILED" }) -Detail $finalDetail -ReportPath $runnerReport)) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# WebSocket Canary Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Iterations: $Iterations"
$lines += "- Interval seconds: $IntervalSec"
$lines += "- Include websocket endpoint snapshot: $IncludeWebSocketSnapshot"
$lines += "- Allow already probed initial state: $AllowAlreadyProbed"
$lines += ""
$lines += "Coverage:"
$lines += "- capture latest canary state without forcing a probe (`refresh=false`)"
$lines += "- run the external websocket canary runner"
$lines += "- verify the post-run latest snapshot is no longer `not-run-yet` and is not in `CRITICAL` state"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Websocket canary staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
