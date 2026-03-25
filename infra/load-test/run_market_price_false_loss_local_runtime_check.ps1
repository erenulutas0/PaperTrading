param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 18090,
  [int]$StartupTimeoutSec = 180,
  [int]$TimeoutSec = 20,
  [string]$Symbol = "BTCUSDT",
  [double]$SeedPrice = 50000,
  [decimal]$Quantity = 0.001,
  [int]$Leverage = 1,
  [decimal]$MinEquity = 99980,
  [decimal]$MinProfitLoss = -20,
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)

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
    [string]$Url
  )

  try {
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    [pscustomobject]@{
      status  = [int]$response.StatusCode
      content = $content
      error   = ""
    }
  } catch {
    [pscustomobject]@{
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

  $false
}

function New-CheckResult {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  [pscustomobject]@{
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

function Get-LatestReport {
  param(
    [string]$Directory,
    [string]$Pattern
  )

  Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
}

function Stop-AppProcess {
  param([System.Diagnostics.Process]$Process)

  if ($null -eq $Process) {
    return
  }

  try {
    if (-not $Process.HasExited) {
      Stop-Process -Id $Process.Id -Force
    }
  } catch {
  }
}

function Start-LocalBackend {
  param(
    [string]$CoreApiDir,
    [int]$ServerPort,
    [string]$RepoRoot,
    [string]$ReportsDir,
    [string]$NamePrefix
  )

  $coreApiPath = Resolve-Path $CoreApiDir
  $mavenUserHome = Join-Path $RepoRoot ".m2"
  $mavenRepoPath = Join-Path $RepoRoot ".m2\repository"
  $null = New-Item -ItemType Directory -Path $mavenUserHome -Force
  $null = New-Item -ItemType Directory -Path $mavenRepoPath -Force

  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $appLogPath = Join-Path $ReportsDir "$NamePrefix-app-$timestamp.log"
  $appErrLogPath = Join-Path $ReportsDir "$NamePrefix-app-$timestamp.err.log"

  $appCommand = @(
    "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/finance_db'",
    "`$env:SPRING_DATASOURCE_USERNAME='postgres'",
    "`$env:SPRING_DATASOURCE_PASSWORD='password'",
    "`$env:REDIS_URL='redis://localhost:6379'",
    "`$env:APP_ALERTING_ENABLED='false'",
    "`$env:APP_FEED_OBSERVABILITY_ENABLED='false'",
    "`$env:APP_SHEDLOCK_OBSERVABILITY_ENABLED='false'",
    "`$env:APP_WEBSOCKET_OBSERVABILITY_ENABLED='false'",
    "`$env:APP_WEBSOCKET_CANARY_ENABLED='false'",
    "`$env:APP_MARKET_WS_ENABLED='false'",
    "`$env:APP_MARKET_MANUAL_SEED_ENABLED='true'",
    "`$env:JWT_SECRET='local-dev-secret-change-me-at-least-32-bytes'",
    "`$env:PORT='$ServerPort'",
    "`$env:MAVEN_USER_HOME='$mavenUserHome'",
    "`$env:MAVEN_OPTS='-Dmaven.repo.local=$mavenRepoPath'",
    "`$env:JAVA_TOOL_OPTIONS='-Duser.home=$RepoRoot'",
    "Set-Location '$coreApiPath'",
    ".\\mvnw.cmd -q '-Dmaven.repo.local=$mavenRepoPath' spring-boot:run"
  ) -join "; "

  $process = Start-Process -FilePath "powershell" `
    -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
    -WorkingDirectory $coreApiPath `
    -RedirectStandardOutput $appLogPath `
    -RedirectStandardError $appErrLogPath `
    -PassThru

  [pscustomobject]@{
    Process    = $process
    AppLogPath = $appLogPath
    ErrLogPath = $appErrLogPath
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "market-price-false-loss-local-runtime-check-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$baseUrl = "http://localhost:$ServerPort"
$healthUrl = "$baseUrl/actuator/health"
$phaseOne = $null
$phaseTwo = $null
$childReportPath = ""
$childStatus = "UNKNOWN"
$portfolioId = ""
$cacheCount = $null
$detailEquity = $null
$leaderboardProfitLoss = $null

try {
  if (Test-PortOpen -Port $ServerPort) {
    Assert-Condition -Results $results -Name "Requested Server Port Available" -Condition $false -Detail "port=$ServerPort"
    throw "Requested server port $ServerPort is already in use. Stop the competing backend or choose a different -ServerPort."
  }

  $phaseOne = Start-LocalBackend -CoreApiDir $CoreApiDir -ServerPort $ServerPort -RepoRoot $repoRoot -ReportsDir $ReportsDir -NamePrefix "market-price-false-loss-phase1"
  $healthyPhaseOne = Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec
  Assert-Condition -Results $results -Name "Phase 1 Health" -Condition $healthyPhaseOne -Detail "url=$healthUrl"
  if (-not $healthyPhaseOne) {
    throw "Phase 1 backend did not become healthy."
  }

  $beforeChild = Get-LatestReport -Directory $ReportsDir -Pattern "market-price-false-loss-staging-checklist-*.md"
  $beforeTimestamp = if ($null -ne $beforeChild) { $beforeChild.LastWriteTimeUtc } else { [datetime]::MinValue }

  & (Join-Path $PSScriptRoot "run_market_price_false_loss_staging_checklist.ps1") `
    -BaseUrl $baseUrl `
    -ReportsDir $ReportsDir `
    -Symbol $Symbol `
    -Quantity $Quantity `
    -Leverage $Leverage `
    -ManualSeedPrice $SeedPrice `
    -MinEquity $MinEquity `
    -MinProfitLoss $MinProfitLoss `
    -NoFail

  $afterChild = Get-LatestReport -Directory $ReportsDir -Pattern "market-price-false-loss-staging-checklist-*.md"
  if ($null -ne $afterChild -and $afterChild.LastWriteTimeUtc -ge $beforeTimestamp) {
    $childReportPath = $afterChild.FullName
    $childContent = Get-Content -Path $childReportPath -Raw
    if ($childContent -match "- Status:\s+\*\*(PASSED|FAILED)\*\*") {
      $childStatus = $Matches[1]
    }
    if ($childContent -match "- Portfolio Id:\s+([0-9a-fA-F-]+)") {
      $portfolioId = $Matches[1]
    }
  }

  Assert-Condition -Results $results -Name "Phase 1 Child Report Created" -Condition (-not [string]::IsNullOrWhiteSpace($childReportPath)) -Detail "$(if ($childReportPath) { $childReportPath } else { '<none>' })"
  Assert-Condition -Results $results -Name "Phase 1 Checklist Passed" -Condition ($childStatus -eq "PASSED") -Detail "status=$childStatus"
  Assert-Condition -Results $results -Name "Portfolio Id Captured" -Condition (-not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "$(if ($portfolioId) { $portfolioId } else { '<none>' })"

  Stop-AppProcess -Process $phaseOne.Process
  Start-Sleep -Seconds 3

  $phaseTwo = Start-LocalBackend -CoreApiDir $CoreApiDir -ServerPort $ServerPort -RepoRoot $repoRoot -ReportsDir $ReportsDir -NamePrefix "market-price-false-loss-phase2"
  $healthyPhaseTwo = Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec
  Assert-Condition -Results $results -Name "Phase 2 Health" -Condition $healthyPhaseTwo -Detail "url=$healthUrl"
  if (-not $healthyPhaseTwo) {
    throw "Phase 2 backend did not become healthy."
  }

  $priceStatus = Invoke-Request -Method "GET" -Url "$baseUrl/actuator/marketprices"
  $priceStatusJson = if ($priceStatus.content) { $priceStatus.content | ConvertFrom-Json } else { $null }
  $cacheCount = if ($null -ne $priceStatusJson) { [int](($priceStatusJson | Select-Object -ExpandProperty count)) } else { -1 }
  Assert-Condition -Results $results -Name "Price Cache Status Read" -Condition ($priceStatus.status -eq 200) -Detail "status=$($priceStatus.status), count=$cacheCount"
  if ($cacheCount -eq 0) {
    $notes.Add("Phase 2 restarted with an empty in-memory price cache before portfolio/leaderboard reads.") | Out-Null
  } elseif ($cacheCount -gt 0) {
    $notes.Add("Phase 2 restarted with $cacheCount cached market price(s); local environment likely refreshed prices via REST on startup before validation.") | Out-Null
  }

  $portfolioDetail = Invoke-Request -Method "GET" -Url "$baseUrl/api/v1/portfolios/$portfolioId"
  $detailJson = if ($portfolioDetail.content) { $portfolioDetail.content | ConvertFrom-Json } else { $null }
  $detailEquity = if ($null -ne $detailJson) { [decimal]($detailJson | Select-Object -ExpandProperty totalEquity) } else { [decimal]::MinValue }
  $detailReturn = if ($null -ne $detailJson) { [decimal]($detailJson | Select-Object -ExpandProperty returnPercentageALL) } else { [decimal]::MinValue }
  $detailFloor = [decimal](($MinProfitLoss / 100000) * 100)
  Assert-Condition -Results $results -Name "Phase 2 Portfolio Equity Floor" -Condition ($portfolioDetail.status -eq 200 -and $detailEquity -ge $MinEquity) -Detail "status=$($portfolioDetail.status), equity=$detailEquity, floor=$MinEquity"
  Assert-Condition -Results $results -Name "Phase 2 Portfolio Return Floor" -Condition ($portfolioDetail.status -eq 200 -and $detailReturn -ge $detailFloor) -Detail "status=$($portfolioDetail.status), return=$detailReturn, floor=$detailFloor"

  $leaderboardResponse = Invoke-Request -Method "GET" -Url "$baseUrl/api/v1/leaderboards?period=ALL&sortBy=PROFIT_LOSS&direction=DESC&page=0&size=100"
  $leaderboardJson = if ($leaderboardResponse.content) { $leaderboardResponse.content | ConvertFrom-Json } else { $null }
  $entries = if ($null -ne $leaderboardJson) { @($leaderboardJson.content) } else { @() }
  $entryCount = @($entries).Count
  $entry = $entries | Where-Object { [string]$_.portfolioId -eq $portfolioId } | Select-Object -First 1
  $leaderboardProfitLoss = if ($null -ne $entry) { [decimal]$entry.profitLoss } else { [decimal]::MinValue }
  $leaderboardEquity = if ($null -ne $entry) { [decimal]$entry.totalEquity } else { [decimal]::MinValue }
  Assert-Condition -Results $results -Name "Phase 2 Leaderboard Entry Present" -Condition ($null -ne $entry) -Detail "status=$($leaderboardResponse.status), entries=$entryCount"
  Assert-Condition -Results $results -Name "Phase 2 Leaderboard Profit Floor" -Condition ($null -ne $entry -and $leaderboardProfitLoss -ge $MinProfitLoss) -Detail "profitLoss=$leaderboardProfitLoss, floor=$MinProfitLoss"
  Assert-Condition -Results $results -Name "Phase 2 Leaderboard Equity Floor" -Condition ($null -ne $entry -and $leaderboardEquity -ge $MinEquity) -Detail "equity=$leaderboardEquity, floor=$MinEquity"
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes.Add($_.InvocationInfo.PositionMessage) | Out-Null
  }
} finally {
  if ($null -ne $phaseOne) {
    Stop-AppProcess -Process $phaseOne.Process
  }
  if ($null -ne $phaseTwo) {
    Stop-AppProcess -Process $phaseTwo.Process
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$overallStatus = if ($failedResults.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Market Price False-Loss Local Runtime Check"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $baseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Child Checklist Status: **$childStatus**"
$lines += "- Child Checklist Report: $(if ($childReportPath) { $childReportPath } else { '<none>' })"
$lines += "- Portfolio Id: $(if ($portfolioId) { $portfolioId } else { '<none>' })"
$lines += "- Phase 1 Log: $(if ($null -ne $phaseOne) { $phaseOne.AppLogPath } else { '<none>' })"
$lines += "- Phase 1 Err Log: $(if ($null -ne $phaseOne) { $phaseOne.ErrLogPath } else { '<none>' })"
$lines += "- Phase 2 Log: $(if ($null -ne $phaseTwo) { $phaseTwo.AppLogPath } else { '<none>' })"
$lines += "- Phase 2 Err Log: $(if ($null -ne $phaseTwo) { $phaseTwo.ErrLogPath } else { '<none>' })"
$lines += "- Phase 2 Price Cache Count: $(if ($null -ne $cacheCount) { $cacheCount } else { '<none>' })"
$lines += "- Phase 2 Detail Equity: $(if ($null -ne $detailEquity) { $detailEquity } else { '<none>' })"
$lines += "- Phase 2 Leaderboard Profit/Loss: $(if ($null -ne $leaderboardProfitLoss) { $leaderboardProfitLoss } else { '<none>' })"
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

Write-Host "Market price false-loss local runtime report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
