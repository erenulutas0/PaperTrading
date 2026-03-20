param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Iterations = 10,
  [int]$IntervalSec = 30,
  [int]$RequestTimeoutSec = 20,
  [switch]$IncludeWebSocketSnapshot,
  [switch]$FailOnAnyFailure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-JsonGet {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  return Invoke-RestMethod -Method Get -Uri $Url -TimeoutSec $TimeoutSec -ErrorAction Stop
}

function Get-Percentile {
  param(
    [double[]]$Values,
    [double]$Percentile
  )

  if ($null -eq $Values -or $Values.Count -eq 0) {
    return 0.0
  }
  $sorted = $Values | Sort-Object
  $rank = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
  if ($rank -lt 0) { $rank = 0 }
  if ($rank -ge $sorted.Count) { $rank = $sorted.Count - 1 }
  return [double]$sorted[$rank]
}

function To-DoubleSafe {
  param([object]$Value)
  if ($null -eq $Value) {
    return 0.0
  }
  try {
    return [double]$Value
  } catch {
    return 0.0
  }
}

function Get-PropertyValueOrDefault {
  param(
    [object]$Object,
    [string]$Name,
    [object]$DefaultValue
  )

  if ($null -eq $Object) {
    return $DefaultValue
  }

  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property -or $null -eq $property.Value) {
    return $DefaultValue
  }

  return $property.Value
}

function Get-CanaryLatestSnapshot {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  try {
    return Invoke-JsonGet -Url "$Url?refresh=false" -TimeoutSec $TimeoutSec
  } catch {
    return $null
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Path $reportsDir -Force
$reportPath = Join-Path $reportsDir "websocket-canary-external-$timestamp.md"

$canaryEndpoint = "$BaseUrl/actuator/websocketcanary"
$websocketEndpoint = "$BaseUrl/actuator/websocket"
$healthEndpoint = "$BaseUrl/actuator/health"

$runRows = New-Object System.Collections.Generic.List[object]
$errors = New-Object System.Collections.Generic.List[string]
$latencies = New-Object System.Collections.Generic.List[double]
$successCount = 0
$failureCount = 0
$overallStatus = "PASSED"

$healthStatus = "unknown"
try {
  $health = Invoke-JsonGet -Url $healthEndpoint -TimeoutSec $RequestTimeoutSec
  if ($health.status) {
    $healthStatus = [string]$health.status
  }
} catch {
  $errors.Add("Health check failed: $($_.Exception.Message)")
}

$initialSnapshot = Get-CanaryLatestSnapshot -Url $canaryEndpoint -TimeoutSec $RequestTimeoutSec
$initialCheckedAt = [string](Get-PropertyValueOrDefault -Object $initialSnapshot -Name "checkedAt" -DefaultValue "")
$initialAlertState = [string](Get-PropertyValueOrDefault -Object $initialSnapshot -Name "alertState" -DefaultValue "")
$initialSuccess = [string](Get-PropertyValueOrDefault -Object $initialSnapshot -Name "success" -DefaultValue "")
$initialError = [string](Get-PropertyValueOrDefault -Object $initialSnapshot -Name "error" -DefaultValue "")
$initialState = if ([string]::IsNullOrWhiteSpace($initialCheckedAt)) { "not-run-yet" } else { "snapshot-available" }

for ($i = 1; $i -le $Iterations; $i++) {
  $checkedAt = (Get-Date).ToString("s")
  $success = $false
  $latencyMs = 0.0
  $consecutiveFailures = 0
  $alertState = "unknown"
  $probeError = ""
  $topicReceived = $false
  $userQueueReceived = $false
  $activeSessions = ""
  $stompErrors = ""

  try {
    $snapshot = Invoke-JsonGet -Url $canaryEndpoint -TimeoutSec $RequestTimeoutSec
    if ($null -ne $snapshot) {
      $success = [bool]$snapshot.success
      $latencyMs = To-DoubleSafe -Value $snapshot.latencyMs
      $consecutiveFailures = [int](Get-PropertyValueOrDefault -Object $snapshot -Name "consecutiveFailures" -DefaultValue 0)
      $alertState = [string](Get-PropertyValueOrDefault -Object $snapshot -Name "alertState" -DefaultValue "unknown")
      $probeError = [string](Get-PropertyValueOrDefault -Object $snapshot -Name "error" -DefaultValue "")
      $topicReceived = [bool](Get-PropertyValueOrDefault -Object $snapshot -Name "topicReceived" -DefaultValue $false)
      $userQueueReceived = [bool](Get-PropertyValueOrDefault -Object $snapshot -Name "userQueueReceived" -DefaultValue $false)
      $latencies.Add($latencyMs)
    } else {
      $probeError = "empty-canary-response"
    }
  } catch {
    $probeError = $_.Exception.Message
  }

  if ($IncludeWebSocketSnapshot) {
    try {
      $wsSnapshot = Invoke-JsonGet -Url $websocketEndpoint -TimeoutSec $RequestTimeoutSec
      if ($null -ne $wsSnapshot) {
        $activeSessions = [string](Get-PropertyValueOrDefault -Object $wsSnapshot -Name "activeSessions" -DefaultValue "")
        $stompErrors = [string](Get-PropertyValueOrDefault -Object $wsSnapshot -Name "stompErrorEvents" -DefaultValue "")
      }
    } catch {
      if ([string]::IsNullOrWhiteSpace($probeError)) {
        $probeError = "websocket-endpoint-failed: $($_.Exception.Message)"
      }
    }
  }

  if ($success) {
    $successCount++
  } else {
    $failureCount++
  }

  $runRows.Add([pscustomobject]@{
      run                 = $i
      checkedAt           = $checkedAt
      success             = $success
      latencyMs           = [Math]::Round($latencyMs, 2)
      consecutiveFailures = $consecutiveFailures
      alertState          = $alertState
      topicReceived       = $topicReceived
      userQueueReceived   = $userQueueReceived
      activeSessions      = $activeSessions
      stompErrorEvents    = $stompErrors
      error               = $probeError
    })

  if ($i -lt $Iterations -and $IntervalSec -gt 0) {
    Start-Sleep -Seconds $IntervalSec
  }
}

$totalRuns = $runRows.Count
$successRate = if ($totalRuns -gt 0) { [Math]::Round(($successCount * 100.0) / $totalRuns, 2) } else { 0.0 }
$avgLatency = if ($latencies.Count -gt 0) { [Math]::Round((($latencies | Measure-Object -Average).Average), 2) } else { 0.0 }
$p95Latency = [Math]::Round((Get-Percentile -Values $latencies.ToArray() -Percentile 95), 2)
$p99Latency = [Math]::Round((Get-Percentile -Values $latencies.ToArray() -Percentile 99), 2)
$finalSnapshot = Get-CanaryLatestSnapshot -Url $canaryEndpoint -TimeoutSec $RequestTimeoutSec
$finalCheckedAt = [string](Get-PropertyValueOrDefault -Object $finalSnapshot -Name "checkedAt" -DefaultValue "")
$finalAlertState = [string](Get-PropertyValueOrDefault -Object $finalSnapshot -Name "alertState" -DefaultValue "")
$finalSuccess = [string](Get-PropertyValueOrDefault -Object $finalSnapshot -Name "success" -DefaultValue "")
$finalError = [string](Get-PropertyValueOrDefault -Object $finalSnapshot -Name "error" -DefaultValue "")
$finalState = if ([string]::IsNullOrWhiteSpace($finalCheckedAt)) { "not-run-yet" } else { "snapshot-available" }

if ($failureCount -gt 0) {
  $overallStatus = "FAILED"
}

$notesBlock = if ($errors.Count -eq 0) { "- none" } else { ($errors | ForEach-Object { "- $_" }) -join [Environment]::NewLine }

$rowsMarkdown = @()
$rowsMarkdown += "| Run | CheckedAt | Success | Latency(ms) | ConsecutiveFailures | AlertState | TopicReceived | UserQueueReceived | ActiveSessions | StompErrors | Error |"
$rowsMarkdown += "|-----|-----------|---------|-------------|---------------------|------------|---------------|-------------------|----------------|------------|-------|"
foreach ($row in $runRows) {
  $rowsMarkdown += "| $($row.run) | $($row.checkedAt) | $($row.success) | $($row.latencyMs) | $($row.consecutiveFailures) | $($row.alertState) | $($row.topicReceived) | $($row.userQueueReceived) | $($row.activeSessions) | $($row.stompErrorEvents) | $($row.error) |"
}

$reportLines = @(
  "# External WebSocket Canary Run",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- Canary endpoint: $canaryEndpoint",
  "- Iterations: $Iterations",
  "- Interval seconds: $IntervalSec",
  "- Request timeout seconds: $RequestTimeoutSec",
  "- Include websocket endpoint snapshot: $IncludeWebSocketSnapshot",
  "- Initial health status: $healthStatus",
  "",
  "## Snapshot Transition",
  "- Initial latest state: $initialState",
  "- Initial checkedAt: $(if ([string]::IsNullOrWhiteSpace($initialCheckedAt)) { 'n/a' } else { $initialCheckedAt })",
  "- Initial success: $(if ([string]::IsNullOrWhiteSpace($initialSuccess)) { 'n/a' } else { $initialSuccess })",
  "- Initial alertState: $(if ([string]::IsNullOrWhiteSpace($initialAlertState)) { 'n/a' } else { $initialAlertState })",
  "- Initial error: $(if ([string]::IsNullOrWhiteSpace($initialError)) { 'n/a' } else { $initialError })",
  "- Final latest state: $finalState",
  "- Final checkedAt: $(if ([string]::IsNullOrWhiteSpace($finalCheckedAt)) { 'n/a' } else { $finalCheckedAt })",
  "- Final success: $(if ([string]::IsNullOrWhiteSpace($finalSuccess)) { 'n/a' } else { $finalSuccess })",
  "- Final alertState: $(if ([string]::IsNullOrWhiteSpace($finalAlertState)) { 'n/a' } else { $finalAlertState })",
  "- Final error: $(if ([string]::IsNullOrWhiteSpace($finalError)) { 'n/a' } else { $finalError })",
  "",
  "## Summary",
  "- Status: **$overallStatus**",
  "- Total runs: $totalRuns",
  "- Success count: $successCount",
  "- Failure count: $failureCount",
  "- Success rate (%): $successRate",
  "- Avg latency (ms): $avgLatency",
  "- P95 latency (ms): $p95Latency",
  "- P99 latency (ms): $p99Latency",
  "",
  "## Run Details",
  ($rowsMarkdown -join [Environment]::NewLine),
  "",
  "## Notes",
  $notesBlock
)

$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "External canary report created: $reportPath"
Write-Output "Status: $overallStatus | Runs: $totalRuns | Success: $successCount | Failure: $failureCount | SuccessRate: $successRate%"

if ($FailOnAnyFailure -and $failureCount -gt 0) {
  exit 1
}
