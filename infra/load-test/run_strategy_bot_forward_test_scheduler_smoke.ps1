param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [int]$TimeoutSec = 20,
  [int]$PollAttempts = 10,
  [int]$PollIntervalSec = 5,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )

  try {
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
    foreach ($key in $Headers.Keys) {
      [void]$requestMessage.Headers.TryAddWithoutValidation($key, [string]$Headers[$key])
    }

    if ($null -ne $Body) {
      $jsonBody = $Body | ConvertTo-Json -Depth 12
      $requestMessage.Content = [System.Net.Http.StringContent]::new($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $headerMap = @{}
    foreach ($header in $response.Headers) {
      $headerMap[$header.Key] = ($header.Value -join ",")
    }
    foreach ($header in $response.Content.Headers) {
      $headerMap[$header.Key] = ($header.Value -join ",")
    }

    return [pscustomobject]@{
      ok      = $response.IsSuccessStatusCode
      status  = [int]$response.StatusCode
      content = $content
      headers = $headerMap
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      ok      = $false
      status  = 0
      content = ""
      headers = @{}
      error   = $_.Exception.Message
    }
  }
}

function New-CheckResult {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  return [pscustomobject]@{
    Name   = $Name
    Passed = $Passed
    Detail = $Detail
  }
}

function Assert-Condition {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [bool]$Condition,
    [string]$Detail
  )

  $Results.Add((New-CheckResult -Name $Name -Passed $Condition -Detail $Detail)) | Out-Null
}

function Get-ObjectPropertyValue {
  param(
    [object]$Object,
    [string]$Name
  )

  if ($null -eq $Object) {
    return $null
  }

  $prop = $Object.PSObject.Properties[$Name]
  if ($null -eq $prop) {
    return $null
  }

  return $prop.Value
}

function Format-DetailFragment {
  param(
    [string]$Label,
    [string]$Value
  )

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return ""
  }

  $normalized = $Value.Replace("`r", " ").Replace("`n", " ").Replace("|", "/").Trim()
  if ($normalized.Length -gt 220) {
    $normalized = $normalized.Substring(0, 220) + "..."
  }
  return ", $Label=$normalized"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$baselineHealthComponent = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health/strategyBotForwardTests"
$baselineHealthComponentJson = if ($baselineHealthComponent.content) { $baselineHealthComponent.content | ConvertFrom-Json } else { $null }
$baselineHealthStatus = [string](Get-ObjectPropertyValue -Object $baselineHealthComponentJson -Name "status")
$baselineHealthDetails = Get-ObjectPropertyValue -Object $baselineHealthComponentJson -Name "details"
$baselineHealthTickCount = [long](Get-ObjectPropertyValue -Object $baselineHealthDetails -Name "scheduledTickCount")
$baselineHealthReachable = ($baselineHealthComponent.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($baselineHealthStatus)) -or $baselineHealthComponent.status -eq 404
$baselineHealthDetail = if ($baselineHealthComponent.status -eq 404) {
  "status=404, componentStatus=<unavailable>, note=health component details not exposed by runtime"
} else {
  "status=$($baselineHealthComponent.status), componentStatus=$baselineHealthStatus, scheduledTickCount=$baselineHealthTickCount"
}
Assert-Condition -Results $results -Name "Forward-Test Health Baseline" -Condition $baselineHealthReachable -Detail $baselineHealthDetail

$baselineActuator = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/strategybotforwardtests"
$baselineJson = if ($baselineActuator.content) { $baselineActuator.content | ConvertFrom-Json } else { $null }
$baselineTickCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "scheduledTickCount")
$baselineSuccessCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshSuccessCount")
$baselineFailureCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshFailureCount")
$baselineSkipCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshSkipCount")
$refreshIntervalSeconds = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshIntervalSeconds")
$staleThresholdSeconds = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "staleThresholdSeconds")
$baselineAlertState = [string](Get-ObjectPropertyValue -Object $baselineJson -Name "alertState")
$baselineLastTickAgeSeconds = [double](Get-ObjectPropertyValue -Object $baselineJson -Name "lastTickAgeSeconds")
Assert-Condition -Results $results -Name "Forward-Test Actuator Baseline" -Condition ($baselineActuator.status -eq 200 -and $refreshIntervalSeconds -gt 0 -and $staleThresholdSeconds -gt 0 -and -not [string]::IsNullOrWhiteSpace($baselineAlertState)) -Detail "status=$($baselineActuator.status), interval=$refreshIntervalSeconds, staleThreshold=$staleThresholdSeconds, alertState=$baselineAlertState, lastTickAgeSeconds=$baselineLastTickAgeSeconds"

$registerBody = @{
  username = "bot_forward_$suffix"
  email    = "bot_forward_$suffix@test.com"
  password = "P@ssw0rd!123456"
}

$register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $registerBody
$registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
$accessToken = [string](Get-ObjectPropertyValue -Object $registerJson -Name "accessToken")
$userId = [string](Get-ObjectPropertyValue -Object $registerJson -Name "id")
Assert-Condition -Results $results -Name "Register User" -Condition ($register.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken)) -Detail "status=$($register.status)"

$authHeaders = @{
  "Authorization" = "Bearer $accessToken"
}

$portfolioRequestId = "bot-forward-portfolio-$suffix"
$portfolioHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "X-Request-Id"    = $portfolioRequestId
  "Idempotency-Key" = "bot-forward-portfolio-$suffix"
}

$portfolioCreate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $portfolioHeaders -Body @{
  name       = "Bot Forward Portfolio $suffix"
  ownerId    = $userId
  visibility = "PRIVATE"
}
$portfolioJson = if ($portfolioCreate.content) { $portfolioCreate.content | ConvertFrom-Json } else { $null }
$portfolioId = [string](Get-ObjectPropertyValue -Object $portfolioJson -Name "id")
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($portfolioCreate.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "status=$($portfolioCreate.status)"

$botCreate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/strategy-bots" -Headers $authHeaders -Body @{
  linkedPortfolioId      = $portfolioId
  name                   = "Scheduler Forward Smoke $suffix"
  description            = "Forward-test scheduler smoke"
  status                 = "READY"
  market                 = "CRYPTO"
  symbol                 = "BTCUSDT"
  timeframe              = "1H"
  entryRules             = @{ all = @("price_above_ma_3") }
  exitRules              = @{ any = @("take_profit_hit") }
  maxPositionSizePercent = 50
  takeProfitPercent      = 2
  cooldownMinutes        = 1000000
}
$botJson = if ($botCreate.content) { $botCreate.content | ConvertFrom-Json } else { $null }
$botId = [string](Get-ObjectPropertyValue -Object $botJson -Name "id")
Assert-Condition -Results $results -Name "Create Strategy Bot" -Condition ($botCreate.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($botId)) -Detail "status=$($botCreate.status)"

$runRequest = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/strategy-bots/$botId/runs" -Headers $authHeaders -Body @{
  runMode = "FORWARD_TEST"
}
$runRequestJson = if ($runRequest.content) { $runRequest.content | ConvertFrom-Json } else { $null }
$runId = [string](Get-ObjectPropertyValue -Object $runRequestJson -Name "id")
Assert-Condition -Results $results -Name "Request Forward-Test Run" -Condition ($runRequest.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($runId)) -Detail "status=$($runRequest.status)"

$executeRun = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/strategy-bots/$botId/runs/$runId/execute" -Headers $authHeaders
$executeJson = if ($executeRun.content) { $executeRun.content | ConvertFrom-Json } else { $null }
$executeStatus = [string](Get-ObjectPropertyValue -Object $executeJson -Name "status")
$executeSummary = Get-ObjectPropertyValue -Object $executeJson -Name "summary"
$executePhase = [string](Get-ObjectPropertyValue -Object $executeSummary -Name "phase")
$executeDetail = "status=$($executeRun.status), runStatus=$executeStatus, phase=$executePhase"
$executeDetail += Format-DetailFragment -Label "error" -Value $executeRun.error
$executeDetail += Format-DetailFragment -Label "body" -Value $executeRun.content
Assert-Condition -Results $results -Name "Execute Forward-Test Run" -Condition ($executeRun.status -eq 200 -and ($executeStatus -eq "RUNNING" -or $executeStatus -eq "COMPLETED")) -Detail $executeDetail

$finalSnapshotJson = $baselineJson
$schedulerObserved = $false
$targetRunObserved = $false
$lastRunStatus = ""
$lastRunError = ""
$lastSkippedRunId = ""
$lastSkipReason = ""

for ($attempt = 1; $attempt -le $PollAttempts; $attempt++) {
  Start-Sleep -Seconds $PollIntervalSec
  $actuatorResponse = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/strategybotforwardtests"
  if ($actuatorResponse.status -ne 200 -or [string]::IsNullOrWhiteSpace($actuatorResponse.content)) {
    continue
  }

  $finalSnapshotJson = $actuatorResponse.content | ConvertFrom-Json
  $tickCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "scheduledTickCount")
  $successCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSuccessCount")
  $failureCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshFailureCount")
  $skipCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSkipCount")
  $lastRefreshedRunId = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshedRunId")
  $lastRunStatus = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshedRunStatus")
  $lastRunError = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastError")
  $lastSkippedRunId = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastSkippedRunId")
  $lastSkipReason = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastSkipReason")

  if ($tickCount -gt $baselineTickCount) {
    $schedulerObserved = $true
  }

  if ($lastRefreshedRunId -eq $runId -and $successCount -gt $baselineSuccessCount) {
    $targetRunObserved = $true
  }

  if ($schedulerObserved -and $targetRunObserved) {
    break
  }

  if ($failureCount -gt $baselineFailureCount -and $lastRefreshedRunId -eq $runId) {
    break
  }

  if ($skipCount -gt $baselineSkipCount -and $lastSkippedRunId -eq $runId) {
    break
  }
}

$finalTickCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "scheduledTickCount")
$finalSuccessCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSuccessCount")
$finalFailureCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshFailureCount")
$finalSkipCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSkipCount")
$finalLastRefreshedRunId = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshedRunId")
$lastRefreshAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshAt")
$lastSkipAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastSkipAt")
$finalAlertState = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "alertState")
$finalLastTickAgeSeconds = [double](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastTickAgeSeconds")
$finalStaleThresholdSeconds = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "staleThresholdSeconds")

$finalHealthComponent = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health/strategyBotForwardTests"
$finalHealthComponentJson = if ($finalHealthComponent.content) { $finalHealthComponent.content | ConvertFrom-Json } else { $null }
$finalHealthStatus = [string](Get-ObjectPropertyValue -Object $finalHealthComponentJson -Name "status")
$finalHealthDetails = Get-ObjectPropertyValue -Object $finalHealthComponentJson -Name "details"
$finalHealthTickCount = [long](Get-ObjectPropertyValue -Object $finalHealthDetails -Name "scheduledTickCount")
$healthFallbackClear = ($finalAlertState -eq "NONE" -and $finalLastTickAgeSeconds -le $finalStaleThresholdSeconds)
$finalHealthConverged = if ($finalHealthComponent.status -eq 200) {
  $finalHealthStatus -eq "UP"
} elseif ($finalHealthComponent.status -eq 404) {
  $healthFallbackClear
} else {
  $false
}
$finalHealthDetail = if ($finalHealthComponent.status -eq 404) {
  "status=404, componentStatus=<unavailable>, fallbackAlertState=$finalAlertState, lastTickAgeSeconds=$finalLastTickAgeSeconds, staleThresholdSeconds=$finalStaleThresholdSeconds"
} else {
  "status=$($finalHealthComponent.status), componentStatus=$finalHealthStatus, scheduledTickCount=$finalHealthTickCount"
}

Assert-Condition -Results $results -Name "Scheduler Tick Observed" -Condition $schedulerObserved -Detail "baseline=$baselineTickCount final=$finalTickCount"
Assert-Condition -Results $results -Name "Target Run Refreshed By Scheduler" -Condition ($targetRunObserved -and ($lastRunStatus -eq "RUNNING" -or $lastRunStatus -eq "COMPLETED")) -Detail "lastRunId=$finalLastRefreshedRunId lastStatus=$lastRunStatus lastError=$lastRunError successDelta=$($finalSuccessCount - $baselineSuccessCount) failureDelta=$($finalFailureCount - $baselineFailureCount) skipDelta=$($finalSkipCount - $baselineSkipCount) lastSkippedRunId=$lastSkippedRunId lastSkipReason=$lastSkipReason"
Assert-Condition -Results $results -Name "Forward-Test Health Converged" -Condition $finalHealthConverged -Detail $finalHealthDetail
Assert-Condition -Results $results -Name "Forward-Test Alert State Clear" -Condition $healthFallbackClear -Detail "alertState=$finalAlertState, lastTickAgeSeconds=$finalLastTickAgeSeconds, staleThresholdSeconds=$finalStaleThresholdSeconds"

$runRead = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/strategy-bots/$botId/runs/$runId" -Headers $authHeaders
$runReadJson = if ($runRead.content) { $runRead.content | ConvertFrom-Json } else { $null }
$runReadStatus = [string](Get-ObjectPropertyValue -Object $runReadJson -Name "status")
$runReadSummary = Get-ObjectPropertyValue -Object $runReadJson -Name "summary"
$lastEvaluatedOpenTime = Get-ObjectPropertyValue -Object $runReadSummary -Name "lastEvaluatedOpenTime"
$positionOpen = Get-ObjectPropertyValue -Object $runReadSummary -Name "positionOpen"
Assert-Condition -Results $results -Name "Run Snapshot Available" -Condition ($runRead.status -eq 200 -and $null -ne $lastEvaluatedOpenTime -and ($runReadStatus -eq "RUNNING" -or $runReadStatus -eq "COMPLETED" -or $runReadStatus -eq "FAILED")) -Detail "status=$($runRead.status), runStatus=$runReadStatus, lastEvaluatedOpenTime=$lastEvaluatedOpenTime, positionOpen=$positionOpen, lastRefreshAt=$lastRefreshAt"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "strategy-bot-forward-test-scheduler-smoke-$timestamp.md"

$lines = @()
$lines += "# Strategy Bot Forward-Test Scheduler Smoke"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Poll Attempts: $PollAttempts"
$lines += "- Poll Interval Seconds: $PollIntervalSec"
$lines += "- Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' })"
$lines += ""
$lines += "## Snapshot Delta"
$lines += ""
$lines += "- Baseline Tick Count: $baselineTickCount"
$lines += "- Final Tick Count: $finalTickCount"
$lines += "- Baseline Success Count: $baselineSuccessCount"
$lines += "- Final Success Count: $finalSuccessCount"
$lines += "- Baseline Failure Count: $baselineFailureCount"
$lines += "- Final Failure Count: $finalFailureCount"
$lines += "- Baseline Skip Count: $baselineSkipCount"
$lines += "- Final Skip Count: $finalSkipCount"
$lines += "- Baseline Health Status: $(if ([string]::IsNullOrWhiteSpace($baselineHealthStatus)) { '<unknown>' } else { $baselineHealthStatus })"
$lines += "- Final Health Status: $(if ([string]::IsNullOrWhiteSpace($finalHealthStatus)) { '<unknown>' } else { $finalHealthStatus })"
$lines += "- Baseline Alert State: $(if ([string]::IsNullOrWhiteSpace($baselineAlertState)) { '<unknown>' } else { $baselineAlertState })"
$lines += "- Final Alert State: $(if ([string]::IsNullOrWhiteSpace($finalAlertState)) { '<unknown>' } else { $finalAlertState })"
$lines += "- Baseline Last Tick Age Seconds: $baselineLastTickAgeSeconds"
$lines += "- Final Last Tick Age Seconds: $finalLastTickAgeSeconds"
$lines += "- Stale Threshold Seconds: $(if ($finalStaleThresholdSeconds -gt 0) { $finalStaleThresholdSeconds } else { $staleThresholdSeconds })"
$lines += "- Last Refreshed Run Id: $finalLastRefreshedRunId"
$lines += "- Last Refreshed Run Status: $lastRunStatus"
$lines += "- Last Skipped Run Id: $(if ([string]::IsNullOrWhiteSpace($lastSkippedRunId)) { '<none>' } else { $lastSkippedRunId })"
$lines += "- Last Skip Reason: $(if ([string]::IsNullOrWhiteSpace($lastSkipReason)) { '<none>' } else { $lastSkipReason })"
$lines += "- Last Skip At: $(if ([string]::IsNullOrWhiteSpace($lastSkipAt)) { '<none>' } else { $lastSkipAt })"
$lines += "- Last Error: $(if ([string]::IsNullOrWhiteSpace($lastRunError)) { '<none>' } else { $lastRunError })"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Strategy bot forward-test scheduler smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
