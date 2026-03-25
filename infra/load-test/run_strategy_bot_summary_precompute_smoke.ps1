param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [int]$TimeoutSec = 20,
  [int]$PollAttempts = 16,
  [int]$PollIntervalSec = 60,
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

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$baselineHealthComponent = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health/strategyBotSummaries"
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
Assert-Condition -Results $results -Name "Summary Health Baseline" -Condition $baselineHealthReachable -Detail $baselineHealthDetail

$baselineActuator = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/strategybotsummaries"
$baselineJson = if ($baselineActuator.content) { $baselineActuator.content | ConvertFrom-Json } else { $null }
$baselineTickCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "scheduledTickCount")
$baselineSuccessCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshSuccessCount")
$baselineFailureCount = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshFailureCount")
$baselineLastRefreshAt = [string](Get-ObjectPropertyValue -Object $baselineJson -Name "lastRefreshAt")
$baselineLastRefreshedBotCount = [int](Get-ObjectPropertyValue -Object $baselineJson -Name "lastRefreshedBotCount")
$refreshIntervalSeconds = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "refreshIntervalSeconds")
$activityWindowSeconds = [long](Get-ObjectPropertyValue -Object $baselineJson -Name "activityWindowSeconds")
$batchSize = [int](Get-ObjectPropertyValue -Object $baselineJson -Name "batchSize")
Assert-Condition -Results $results -Name "Summary Actuator Baseline" -Condition ($baselineActuator.status -eq 200 -and $refreshIntervalSeconds -gt 0 -and $activityWindowSeconds -gt 0 -and $batchSize -gt 0) -Detail "status=$($baselineActuator.status), interval=$refreshIntervalSeconds, activityWindow=$activityWindowSeconds, batchSize=$batchSize, lastRefreshAt=$baselineLastRefreshAt, lastRefreshedBotCount=$baselineLastRefreshedBotCount"

$registerBody = @{
  username = "bot_summary_$suffix"
  email    = "bot_summary_$suffix@test.com"
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

$portfolioRequestId = "bot-summary-portfolio-$suffix"
$portfolioHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "X-Request-Id"    = $portfolioRequestId
  "Idempotency-Key" = "bot-summary-portfolio-$suffix"
}

$portfolioCreate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $portfolioHeaders -Body @{
  name       = "Bot Summary Portfolio $suffix"
  ownerId    = $userId
  visibility = "PRIVATE"
}
$portfolioJson = if ($portfolioCreate.content) { $portfolioCreate.content | ConvertFrom-Json } else { $null }
$portfolioId = [string](Get-ObjectPropertyValue -Object $portfolioJson -Name "id")
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($portfolioCreate.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "status=$($portfolioCreate.status)"

$botCreate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/strategy-bots" -Headers $authHeaders -Body @{
  linkedPortfolioId      = $portfolioId
  name                   = "Summary Smoke $suffix"
  description            = "Summary precompute smoke"
  status                 = "READY"
  market                 = "CRYPTO"
  symbol                 = "BTCUSDT"
  timeframe              = "1H"
  entryRules             = @{ all = @("price_above_ma_3") }
  exitRules              = @{ any = @("take_profit_hit") }
  maxPositionSizePercent = 50
  takeProfitPercent      = 2
  cooldownMinutes        = 15
}
$botJson = if ($botCreate.content) { $botCreate.content | ConvertFrom-Json } else { $null }
$botId = [string](Get-ObjectPropertyValue -Object $botJson -Name "id")
Assert-Condition -Results $results -Name "Create Strategy Bot" -Condition ($botCreate.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($botId)) -Detail "status=$($botCreate.status)"

$runRequest = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/strategy-bots/$botId/runs" -Headers $authHeaders -Body @{
  runMode = "BACKTEST"
}
$runRequestJson = if ($runRequest.content) { $runRequest.content | ConvertFrom-Json } else { $null }
$runId = [string](Get-ObjectPropertyValue -Object $runRequestJson -Name "id")
Assert-Condition -Results $results -Name "Request Strategy Bot Run" -Condition ($runRequest.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($runId)) -Detail "status=$($runRequest.status)"

$finalSnapshotJson = $baselineJson
$schedulerObserved = $false
$refreshObserved = $false

for ($attempt = 1; $attempt -le $PollAttempts; $attempt++) {
  Start-Sleep -Seconds $PollIntervalSec
  $actuatorResponse = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/strategybotsummaries"
  if ($actuatorResponse.status -ne 200 -or [string]::IsNullOrWhiteSpace($actuatorResponse.content)) {
    continue
  }

  $finalSnapshotJson = $actuatorResponse.content | ConvertFrom-Json
  $tickCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "scheduledTickCount")
  $successCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSuccessCount")
  $failureCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshFailureCount")
  $lastRefreshAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshAt")
  $lastRefreshedBotCount = [int](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshedBotCount")

  if ($tickCount -gt $baselineTickCount) {
    $schedulerObserved = $true
  }

  if ($successCount -gt $baselineSuccessCount -and $lastRefreshedBotCount -gt 0 -and $lastRefreshAt -ne $baselineLastRefreshAt) {
    $refreshObserved = $true
  }

  if ($schedulerObserved -and $refreshObserved) {
    break
  }

  if ($failureCount -gt $baselineFailureCount) {
    break
  }
}

$finalTickCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "scheduledTickCount")
$finalSuccessCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshSuccessCount")
$finalFailureCount = [long](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "refreshFailureCount")
$finalLastRefreshAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshAt")
$finalLastSuccessAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastSuccessAt")
$finalLastFailureAt = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastFailureAt")
$finalLastRefreshedBotCount = [int](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastRefreshedBotCount")
$finalLastError = [string](Get-ObjectPropertyValue -Object $finalSnapshotJson -Name "lastError")

$finalHealthComponent = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health/strategyBotSummaries"
$finalHealthComponentJson = if ($finalHealthComponent.content) { $finalHealthComponent.content | ConvertFrom-Json } else { $null }
$finalHealthStatus = [string](Get-ObjectPropertyValue -Object $finalHealthComponentJson -Name "status")
$finalHealthDetails = Get-ObjectPropertyValue -Object $finalHealthComponentJson -Name "details"
$finalHealthTickCount = [long](Get-ObjectPropertyValue -Object $finalHealthDetails -Name "scheduledTickCount")
$finalHealthConverged = if ($finalHealthComponent.status -eq 200) {
  $finalHealthStatus -eq "UP"
} elseif ($finalHealthComponent.status -eq 404) {
  $schedulerObserved -and $refreshObserved
} else {
  $false
}
$finalHealthDetail = if ($finalHealthComponent.status -eq 404) {
  "status=404, componentStatus=<unavailable>, fallbackTickDelta=$($finalTickCount - $baselineTickCount), fallbackRefreshObserved=$refreshObserved"
} else {
  "status=$($finalHealthComponent.status), componentStatus=$finalHealthStatus, scheduledTickCount=$finalHealthTickCount"
}

Assert-Condition -Results $results -Name "Summary Scheduler Tick Observed" -Condition $schedulerObserved -Detail "baseline=$baselineTickCount final=$finalTickCount"
Assert-Condition -Results $results -Name "Summary Refresh Observed" -Condition $refreshObserved -Detail "successDelta=$($finalSuccessCount - $baselineSuccessCount) failureDelta=$($finalFailureCount - $baselineFailureCount) lastRefreshedBotCount=$finalLastRefreshedBotCount lastRefreshAt=$finalLastRefreshAt lastFailureAt=$finalLastFailureAt lastError=$finalLastError"
Assert-Condition -Results $results -Name "Summary Health Converged" -Condition $finalHealthConverged -Detail $finalHealthDetail

$botRead = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/strategy-bots/$botId" -Headers $authHeaders
$botReadJson = if ($botRead.content) { $botRead.content | ConvertFrom-Json } else { $null }
$botReadStatus = [string](Get-ObjectPropertyValue -Object $botReadJson -Name "status")
Assert-Condition -Results $results -Name "Bot Detail Available" -Condition ($botRead.status -eq 200 -and $botReadStatus -eq "READY") -Detail "status=$($botRead.status), botStatus=$botReadStatus"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "strategy-bot-summary-precompute-smoke-$timestamp.md"

$lines = @()
$lines += "# Strategy Bot Summary Precompute Smoke"
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
$lines += "- Baseline Last Refresh At: $(if ([string]::IsNullOrWhiteSpace($baselineLastRefreshAt)) { '<none>' } else { $baselineLastRefreshAt })"
$lines += "- Final Last Refresh At: $(if ([string]::IsNullOrWhiteSpace($finalLastRefreshAt)) { '<none>' } else { $finalLastRefreshAt })"
$lines += "- Final Last Success At: $(if ([string]::IsNullOrWhiteSpace($finalLastSuccessAt)) { '<none>' } else { $finalLastSuccessAt })"
$lines += "- Final Last Failure At: $(if ([string]::IsNullOrWhiteSpace($finalLastFailureAt)) { '<none>' } else { $finalLastFailureAt })"
$lines += "- Baseline Last Refreshed Bot Count: $baselineLastRefreshedBotCount"
$lines += "- Final Last Refreshed Bot Count: $finalLastRefreshedBotCount"
$lines += "- Last Error: $(if ([string]::IsNullOrWhiteSpace($finalLastError)) { '<none>' } else { $finalLastError })"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Strategy bot summary precompute smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
