param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [int]$TimeoutSec = 15,
  [string]$ForwardedFor = "203.0.113.42",
  [int]$CommentAttempts = 15,
  [int]$ReplyAttempts = 15,
  [int]$FollowAttempts = 24,
  [int]$RefreshAttempts = 24,
  [int]$ReadProbeCount = 5,
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

function Get-JsonValue {
  param(
    [object]$Object,
    [string]$Name
  )

  if ($null -eq $Object) {
    return $null
  }

  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property) {
    return $null
  }

  return $property.Value
}

function New-Headers {
  param(
    [string]$AccessToken = ""
  )

  $headers = @{
    "X-Forwarded-For" = $ForwardedFor
  }

  if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
    $headers["Authorization"] = "Bearer $AccessToken"
  }

  return $headers
}

function Register-TestUser {
  param(
    [string]$Label,
    [string]$Suffix
  )

  $response = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Headers (New-Headers) -Body @{
    username = "rl_${Label}_$Suffix"
    email    = "rl_${Label}_$Suffix@test.com"
    password = "P@ssw0rd!123456"
  }

  if ($response.status -ne 200) {
    throw "Register failed for ${Label}: status=$($response.status), error=$($response.error), body=$($response.content)"
  }

  $json = if ($response.content) { $response.content | ConvertFrom-Json } else { $null }
  $userId = [string](Get-JsonValue -Object $json -Name "id")
  $accessToken = [string](Get-JsonValue -Object $json -Name "accessToken")
  $refreshToken = [string](Get-JsonValue -Object $json -Name "refreshToken")

  if ([string]::IsNullOrWhiteSpace($userId) -or [string]::IsNullOrWhiteSpace($accessToken)) {
    throw "Register response missing auth fields for $Label"
  }

  return [pscustomobject]@{
    id           = $userId
    accessToken  = $accessToken
    refreshToken = $refreshToken
  }
}

function Invoke-BurstScenario {
  param(
    [string]$Name,
    [int]$Attempts,
    [scriptblock]$RequestFactory
  )

  $successCount = 0
  $rateLimitedCount = 0
  $unexpectedCount = 0
  $firstUnexpected = ""
  $firstRateLimitedAttempt = 0

  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    $response = & $RequestFactory $attempt

    if ($response.status -eq 200) {
      $successCount++
      continue
    }

    if ($response.status -eq 429) {
      $rateLimitedCount++
      if ($firstRateLimitedAttempt -eq 0) {
        $firstRateLimitedAttempt = $attempt
      }
      continue
    }

    $unexpectedCount++
    if ([string]::IsNullOrWhiteSpace($firstUnexpected)) {
      $firstUnexpected = "attempt=$attempt,status=$($response.status),error=$($response.error),body=$($response.content)"
    }
  }

  return [pscustomobject]@{
    Name                    = $Name
    Attempts                = $Attempts
    SuccessCount            = $successCount
    RateLimitedCount        = $rateLimitedCount
    UnexpectedCount         = $unexpectedCount
    FirstRateLimitedAttempt = $firstRateLimitedAttempt
    FirstUnexpected         = $firstUnexpected
  }
}

function Invoke-ReadProbe {
  param(
    [string]$Name,
    [int]$Attempts,
    [scriptblock]$RequestFactory
  )

  $successCount = 0
  $unexpectedCount = 0
  $firstUnexpected = ""

  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    $response = & $RequestFactory $attempt
    if ($response.status -eq 200) {
      $successCount++
      continue
    }

    $unexpectedCount++
    if ([string]::IsNullOrWhiteSpace($firstUnexpected)) {
      $firstUnexpected = "attempt=$attempt,status=$($response.status),error=$($response.error),body=$($response.content)"
    }
  }

  return [pscustomobject]@{
    Name            = $Name
    Attempts        = $Attempts
    SuccessCount    = $successCount
    UnexpectedCount = $unexpectedCount
    FirstUnexpected = $firstUnexpected
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = "$(Get-Date -Format 'yyyyMMddHHmmss')_$([guid]::NewGuid().ToString('N').Substring(0,8))"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$owner = Register-TestUser -Label "owner" -Suffix $suffix
$commenter = Register-TestUser -Label "commenter" -Suffix $suffix
$replier = Register-TestUser -Label "replier" -Suffix $suffix
$follower = Register-TestUser -Label "follower" -Suffix $suffix
$refreshUser = Register-TestUser -Label "refresh" -Suffix $suffix

$followTargets = New-Object 'System.Collections.Generic.List[object]'
for ($i = 1; $i -le $FollowAttempts; $i++) {
  $followTargets.Add((Register-TestUser -Label "follow_${i}" -Suffix $suffix)) | Out-Null
}

$createPortfolio = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers (New-Headers) -Body @{
  name       = "Rate Limit Smoke $suffix"
  ownerId    = $owner.id
  visibility = "PUBLIC"
}
$portfolioJson = if ($createPortfolio.content) { $createPortfolio.content | ConvertFrom-Json } else { $null }
$portfolioId = [string](Get-JsonValue -Object $portfolioJson -Name "id")
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($createPortfolio.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($portfolioId)) -Detail "status=$($createPortfolio.status)"

$seedComment = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/interactions/$portfolioId/comments" -Headers (New-Headers -AccessToken $owner.accessToken) -Body @{
  targetType = "PORTFOLIO"
  content    = "Rate-limit reply seed"
}
$seedCommentJson = if ($seedComment.content) { $seedComment.content | ConvertFrom-Json } else { $null }
$seedCommentId = [string](Get-JsonValue -Object $seedCommentJson -Name "id")
Assert-Condition -Results $results -Name "Seed Root Comment" -Condition ($seedComment.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($seedCommentId)) -Detail "status=$($seedComment.status)"

$commentBurst = Invoke-BurstScenario -Name "Comment Write Burst" -Attempts $CommentAttempts -RequestFactory {
  param($attempt)
  Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/interactions/$portfolioId/comments" -Headers (New-Headers -AccessToken $commenter.accessToken) -Body @{
    targetType = "PORTFOLIO"
    content    = "Burst comment $attempt"
  }
}

$commentReadProbe = Invoke-ReadProbe -Name "Read Probe After Comment Burst" -Attempts $ReadProbeCount -RequestFactory {
  param($attempt)
  Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/$($owner.id)/profile" -Headers (New-Headers)
}

$replyBurst = Invoke-BurstScenario -Name "Reply Write Burst" -Attempts $ReplyAttempts -RequestFactory {
  param($attempt)
  Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/interactions/$seedCommentId/comments" -Headers (New-Headers -AccessToken $replier.accessToken) -Body @{
    targetType = "COMMENT"
    content    = "Burst reply $attempt"
  }
}

$replyReadProbe = Invoke-ReadProbe -Name "Read Probe After Reply Burst" -Attempts $ReadProbeCount -RequestFactory {
  param($attempt)
  Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/interactions/$portfolioId/summary?type=PORTFOLIO" -Headers (New-Headers -AccessToken $owner.accessToken)
}

$followBurst = Invoke-BurstScenario -Name "Follow Write Burst" -Attempts $FollowAttempts -RequestFactory {
  param($attempt)
  $targetUser = $followTargets[$attempt - 1]
  Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$($targetUser.id)/follow" -Headers (New-Headers -AccessToken $follower.accessToken)
}

$followReadProbe = Invoke-ReadProbe -Name "Read Probe After Follow Burst" -Attempts $ReadProbeCount -RequestFactory {
  param($attempt)
  Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/$($owner.id)/profile" -Headers (New-Headers -AccessToken $follower.accessToken)
}

$currentRefreshToken = $refreshUser.refreshToken
$currentRefreshAccessToken = $refreshUser.accessToken
$refreshBurst = Invoke-BurstScenario -Name "Auth Refresh Burst" -Attempts $RefreshAttempts -RequestFactory {
  param($attempt)
  $response = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/refresh" -Headers (New-Headers) -Body @{
    refreshToken = $currentRefreshToken
  }

  if ($response.status -eq 200 -and $response.content) {
    $json = $response.content | ConvertFrom-Json
    $nextRefreshToken = [string](Get-JsonValue -Object $json -Name "refreshToken")
    $nextAccessToken = [string](Get-JsonValue -Object $json -Name "accessToken")
    if (-not [string]::IsNullOrWhiteSpace($nextRefreshToken)) {
      $script:currentRefreshToken = $nextRefreshToken
    }
    if (-not [string]::IsNullOrWhiteSpace($nextAccessToken)) {
      $script:currentRefreshAccessToken = $nextAccessToken
    }
  }

  return $response
}

$refreshReadProbe = Invoke-ReadProbe -Name "Read Probe After Refresh Burst" -Attempts $ReadProbeCount -RequestFactory {
  param($attempt)
  Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/unread-count" -Headers (New-Headers -AccessToken $currentRefreshAccessToken)
}

$burstScenarios = @(
  $commentBurst,
  $replyBurst,
  $followBurst,
  $refreshBurst
)

$readProbes = @(
  $commentReadProbe,
  $replyReadProbe,
  $followReadProbe,
  $refreshReadProbe
)

foreach ($scenario in $burstScenarios) {
  $scenarioPassed = $scenario.SuccessCount -ge 1 -and $scenario.RateLimitedCount -ge 1 -and $scenario.UnexpectedCount -eq 0
  Assert-Condition -Results $results -Name $scenario.Name -Condition $scenarioPassed -Detail "success=$($scenario.SuccessCount), rateLimited=$($scenario.RateLimitedCount), unexpected=$($scenario.UnexpectedCount), first429=$($scenario.FirstRateLimitedAttempt)"
}

foreach ($probe in $readProbes) {
  $probePassed = $probe.SuccessCount -eq $probe.Attempts -and $probe.UnexpectedCount -eq 0
  Assert-Condition -Results $results -Name $probe.Name -Condition $probePassed -Detail "success=$($probe.SuccessCount), attempts=$($probe.Attempts), unexpected=$($probe.UnexpectedCount)"
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "rate-limit-profile-smoke-$timestamp.md"

$lines = @()
$lines += "# Rate Limit Profile Smoke"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Synthetic client IP: $ForwardedFor"
$lines += "- Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' })"
$lines += ""
$lines += "## Burst Outcomes"
$lines += ""
$lines += "| Scenario | Attempts | 200s | 429s | Unexpected | First429Attempt | FirstUnexpected |"
$lines += "|---|---|---|---|---|---|---|"
foreach ($scenario in $burstScenarios) {
  $lines += "| $($scenario.Name) | $($scenario.Attempts) | $($scenario.SuccessCount) | $($scenario.RateLimitedCount) | $($scenario.UnexpectedCount) | $($scenario.FirstRateLimitedAttempt) | $($scenario.FirstUnexpected.Replace('|', '/')) |"
}
$lines += ""
$lines += "## Read Isolation Probes"
$lines += ""
$lines += "| Probe | Attempts | 200s | Unexpected | FirstUnexpected |"
$lines += "|---|---|---|---|---|"
foreach ($probe in $readProbes) {
  $lines += "| $($probe.Name) | $($probe.Attempts) | $($probe.SuccessCount) | $($probe.UnexpectedCount) | $($probe.FirstUnexpected.Replace('|', '/')) |"
}
$lines += ""
$lines += "## Checks"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Rate limit profile smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
