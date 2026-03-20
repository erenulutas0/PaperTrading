param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [int]$TimeoutSec = 15,
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

function Assert-AuditSnapshot {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [object]$Response,
    [string]$ExpectedAction,
    [string]$ExpectedPath,
    [string]$ExpectedRequestId
  )

  $json = if ($Response.content) { $Response.content | ConvertFrom-Json } else { $null }
  $entries = @(Get-ObjectPropertyValue -Object $json -Name "entries")
  $countValue = Get-ObjectPropertyValue -Object $json -Name "count"
  $count = if ($null -ne $countValue) { [int]$countValue } else { -1 }
  $matchedEntry = $entries | Where-Object {
    [string](Get-ObjectPropertyValue -Object $_ -Name "actionType") -eq $ExpectedAction -and
    [string](Get-ObjectPropertyValue -Object $_ -Name "requestPath") -eq $ExpectedPath -and
    [string](Get-ObjectPropertyValue -Object $_ -Name "requestId") -eq $ExpectedRequestId
  } | Select-Object -First 1

  Assert-Condition -Results $Results -Name $Name -Condition ($Response.status -eq 200 -and $count -ge 1 -and $null -ne $matchedEntry) -Detail "status=$($Response.status), count=$count, action=$ExpectedAction, path=$ExpectedPath"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$ownerRegister = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
  username = "audit_owner_$suffix"
  email    = "audit_owner_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$ownerJson = if ($ownerRegister.content) { $ownerRegister.content | ConvertFrom-Json } else { $null }
$ownerId = if ($null -ne $ownerJson) { [string]$ownerJson.id } else { "" }
Assert-Condition -Results $results -Name "Register Owner" -Condition ($ownerRegister.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($ownerId)) -Detail "status=$($ownerRegister.status)"

$actorRegister = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
  username = "audit_actor_$suffix"
  email    = "audit_actor_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$actorJson = if ($actorRegister.content) { $actorRegister.content | ConvertFrom-Json } else { $null }
$actorToken = if ($null -ne $actorJson) { [string]$actorJson.accessToken } else { "" }
Assert-Condition -Results $results -Name "Register Actor" -Condition ($actorRegister.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($actorToken)) -Detail "status=$($actorRegister.status)"

$portfolioRequestId = "audit-portfolio-$suffix"
$createPortfolio = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers @{
  "X-Request-Id" = $portfolioRequestId
} -Body @{
  name       = "Audit Smoke $suffix"
  ownerId    = $ownerId
  visibility = "PUBLIC"
}
$portfolioJson = if ($createPortfolio.content) { $createPortfolio.content | ConvertFrom-Json } else { $null }
$portfolioId = if ($null -ne $portfolioJson) { [string]$portfolioJson.id } else { "" }
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($createPortfolio.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "status=$($createPortfolio.status)"

$tradeRequestId = "audit-trade-$suffix"
$trade = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/trade/buy" -Headers @{
  "X-Request-Id" = $tradeRequestId
} -Body @{
  portfolioId = $portfolioId
  symbol      = "BTCUSDT"
  quantity    = 0.01
  leverage    = 2
  side        = "LONG"
}
Assert-Condition -Results $results -Name "Trade Buy" -Condition ($trade.status -eq 200) -Detail "status=$($trade.status)"

$followRequestId = "audit-follow-$suffix"
$follow = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$ownerId/follow" -Headers @{
  "Authorization" = "Bearer $actorToken"
  "X-Request-Id"  = $followRequestId
}
Assert-Condition -Results $results -Name "Follow User" -Condition ($follow.status -eq 200) -Detail "status=$($follow.status)"

$commentRequestId = "audit-comment-$suffix"
$comment = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/interactions/$portfolioId/comments" -Headers @{
  "Authorization" = "Bearer $actorToken"
  "X-Request-Id"  = $commentRequestId
} -Body @{
  targetType = "PORTFOLIO"
  content    = "Audit smoke comment"
}
Assert-Condition -Results $results -Name "Comment Portfolio" -Condition ($comment.status -eq 200) -Detail "status=$($comment.status)"

$analysisCreateRequestId = "audit-analysis-create-$suffix"
$analysisCreate = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/analysis-posts" -Headers @{
  "Authorization" = "Bearer $actorToken"
  "X-Request-Id"  = $analysisCreateRequestId
} -Body @{
  title            = "Audit thesis $suffix"
  content          = "Audit smoke analysis body"
  instrumentSymbol = "BTCUSDT"
  direction        = "BULLISH"
}
$analysisCreateJson = if ($analysisCreate.content) { $analysisCreate.content | ConvertFrom-Json } else { $null }
$analysisPostIdValue = Get-ObjectPropertyValue -Object $analysisCreateJson -Name "id"
$analysisPostId = if ($null -ne $analysisPostIdValue) { [string]$analysisPostIdValue } else { "" }
Assert-Condition -Results $results -Name "Create Analysis Post" -Condition ($analysisCreate.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($analysisPostId)) -Detail "status=$($analysisCreate.status), body=$($analysisCreate.content)"

$analysisDeleteRequestId = "audit-analysis-delete-$suffix"
$analysisDelete = Invoke-Request -Method "DELETE" -Url "$BaseUrl/api/v1/analysis-posts/$analysisPostId" -Headers @{
  "Authorization" = "Bearer $actorToken"
  "X-Request-Id"  = $analysisDeleteRequestId
}
Assert-Condition -Results $results -Name "Delete Analysis Post" -Condition ($analysisDelete.status -eq 200) -Detail "status=$($analysisDelete.status)"

$portfolioAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($portfolioRequestId))"
Assert-AuditSnapshot -Results $results -Name "Portfolio Audit Capture" -Response $portfolioAudit -ExpectedAction "PORTFOLIO_CREATED" -ExpectedPath "/api/v1/portfolios" -ExpectedRequestId $portfolioRequestId

$tradeAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($tradeRequestId))"
Assert-AuditSnapshot -Results $results -Name "Trade Audit Capture" -Response $tradeAudit -ExpectedAction "TRADE_BUY_EXECUTED" -ExpectedPath "/api/v1/trade/buy" -ExpectedRequestId $tradeRequestId

$followAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($followRequestId))"
Assert-AuditSnapshot -Results $results -Name "Follow Audit Capture" -Response $followAudit -ExpectedAction "USER_FOLLOWED" -ExpectedPath "/api/v1/users/$ownerId/follow" -ExpectedRequestId $followRequestId

$commentAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($commentRequestId))"
Assert-AuditSnapshot -Results $results -Name "Comment Audit Capture" -Response $commentAudit -ExpectedAction "INTERACTION_COMMENTED" -ExpectedPath "/api/v1/interactions/$portfolioId/comments" -ExpectedRequestId $commentRequestId

$analysisCreateAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($analysisCreateRequestId))"
Assert-AuditSnapshot -Results $results -Name "Analysis Create Audit Capture" -Response $analysisCreateAudit -ExpectedAction "ANALYSIS_POST_CREATED" -ExpectedPath "/api/v1/analysis-posts" -ExpectedRequestId $analysisCreateRequestId

$analysisDeleteAudit = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($analysisDeleteRequestId))"
Assert-AuditSnapshot -Results $results -Name "Analysis Delete Audit Capture" -Response $analysisDeleteAudit -ExpectedAction "ANALYSIS_POST_DELETED" -ExpectedPath "/api/v1/analysis-posts/$analysisPostId" -ExpectedRequestId $analysisDeleteRequestId

$auditActuator = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/auditlog?requestId=$([System.Uri]::EscapeDataString($analysisDeleteRequestId))"
$auditActuatorJson = if ($auditActuator.content) { $auditActuator.content | ConvertFrom-Json } else { $null }
$auditActuatorCountValue = Get-ObjectPropertyValue -Object $auditActuatorJson -Name "count"
$auditActuatorCount = if ($null -ne $auditActuatorCountValue) { [int]$auditActuatorCountValue } else { -1 }
Assert-Condition -Results $results -Name "Audit Actuator Filtered Snapshot" -Condition ($auditActuator.status -eq 200 -and $auditActuatorCount -ge 1) -Detail "status=$($auditActuator.status), count=$auditActuatorCount"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "audit-write-capture-smoke-$timestamp.md"

$lines = @()
$lines += "# Audit Write Capture Smoke"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' })"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Audit write capture smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
