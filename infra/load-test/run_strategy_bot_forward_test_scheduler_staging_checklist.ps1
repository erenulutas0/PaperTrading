param(
  [string]$BaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$TimeoutSec = 25,
  [int]$PollAttempts = 12,
  [int]$PollIntervalSec = 8,
  [string]$RestartCommand = "",
  [int]$RecoveryWaitSec = 20,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the strategy-bot forward-test staging checklist."
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

function Get-StatusFromReport {
  param([string]$ReportPath)

  if (-not (Test-Path $ReportPath)) {
    return ""
  }

  $content = Get-Content -Path $ReportPath -Raw
  $match = [regex]::Match($content, "- Status:\s+\*\*(.+?)\*\*|- Status:\s+(.+)$", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline)
  if ($match.Success) {
    if ($match.Groups[1].Success) {
      return $match.Groups[1].Value.Trim()
    }
    if ($match.Groups[2].Success) {
      return $match.Groups[2].Value.Trim()
    }
  }
  return ""
}

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5
      if ($response.status -eq "UP") {
        return $true
      }
    } catch {
    }
    Start-Sleep -Seconds 2
  }

  return $false
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "strategy-bot-forward-test-staging-checklist-$timestamp.md"
$smokeScript = Join-Path $scriptDir "run_strategy_bot_forward_test_scheduler_smoke.ps1"
$healthUrl = "$BaseUrl/actuator/health"
$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$restartLogPath = Join-Path $ReportsDir "strategy-bot-forward-test-staging-restart-$timestamp.log"

if (-not (Test-Path $smokeScript)) {
  throw "Missing dependency script: $smokeScript"
}

$initialHealthy = Wait-HttpHealthy -Url $healthUrl -TimeoutSec 25
$results.Add((New-StepResult -Name "Initial Health" -Status $(if ($initialHealthy) { "PASSED" } else { "FAILED" }) -Detail "url=$healthUrl")) | Out-Null

$restartExitCode = "n/a"
if (-not [string]::IsNullOrWhiteSpace($RestartCommand)) {
  try {
    $restartOutput = & "pwsh" -NoLogo -NoProfile -Command $RestartCommand 2>&1
    $restartExitCode = if ($null -ne $LASTEXITCODE) { [string]$LASTEXITCODE } else { if ($?) { "0" } else { "1" } }
    Set-Content -Path $restartLogPath -Value (($restartOutput | Out-String).Trim()) -Encoding UTF8
    $results.Add((New-StepResult -Name "Restart Command" -Status $(if ($restartExitCode -eq "0") { "PASSED" } else { "FAILED" }) -Detail "exit_code=$restartExitCode" -ReportPath $restartLogPath)) | Out-Null
  } catch {
    $restartExitCode = "exception"
    $notes.Add($_.Exception.Message) | Out-Null
    $results.Add((New-StepResult -Name "Restart Command" -Status "FAILED" -Detail "exception=$($_.Exception.Message)" -ReportPath $restartLogPath)) | Out-Null
  }

  Start-Sleep -Seconds $RecoveryWaitSec
  $postRestartHealthy = Wait-HttpHealthy -Url $healthUrl -TimeoutSec 60
  $results.Add((New-StepResult -Name "Post-Restart Health" -Status $(if ($postRestartHealthy) { "PASSED" } else { "FAILED" }) -Detail "url=$healthUrl, recovery_wait=$RecoveryWaitSec")) | Out-Null
}

$beforeSmoke = Get-LatestReport -Directory $ReportsDir -Pattern "strategy-bot-forward-test-scheduler-smoke-*.md"
& powershell -ExecutionPolicy Bypass -File $smokeScript `
  -BaseUrl $BaseUrl `
  -OutputDir $ReportsDir `
  -TimeoutSec $TimeoutSec `
  -PollAttempts $PollAttempts `
  -PollIntervalSec $PollIntervalSec `
  -NoFail | Out-Null
$smokeExitCode = $LASTEXITCODE
$smokeReport = Get-LatestReport -Directory $ReportsDir -Pattern "strategy-bot-forward-test-scheduler-smoke-*.md"
if ($smokeReport -eq $beforeSmoke) {
  $smokeReport = ""
}

if ([string]::IsNullOrWhiteSpace($smokeReport)) {
  $results.Add((New-StepResult -Name "Scheduler Smoke" -Status "FAILED" -Detail "report_not_found, exit_code=$smokeExitCode")) | Out-Null
} else {
  $smokeStatus = Get-StatusFromReport -ReportPath $smokeReport
  if ([string]::IsNullOrWhiteSpace($smokeStatus)) {
    $smokeStatus = if ($smokeExitCode -eq 0) { "PASSED" } else { "FAILED" }
  }
  $results.Add((New-StepResult -Name "Scheduler Smoke" -Status $smokeStatus -Detail "exit_code=$smokeExitCode" -ReportPath $smokeReport)) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Strategy Bot Forward-Test Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Poll attempts: $PollAttempts"
$lines += "- Poll interval seconds: $PollIntervalSec"
$lines += "- Restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RestartCommand)) { 'false' } else { 'true' })"
$lines += "- Restart recovery wait seconds: $RecoveryWaitSec"
$lines += ""
$lines += "Coverage:"
$lines += "- verify target runtime is healthy before scheduler validation"
$lines += "- optionally run a caller-supplied restart command before smoke validation"
$lines += "- run the actuator-backed forward-test scheduler smoke with staging-friendly polling"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

if ($notes.Count -gt 0) {
  $lines += ""
  $lines += "## Notes"
  foreach ($note in $notes) {
    $lines += "- $($note.Replace('|', '/'))"
  }
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Strategy bot forward-test staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
