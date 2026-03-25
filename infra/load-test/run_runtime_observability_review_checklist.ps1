param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds(20)

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url
  )

  try {
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    return [pscustomobject]@{
      status  = [int]$response.StatusCode
      content = $content
      ok      = $response.IsSuccessStatusCode
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      status  = 0
      content = ""
      ok      = $false
      error   = $_.Exception.Message
    }
  }
}

function New-StepResult {
  param(
    [string]$Name,
    [string]$Status,
    [string]$Detail
  )

  return [pscustomobject]@{
    Name   = $Name
    Status = $Status
    Detail = $Detail
  }
}

function Get-Json {
  param([string]$Content)

  if ([string]::IsNullOrWhiteSpace($Content)) {
    return $null
  }
  try {
    return $Content | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Test-JsonKeys {
  param(
    [object]$Json,
    [string[]]$Keys
  )

  if ($null -eq $Json) {
    return $false
  }

  foreach ($key in $Keys) {
    if (-not ($Json.PSObject.Properties.Name -contains $key)) {
      return $false
    }
  }
  return $true
}

function Get-JsonPropertyValue {
  param(
    [object]$Json,
    [string]$Name,
    [string]$DefaultValue = ""
  )

  if ($null -eq $Json) {
    return $DefaultValue
  }

  $prop = $Json.PSObject.Properties[$Name]
  if ($null -eq $prop) {
    return $DefaultValue
  }
  return [string]$prop.Value
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "runtime-observability-review-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
$healthJson = Get-Json -Content $health.content
$healthOk = $health.status -eq 200 -and $null -ne $healthJson -and ($healthJson.PSObject.Properties.Name -contains "status")
$results.Add((New-StepResult -Name "Health" -Status $(if ($healthOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($health.status), health=$($healthJson.status), error=$($health.error)")) | Out-Null

$feed = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/feedlatency"
$feedJson = Get-Json -Content $feed.content
$feedOk = $feed.status -eq 200 -and (Test-JsonKeys -Json $feedJson -Keys @("warningP95Ms", "warningP99Ms", "criticalP99Ms", "warningBreaches", "criticalBreaches"))
$results.Add((New-StepResult -Name "Feed Latency Snapshot" -Status $(if ($feedOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($feed.status), warningP95=$(Get-JsonPropertyValue -Json $feedJson -Name 'warningP95Ms' -DefaultValue '<missing>'), criticalP99=$(Get-JsonPropertyValue -Json $feedJson -Name 'criticalP99Ms' -DefaultValue '<missing>'), criticalBreaches=$(Get-JsonPropertyValue -Json $feedJson -Name 'criticalBreaches' -DefaultValue '<missing>'), error=$($feed.error)")) | Out-Null

$auth = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/authsessions"
$authJson = Get-Json -Content $auth.content
$authOk = $auth.status -eq 200 -and (Test-JsonKeys -Json $authJson -Keys @("alertState", "totalRefreshAttempts", "invalidRefreshRatio"))
$results.Add((New-StepResult -Name "Auth Session Snapshot" -Status $(if ($authOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($auth.status), alertState=$(Get-JsonPropertyValue -Json $authJson -Name 'alertState' -DefaultValue '<missing>'), attempts=$(Get-JsonPropertyValue -Json $authJson -Name 'totalRefreshAttempts' -DefaultValue '<missing>'), invalidRatio=$(Get-JsonPropertyValue -Json $authJson -Name 'invalidRefreshRatio' -DefaultValue '<missing>'), error=$($auth.error)")) | Out-Null

$websocket = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/websocket"
$websocketJson = Get-Json -Content $websocket.content
$websocketOk = $websocket.status -eq 200 -and (Test-JsonKeys -Json $websocketJson -Keys @("activeSessions", "reconnectSuccessRatio"))
$results.Add((New-StepResult -Name "WebSocket Snapshot" -Status $(if ($websocketOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($websocket.status), activeSessions=$(Get-JsonPropertyValue -Json $websocketJson -Name 'activeSessions' -DefaultValue '<missing>'), reconnectRatio=$(Get-JsonPropertyValue -Json $websocketJson -Name 'reconnectSuccessRatio' -DefaultValue '<missing>'), error=$($websocket.error)")) | Out-Null

$canary = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/websocketcanary?refresh=false"
$canaryJson = Get-Json -Content $canary.content
$canaryOk = $canary.status -eq 200 -and (Test-JsonKeys -Json $canaryJson -Keys @("alertState", "success", "latencyMs"))
$results.Add((New-StepResult -Name "WebSocket Canary Snapshot" -Status $(if ($canaryOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($canary.status), alertState=$(Get-JsonPropertyValue -Json $canaryJson -Name 'alertState' -DefaultValue '<missing>'), success=$(Get-JsonPropertyValue -Json $canaryJson -Name 'success' -DefaultValue '<missing>'), latencyMs=$(Get-JsonPropertyValue -Json $canaryJson -Name 'latencyMs' -DefaultValue '<missing>'), error=$($canary.error)")) | Out-Null

$idempotency = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/idempotency"
$idempotencyJson = Get-Json -Content $idempotency.content
$idempotencyOk = $idempotency.status -eq 200 -and (Test-JsonKeys -Json $idempotencyJson -Keys @("alertState", "totalRecords", "expiredRecords"))
$results.Add((New-StepResult -Name "Idempotency Snapshot" -Status $(if ($idempotencyOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($idempotency.status), alertState=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'alertState' -DefaultValue '<missing>'), totalRecords=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'totalRecords' -DefaultValue '<missing>'), expiredRecords=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'expiredRecords' -DefaultValue '<missing>'), error=$($idempotency.error)")) | Out-Null

$opsAlerts = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/opsalerts"
$opsAlertsJson = Get-Json -Content $opsAlerts.content
$opsAlertsOk = $opsAlerts.status -eq 200 -and (Test-JsonKeys -Json $opsAlertsJson -Keys @("totalAlertCount", "webhookSentCount", "webhookFailedCount"))
$results.Add((New-StepResult -Name "Ops Alert Snapshot" -Status $(if ($opsAlertsOk) { "PASSED" } else { "FAILED" }) -Detail "status=$($opsAlerts.status), totalAlerts=$(Get-JsonPropertyValue -Json $opsAlertsJson -Name 'totalAlertCount' -DefaultValue '<missing>'), webhookSent=$(Get-JsonPropertyValue -Json $opsAlertsJson -Name 'webhookSentCount' -DefaultValue '<missing>'), webhookFailed=$(Get-JsonPropertyValue -Json $opsAlertsJson -Name 'webhookFailedCount' -DefaultValue '<missing>'), error=$($opsAlerts.error)")) | Out-Null

if ($feedOk) {
  $notes.Add("Feed latency thresholds: warningP95=$(Get-JsonPropertyValue -Json $feedJson -Name 'warningP95Ms'), warningP99=$(Get-JsonPropertyValue -Json $feedJson -Name 'warningP99Ms'), criticalP99=$(Get-JsonPropertyValue -Json $feedJson -Name 'criticalP99Ms')") | Out-Null
}
if ($authOk) {
  $notes.Add("Auth refresh churn: attempts=$(Get-JsonPropertyValue -Json $authJson -Name 'totalRefreshAttempts'), invalidRatio=$(Get-JsonPropertyValue -Json $authJson -Name 'invalidRefreshRatio'), alertState=$(Get-JsonPropertyValue -Json $authJson -Name 'alertState')") | Out-Null
}
if ($websocketOk -and $canaryOk) {
  $notes.Add("WebSocket runtime: activeSessions=$(Get-JsonPropertyValue -Json $websocketJson -Name 'activeSessions'), reconnectRatio=$(Get-JsonPropertyValue -Json $websocketJson -Name 'reconnectSuccessRatio'), canaryAlertState=$(Get-JsonPropertyValue -Json $canaryJson -Name 'alertState')") | Out-Null
}
if ($idempotencyOk) {
  $notes.Add("Idempotency cleanup: totalRecords=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'totalRecords'), expiredRecords=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'expiredRecords'), alertState=$(Get-JsonPropertyValue -Json $idempotencyJson -Name 'alertState')") | Out-Null
}

$failedCount = (@($results | Where-Object { $_.Status -ne "PASSED" })).Count
$overallStatus = if ($failedCount -eq 0) { "PASSED" } else { "FAILED" }

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Runtime Observability Review Checklist")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("")
$lines.Add("Coverage:")
$lines.Add("- feed latency snapshot")
$lines.Add("- auth session churn snapshot")
$lines.Add("- websocket + canary snapshot")
$lines.Add("- idempotency cleanup snapshot")
$lines.Add("- ops alert counter summary")
$lines.Add("")
$lines.Add("| Step | Status | Detail |")
$lines.Add("|---|---|---|")
foreach ($result in $results) {
  $lines.Add("| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) |")
}
if ($notes.Count -gt 0) {
  $lines.Add("")
  $lines.Add("## Notes")
  foreach ($note in $notes) {
    $lines.Add("- $($note.Replace('|', '/'))")
  }
}

$lines | Set-Content -Path $reportPath

Write-Host "Runtime observability review checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $failedCount"

if ($overallStatus -ne "PASSED" -and -not $NoFail) {
  exit 1
}
