param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
  [string]$Symbol = "BTCUSDT",
  [decimal]$Quantity = 0.001,
  [int]$Leverage = 1,
  [double]$ManualSeedPrice = 0,
  [decimal]$MinEquity = 99980,
  [decimal]$MinProfitLoss = -20,
  [int]$LeaderboardPageSize = 100,
  [int]$LeaderboardMaxPages = 10,
  [int]$TimeoutSec = 20,
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

  $prop.Value
}

function Register-User {
  param(
    [string]$BaseUrl,
    [string]$Username,
    [string]$Email
  )

  $response = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
    username = $Username
    email    = $Email
    password = "P@ssw0rd!123456"
  }

  $json = if ($response.content) { $response.content | ConvertFrom-Json } else { $null }
  [pscustomobject]@{
    Response = $response
    Json     = $json
  }
}

function Wait-ForApiCondition {
  param(
    [scriptblock]$Probe,
    [scriptblock]$Matcher,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $result = & $Probe
    $matched = $false
    try {
      $matched = [bool](& $Matcher $result)
    } catch {
      $matched = $false
    }
    if ($matched) {
      return $result
    }
    Start-Sleep -Milliseconds 400
  }

  & $Probe
}

function Find-LeaderboardEntry {
  param(
    [string]$BaseUrl,
    [string]$PortfolioId,
    [int]$PageSize,
    [int]$MaxPages
  )

  for ($page = 0; $page -lt $MaxPages; $page++) {
    $response = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/leaderboards?period=ALL&sortBy=PROFIT_LOSS&direction=DESC&page=$page&size=$PageSize"
    $json = if ($response.content) { $response.content | ConvertFrom-Json } else { $null }
    $content = if ($null -ne $json) { @(Get-ObjectPropertyValue -Object $json -Name "content") } else { @() }

    foreach ($entry in $content) {
      if ([string](Get-ObjectPropertyValue -Object $entry -Name "portfolioId") -eq $PortfolioId) {
        return [pscustomobject]@{
          Response = $response
          Json     = $json
          Entry    = $entry
          Page     = $page
        }
      }
    }

    if ($content.Count -lt $PageSize) {
      break
    }
  }

  [pscustomobject]@{
    Response = $null
    Json     = $null
    Entry    = $null
    Page     = -1
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "market-price-false-loss-staging-checklist-$timestamp.md"

$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$portfolioId = ""
$portfolioName = ""
$detailEquity = $null
$detailReturnAll = $null
$leaderboardProfitLoss = $null
$leaderboardReturn = $null
$leaderboardEquity = $null
$manualSeedStatus = "not-requested"

try {
  $health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
  Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

  $ownerRegistration = Register-User -BaseUrl $BaseUrl -Username "false_loss_$suffix" -Email "false_loss_$suffix@test.com"
  $ownerJson = $ownerRegistration.Json
  $ownerId = [string](Get-ObjectPropertyValue -Object $ownerJson -Name "id")
  Assert-Condition -Results $results -Name "Register Owner" -Condition ($ownerRegistration.Response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($ownerId)) -Detail "status=$($ownerRegistration.Response.status)"

  $portfolioName = "False Loss Check $suffix"
  $portfolioResponse = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Body @{
    name       = $portfolioName
    ownerId    = $ownerId
    visibility = "PUBLIC"
  }
  $portfolioJson = if ($portfolioResponse.content) { $portfolioResponse.content | ConvertFrom-Json } else { $null }
  $portfolioId = [string](Get-ObjectPropertyValue -Object $portfolioJson -Name "id")
  Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($portfolioResponse.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "status=$($portfolioResponse.status)"

  if ($ManualSeedPrice -gt 0) {
    $seedResponse = Invoke-Request -Method "POST" -Url "$BaseUrl/actuator/marketprices" -Body @{
      symbol = $Symbol
      price  = $ManualSeedPrice
    }
    $seedJson = if ($seedResponse.content) { $seedResponse.content | ConvertFrom-Json } else { $null }
    $seedAccepted = [bool](Get-ObjectPropertyValue -Object $seedJson -Name "accepted")
    $seededPrice = Get-ObjectPropertyValue -Object $seedJson -Name "seededPrice"
    $manualSeedStatus = "status=$($seedResponse.status), accepted=$seedAccepted, price=$seededPrice"
    Assert-Condition -Results $results -Name "Manual Price Seed" -Condition ($seedResponse.status -eq 200 -and $seedAccepted) -Detail $manualSeedStatus
  }

  $tradeResponse = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/trade/buy" -Body @{
    portfolioId = $portfolioId
    symbol      = $Symbol
    quantity    = $Quantity
    leverage    = $Leverage
    side        = "LONG"
  }
  Assert-Condition -Results $results -Name "Open Long Position" -Condition ($tradeResponse.status -eq 200) -Detail "status=$($tradeResponse.status)"

  $portfolioDetail = Wait-ForApiCondition -Probe {
    Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/portfolios/$portfolioId"
  } -Matcher {
    param($response)
    if ($response.status -ne 200 -or [string]::IsNullOrWhiteSpace($response.content)) {
      return $false
    }
    $json = $response.content | ConvertFrom-Json
    $items = @(Get-ObjectPropertyValue -Object $json -Name "items")
    return $items.Count -ge 1
  } -TimeoutSec 10
  $detailJson = if ($portfolioDetail.content) { $portfolioDetail.content | ConvertFrom-Json } else { $null }
  $detailEquity = [decimal](Get-ObjectPropertyValue -Object $detailJson -Name "totalEquity")
  $detailReturnAll = [decimal](Get-ObjectPropertyValue -Object $detailJson -Name "returnPercentageALL")
  $detailMinReturn = [decimal](($MinProfitLoss / 100000) * 100)
  Assert-Condition -Results $results -Name "Portfolio Detail Equity Floor" -Condition ($portfolioDetail.status -eq 200 -and $detailEquity -ge $MinEquity) -Detail "status=$($portfolioDetail.status), totalEquity=$detailEquity, floor=$MinEquity"
  Assert-Condition -Results $results -Name "Portfolio Detail Return Floor" -Condition ($portfolioDetail.status -eq 200 -and $detailReturnAll -ge $detailMinReturn) -Detail "status=$($portfolioDetail.status), returnAll=$detailReturnAll, floor=$detailMinReturn"

  $leaderboardLookup = Wait-ForApiCondition -Probe {
    Find-LeaderboardEntry -BaseUrl $BaseUrl -PortfolioId $portfolioId -PageSize $LeaderboardPageSize -MaxPages $LeaderboardMaxPages
  } -Matcher {
    param($result)
    return $null -ne $result.Entry
  } -TimeoutSec 10
  $leaderboardEntry = $leaderboardLookup.Entry
  $leaderboardProfitLoss = if ($null -ne $leaderboardEntry) { [decimal](Get-ObjectPropertyValue -Object $leaderboardEntry -Name "profitLoss") } else { [decimal]::MinValue }
  $leaderboardReturn = if ($null -ne $leaderboardEntry) { [decimal](Get-ObjectPropertyValue -Object $leaderboardEntry -Name "returnPercentage") } else { [decimal]::MinValue }
  $leaderboardEquity = if ($null -ne $leaderboardEntry) { [decimal](Get-ObjectPropertyValue -Object $leaderboardEntry -Name "totalEquity") } else { [decimal]::MinValue }
  Assert-Condition -Results $results -Name "Leaderboard Entry Present" -Condition ($null -ne $leaderboardEntry) -Detail "page=$($leaderboardLookup.Page)"
  Assert-Condition -Results $results -Name "Leaderboard Profit Floor" -Condition ($null -ne $leaderboardEntry -and $leaderboardProfitLoss -ge $MinProfitLoss) -Detail "profitLoss=$leaderboardProfitLoss, floor=$MinProfitLoss"
  Assert-Condition -Results $results -Name "Leaderboard Return Floor" -Condition ($null -ne $leaderboardEntry -and $leaderboardReturn -ge $detailMinReturn) -Detail "return=$leaderboardReturn, floor=$detailMinReturn"
  Assert-Condition -Results $results -Name "Leaderboard Equity Floor" -Condition ($null -ne $leaderboardEntry -and $leaderboardEquity -ge $MinEquity) -Detail "equity=$leaderboardEquity, floor=$MinEquity"
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes.Add($_.InvocationInfo.PositionMessage) | Out-Null
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

$lines = @()
$lines += "# Market Price False-Loss Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$(if ($allPassed) { 'PASSED' } else { 'FAILED' })**"
$lines += "- Symbol: $Symbol"
$lines += "- Quantity: $Quantity"
$lines += "- Leverage: $Leverage"
$lines += "- Portfolio Id: $(if ($portfolioId) { $portfolioId } else { '<none>' })"
$lines += "- Portfolio Name: $(if ($portfolioName) { $portfolioName } else { '<none>' })"
$lines += "- Manual seed: $manualSeedStatus"
$lines += "- Detail total equity: $(if ($null -ne $detailEquity) { $detailEquity } else { '<none>' })"
$lines += "- Detail return ALL: $(if ($null -ne $detailReturnAll) { $detailReturnAll } else { '<none>' })"
$lines += "- Leaderboard profit/loss: $(if ($null -ne $leaderboardProfitLoss) { $leaderboardProfitLoss } else { '<none>' })"
$lines += "- Leaderboard return: $(if ($null -ne $leaderboardReturn) { $leaderboardReturn } else { '<none>' })"
$lines += ""
$lines += "Coverage:"
$lines += "- create a fresh public portfolio"
$lines += "- open one BTCUSDT long position"
$lines += "- verify portfolio detail total equity and return do not collapse into false-loss territory"
$lines += "- verify portfolio leaderboard profit/loss and equity stay above the configured floor"
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

Write-Host "Market price false-loss checklist report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
