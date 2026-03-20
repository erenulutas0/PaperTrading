param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 18084,
  [int]$StartupTimeoutSec = 180,
  [int]$PriceTimeoutSec = 90,
  [int]$ObservationWindowSec = 25,
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$SkipAppStart,
  [switch]$PreserveAppAfterRun,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds(20)

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ($listener.LocalEndpoint).Port
  $listener.Stop()
  return $port
}

function Test-PortOpen {
  param([int]$Port)

  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    $ok = $async.AsyncWaitHandle.WaitOne(200)
    if (-not $ok) {
      return $false
    }
    $client.EndConnect($async) | Out-Null
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

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
      $jsonBody = $Body | ConvertTo-Json -Depth 10
      $requestMessage.Content = [System.Net.Http.StringContent]::new($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

    return [pscustomobject]@{
      ok      = $response.IsSuccessStatusCode
      status  = [int]$response.StatusCode
      content = $content
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      ok      = $false
      status  = 0
      content = ""
      error   = $_.Exception.Message
    }
  }
}

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $response = Invoke-Request -Method "GET" -Url $Url
    if ($response.status -eq 200) {
      try {
        $json = $response.content | ConvertFrom-Json
        if ([string]$json.status -eq "UP") {
          return $true
        }
      } catch {
      }
    }
    Start-Sleep -Seconds 2
  }

  return $false
}

function Wait-NonEmptyPrices {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $response = Invoke-Request -Method "GET" -Url $Url
    if ($response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($response.content)) {
      try {
        $json = $response.content | ConvertFrom-Json
        $props = @($json.PSObject.Properties | Where-Object {
            $_.Value -is [double] -or $_.Value -is [decimal] -or $_.Value -is [int]
          })
        if ($props.Count -gt 0) {
          return [pscustomobject]@{
            Ready      = $true
            SymbolCount = $props.Count
            Raw         = $response.content
          }
        }
      } catch {
      }
    }
    Start-Sleep -Seconds 2
  }

  return [pscustomobject]@{
    Ready       = $false
    SymbolCount = 0
    Raw         = ""
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

function Get-ContentText {
  param([string]$Path)

  if (-not (Test-Path $Path)) {
    return ""
  }

  return Get-Content -Path $Path -Raw
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$reportPath = Join-Path $ReportsDir "portfolio-pagination-warning-smoke-$timestamp.md"
$appLogPath = Join-Path $ReportsDir "portfolio-pagination-warning-app-$timestamp.log"
$appErrLogPath = Join-Path $ReportsDir "portfolio-pagination-warning-app-$timestamp.err.log"

$portSwitchNote = ""
if (-not $SkipAppStart -and (Test-PortOpen -Port $ServerPort)) {
  $requestedPort = $ServerPort
  $ServerPort = Get-FreeTcpPort
  $portSwitchNote = "Requested server port $requestedPort was in use; switched to $ServerPort."
}

$baseUrl = "http://localhost:$ServerPort"
$healthUrl = "$baseUrl/actuator/health"
$priceUrl = "$baseUrl/api/v1/market/prices"
$ownerPageUrl = ""
$discoverUrl = "$baseUrl/api/v1/portfolios/discover?page=0&size=20"
$appProcess = $null
$appStartedByScript = $false
$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'

if ($portSwitchNote) {
  $notes.Add($portSwitchNote) | Out-Null
}

try {
  if (-not $SkipAppStart) {
    $coreApiPath = Resolve-Path $CoreApiDir
    $repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
    $mavenUserHome = Join-Path $repoRoot ".m2"
    $mavenRepoPath = Join-Path $repoRoot ".m2\repository"
    $null = New-Item -ItemType Directory -Path $mavenUserHome -Force
    $null = New-Item -ItemType Directory -Path $mavenRepoPath -Force
    $appCommand = @(
      "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/finance_db'",
      "`$env:SPRING_DATASOURCE_USERNAME='postgres'",
      "`$env:SPRING_DATASOURCE_PASSWORD='password'",
      "`$env:REDIS_URL='redis://localhost:6379'",
      "`$env:APP_CORS_ALLOWED_ORIGIN_PATTERNS='http://localhost:3005'",
      "`$env:APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS='http://localhost:3005'",
      "`$env:APP_ALERTING_ENABLED='false'",
      "`$env:APP_AUTH_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_FEED_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_SHEDLOCK_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_WEBSOCKET_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_WEBSOCKET_CANARY_ENABLED='false'",
      "`$env:APP_MARKET_WS_ENABLED='false'",
      "`$env:JWT_SECRET='local-dev-secret-change-me-at-least-32-bytes'",
      "`$env:PORT='$ServerPort'",
      "`$env:MAVEN_USER_HOME='$mavenUserHome'",
      "`$env:MAVEN_OPTS='-Dmaven.repo.local=$mavenRepoPath'",
      "`$env:JAVA_TOOL_OPTIONS='-Duser.home=$repoRoot'",
      "Set-Location '$coreApiPath'",
      ".\\mvnw.cmd -q '-Dmaven.repo.local=$mavenRepoPath' spring-boot:run"
    ) -join "; "

    $appProcess = Start-Process -FilePath "powershell" `
      -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
      -WorkingDirectory $coreApiPath `
      -RedirectStandardOutput $appLogPath `
      -RedirectStandardError $appErrLogPath `
      -PassThru
    $appStartedByScript = $true
  }

  $healthy = Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec
  Assert-Condition -Results $results -Name "Backend Health" -Condition $healthy -Detail "url=$healthUrl"
  if (-not $healthy) {
    throw "Core API did not become healthy at $healthUrl within $StartupTimeoutSec seconds."
  }

  $pricesReady = Wait-NonEmptyPrices -Url $priceUrl -TimeoutSec $PriceTimeoutSec
  Assert-Condition -Results $results -Name "Market Prices Ready" -Condition $pricesReady.Ready -Detail "symbols=$($pricesReady.SymbolCount)"
  if (-not $pricesReady.Ready) {
    throw "Market prices did not hydrate at $priceUrl within $PriceTimeoutSec seconds."
  }

  $registerBody = @{
    username = "pagingwarn_$suffix"
    email    = "pagingwarn_$suffix@test.com"
    password = "P@ssw0rd!123456"
  }
  $register = Invoke-Request -Method "POST" -Url "$baseUrl/api/v1/auth/register" -Body $registerBody
  $registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
  $accessToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
  $userId = if ($null -ne $registerJson) { [string]$registerJson.id } else { "" }
  Assert-Condition -Results $results -Name "Seed User" -Condition ($register.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken) -and -not [string]::IsNullOrWhiteSpace($userId)) -Detail "status=$($register.status)"
  if ([string]::IsNullOrWhiteSpace($accessToken) -or [string]::IsNullOrWhiteSpace($userId)) {
    throw "Unable to seed smoke user via /api/v1/auth/register."
  }

  $createHeaders = @{
    "Authorization" = "Bearer $accessToken"
  }
  foreach ($index in 1..3) {
    $portfolioBody = @{
      name       = "Paging Warning Smoke $index $suffix"
      ownerId    = $userId
      visibility = "PUBLIC"
    }
    $portfolioCreate = Invoke-Request -Method "POST" -Url "$baseUrl/api/v1/portfolios" -Headers $createHeaders -Body $portfolioBody
    Assert-Condition -Results $results -Name "Seed Portfolio $index" -Condition ($portfolioCreate.status -eq 200) -Detail "status=$($portfolioCreate.status)"
  }

  $ownerPageUrl = "$baseUrl/api/v1/portfolios?ownerId=$([System.Uri]::EscapeDataString($userId))&page=0&size=20"
  $ownerPage = Invoke-Request -Method "GET" -Url $ownerPageUrl
  $ownerPageJson = if ($ownerPage.content) { $ownerPage.content | ConvertFrom-Json } else { $null }
  $ownerContentCount = if ($null -ne $ownerPageJson -and $null -ne $ownerPageJson.content) { @($ownerPageJson.content).Count } else { 0 }
  Assert-Condition -Results $results -Name "Owner Portfolio Page" -Condition ($ownerPage.status -eq 200 -and $ownerContentCount -ge 3) -Detail "status=$($ownerPage.status), content=$ownerContentCount"

  $discoverPage = Invoke-Request -Method "GET" -Url $discoverUrl
  $discoverJson = if ($discoverPage.content) { $discoverPage.content | ConvertFrom-Json } else { $null }
  $discoverContentCount = if ($null -ne $discoverJson -and $null -ne $discoverJson.content) { @($discoverJson.content).Count } else { 0 }
  Assert-Condition -Results $results -Name "Discover Portfolio Page" -Condition ($discoverPage.status -eq 200 -and $discoverContentCount -ge 1) -Detail "status=$($discoverPage.status), content=$discoverContentCount"

  Start-Sleep -Seconds $ObservationWindowSec

  $combinedLog = ((Get-ContentText -Path $appLogPath) + "`n" + (Get-ContentText -Path $appErrLogPath)).Trim()
  $hasSnapshotLog = $combinedLog -match "Captured snapshots for"
  $hasLeaderboardLog = $combinedLog -match "Leaderboard Job: Period"
  $hasWarning = $combinedLog -match "HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory"

  Assert-Condition -Results $results -Name "Snapshot Scheduler Activity" -Condition $hasSnapshotLog -Detail "pattern=Captured snapshots for"
  Assert-Condition -Results $results -Name "Leaderboard Scheduler Activity" -Condition $hasLeaderboardLog -Detail "pattern=Leaderboard Job: Period"
  Assert-Condition -Results $results -Name "No HHH90003004 Warning" -Condition (-not $hasWarning) -Detail "observationWindowSec=$ObservationWindowSec"

  if ($ObservationWindowSec -lt 10) {
    $notes.Add("Observation window was shorter than two liquidation cycles; increase -ObservationWindowSec for stronger liquidation confidence.") | Out-Null
  } else {
    $notes.Add("Observation window covers at least two liquidation scheduler intervals (5s fixed delay).") | Out-Null
  }
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes.Add($_.InvocationInfo.PositionMessage) | Out-Null
  }
} finally {
  if ($appStartedByScript -and $appProcess) {
    try {
      if (-not $PreserveAppAfterRun -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
      }
    } catch {
    }
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$overallStatus = if ($failedResults.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Portfolio Pagination Warning Smoke"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $baseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- App Log: $appLogPath"
$lines += "- App Error Log: $appErrLogPath"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $detail = [string]$result.Detail
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($detail.Replace('|', '/')) |"
}

if ($notes.Count -gt 0) {
  $lines += ""
  $lines += "## Notes"
  foreach ($note in $notes) {
    $lines += "- $($note.Replace('|', '/'))"
  }
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Portfolio pagination warning smoke report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
