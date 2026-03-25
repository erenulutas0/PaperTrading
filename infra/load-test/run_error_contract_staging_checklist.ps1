param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
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
      $jsonBody = $Body | ConvertTo-Json -Depth 10
      $requestMessage.Content = [System.Net.Http.StringContent]::new($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

    $responseHeaders = @{}
    foreach ($header in $response.Headers) {
      $responseHeaders[$header.Key] = ($header.Value -join ",")
    }
    foreach ($header in $response.Content.Headers) {
      $responseHeaders[$header.Key] = ($header.Value -join ",")
    }

    return [pscustomobject]@{
      ok      = $response.IsSuccessStatusCode
      status  = [int]$response.StatusCode
      content = $content
      headers = $responseHeaders
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

function Register-User {
  param(
    [string]$BaseUrl,
    [string]$Label,
    [string]$Suffix
  )

  $response = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
    username = "${Label}_$Suffix"
    email    = "${Label}_$Suffix@test.com"
    password = "P@ssw0rd!123456"
  }
  $json = if ($response.content) { $response.content | ConvertFrom-Json } else { $null }

  return [pscustomobject]@{
    response    = $response
    id          = if ($null -ne $json) { [string]$json.id } else { "" }
    accessToken = if ($null -ne $json) { [string]$json.accessToken } else { "" }
  }
}

function New-AuthHeaders {
  param(
    [string]$AccessToken,
    [string]$RequestId = ""
  )

  $headers = @{}
  if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
    $headers["Authorization"] = "Bearer $AccessToken"
  }
  if (-not [string]::IsNullOrWhiteSpace($RequestId)) {
    $headers["X-Request-Id"] = $RequestId
  }
  return $headers
}

function Assert-ApiError {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [pscustomobject]$Response,
    [int]$ExpectedStatus,
    [string]$ExpectedCode,
    [string]$ExpectedRequestId
  )

  $json = if ($Response.content) { $Response.content | ConvertFrom-Json } else { $null }
  $code = if ($null -ne $json) { [string](Get-ObjectPropertyValue -Object $json -Name "code") } else { "" }
  $requestId = if ($null -ne $json) { [string](Get-ObjectPropertyValue -Object $json -Name "requestId") } else { "" }
  $headerRequestIdRaw = if ($Response.headers.ContainsKey("X-Request-Id")) { [string]$Response.headers["X-Request-Id"] } else { "" }
  $headerRequestIds = @()
  if (-not [string]::IsNullOrWhiteSpace($headerRequestIdRaw)) {
    $headerRequestIds = @(
      $headerRequestIdRaw.Split(",") |
      ForEach-Object { $_.Trim() } |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      Select-Object -Unique
    )
  }
  $headerRequestIdMatched = $headerRequestIds.Count -gt 0 -and ($headerRequestIds -contains $ExpectedRequestId)

  $passed = (
    $Response.status -eq $ExpectedStatus -and
    $code -eq $ExpectedCode -and
    $requestId -eq $ExpectedRequestId -and
    $headerRequestIdMatched
  )

  $detail = "status=$($Response.status); code=$code; requestId=$requestId; headerRequestId=$($headerRequestIds -join ';')"
  Assert-Condition -Results $Results -Name $Name -Condition $passed -Detail $detail
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$owner = Register-User -BaseUrl $BaseUrl -Label "err_owner" -Suffix $suffix
Assert-Condition -Results $results -Name "Register Owner" -Condition ($owner.response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($owner.id) -and -not [string]::IsNullOrWhiteSpace($owner.accessToken)) -Detail "status=$($owner.response.status)"

$follower = Register-User -BaseUrl $BaseUrl -Label "err_follower" -Suffix $suffix
Assert-Condition -Results $results -Name "Register Follower" -Condition ($follower.response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($follower.id) -and -not [string]::IsNullOrWhiteSpace($follower.accessToken)) -Detail "status=$($follower.response.status)"

$publicPortfolio = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Body @{
  name       = "Error Contract Public $suffix"
  ownerId    = $owner.id
  visibility = "PUBLIC"
}
$publicPortfolioJson = if ($publicPortfolio.content) { $publicPortfolio.content | ConvertFrom-Json } else { $null }
$publicPortfolioId = if ($null -ne $publicPortfolioJson) { [string]$publicPortfolioJson.id } else { "" }
Assert-Condition -Results $results -Name "Create Public Portfolio" -Condition ($publicPortfolio.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($publicPortfolioId)) -Detail "status=$($publicPortfolio.status)"

$portfolioInvalidVisibilityRequestId = "error-contract-portfolio-$suffix"
$portfolioInvalidVisibility = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers @{
  "X-Request-Id" = $portfolioInvalidVisibilityRequestId
} -Body @{
  name       = "Bad Visibility $suffix"
  ownerId    = $owner.id
  visibility = "SECRET"
}
Assert-ApiError -Results $results -Name "Portfolio Invalid Visibility Contract" -Response $portfolioInvalidVisibility -ExpectedStatus 400 -ExpectedCode "invalid_visibility" -ExpectedRequestId $portfolioInvalidVisibilityRequestId

$tradeInvalidPortfolioRequestId = "error-contract-trade-$suffix"
$tradeInvalidPortfolio = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/trade/buy" -Headers @{
  "X-Request-Id" = $tradeInvalidPortfolioRequestId
} -Body @{
  portfolioId = "not-a-uuid"
  symbol      = "BTCUSDT"
  quantity    = 0.1
  leverage    = 10
  side        = "LONG"
}
Assert-ApiError -Results $results -Name "Trade Invalid Portfolio Contract" -Response $tradeInvalidPortfolio -ExpectedStatus 400 -ExpectedCode "portfolio_id_invalid" -ExpectedRequestId $tradeInvalidPortfolioRequestId

$tournamentInvalidLimitRequestId = "error-contract-tournament-$suffix"
$tournamentInvalidLimit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/tournaments/$([guid]::NewGuid())/trades?limit=0" -Headers @{
  "X-Request-Id" = $tournamentInvalidLimitRequestId
}
Assert-ApiError -Results $results -Name "Tournament Invalid Limit Contract" -Response $tournamentInvalidLimit -ExpectedStatus 400 -ExpectedCode "invalid_tournament_trades_limit" -ExpectedRequestId $tournamentInvalidLimitRequestId

$watchlistInvalidSizeRequestId = "error-contract-watchlist-$suffix"
$watchlistInvalidSize = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/watchlists?page=0&size=0" -Headers (New-AuthHeaders -AccessToken $owner.accessToken -RequestId $watchlistInvalidSizeRequestId)
Assert-ApiError -Results $results -Name "Watchlist Invalid Size Contract" -Response $watchlistInvalidSize -ExpectedStatus 400 -ExpectedCode "invalid_watchlist_size" -ExpectedRequestId $watchlistInvalidSizeRequestId

$followSelfRequestId = "error-contract-follow-self-$suffix"
$followSelf = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$($owner.id)/follow" -Headers (New-AuthHeaders -AccessToken $owner.accessToken -RequestId $followSelfRequestId)
Assert-ApiError -Results $results -Name "Follow Self Contract" -Response $followSelf -ExpectedStatus 400 -ExpectedCode "cannot_follow_self" -ExpectedRequestId $followSelfRequestId

$initialFollow = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$($owner.id)/follow" -Headers (New-AuthHeaders -AccessToken $follower.accessToken)
Assert-Condition -Results $results -Name "Initial Follow Seed" -Condition ($initialFollow.status -eq 200) -Detail "status=$($initialFollow.status)"

$followDuplicateRequestId = "error-contract-follow-dup-$suffix"
$followDuplicate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$($owner.id)/follow" -Headers (New-AuthHeaders -AccessToken $follower.accessToken -RequestId $followDuplicateRequestId)
Assert-ApiError -Results $results -Name "Follow Duplicate Contract" -Response $followDuplicate -ExpectedStatus 409 -ExpectedCode "already_following" -ExpectedRequestId $followDuplicateRequestId

$leaveWithoutParticipationRequestId = "error-contract-leave-$suffix"
$leaveWithoutParticipation = Invoke-Request -Method "DELETE" -Url "$BaseUrl/api/v1/portfolios/$publicPortfolioId/leave" -Headers (New-AuthHeaders -AccessToken $follower.accessToken -RequestId $leaveWithoutParticipationRequestId)
Assert-ApiError -Results $results -Name "Leave Without Participation Contract" -Response $leaveWithoutParticipation -ExpectedStatus 404 -ExpectedCode "portfolio_participation_not_found" -ExpectedRequestId $leaveWithoutParticipationRequestId

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
$reportPath = Join-Path $ReportsDir "error-contract-staging-checklist-$timestamp.md"

$lines = @()
$lines += "# Error Contract Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$(if ($allPassed) { 'PASSED' } else { 'FAILED' })**"
$lines += ""
$lines += "Coverage:"
$lines += "- portfolio invalid visibility contract"
$lines += "- trade invalid portfolio id contract"
$lines += "- tournament invalid limit contract"
$lines += "- watchlist invalid page/size contract"
$lines += "- follow self and duplicate follow contracts"
$lines += "- portfolio participation leave-without-edge contract"
$lines += "- `X-Request-Id` echo + JSON `requestId` continuity on each error path"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Error contract staging checklist report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
