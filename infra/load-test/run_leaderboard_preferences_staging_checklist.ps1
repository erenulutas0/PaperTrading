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

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
  username = "leaderboard_pref_$suffix"
  email    = "leaderboard_pref_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
$accessToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
Assert-Condition -Results $results -Name "Register User" -Condition ($register.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken)) -Detail "status=$($register.status)"

$authHeaders = @{
  "Authorization" = "Bearer $accessToken"
}

$initialPreferences = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers $authHeaders
$initialJson = if ($initialPreferences.content) { $initialPreferences.content | ConvertFrom-Json } else { $null }
$initialLeaderboard = Get-ObjectPropertyValue -Object $initialJson -Name "leaderboard"
$initialDashboard = Get-ObjectPropertyValue -Object $initialLeaderboard -Name "dashboard"
$initialPublic = Get-ObjectPropertyValue -Object $initialLeaderboard -Name "publicPage"
Assert-Condition -Results $results -Name "Default Leaderboard Preferences" -Condition (
  $initialPreferences.status -eq 200 -and
  [string](Get-ObjectPropertyValue -Object $initialDashboard -Name "period") -eq "1D" -and
  [string](Get-ObjectPropertyValue -Object $initialDashboard -Name "sortBy") -eq "RETURN_PERCENTAGE" -and
  [string](Get-ObjectPropertyValue -Object $initialDashboard -Name "direction") -eq "DESC" -and
  [string](Get-ObjectPropertyValue -Object $initialPublic -Name "sortBy") -eq "RETURN_PERCENTAGE" -and
  [string](Get-ObjectPropertyValue -Object $initialPublic -Name "direction") -eq "DESC"
) -Detail "status=$($initialPreferences.status)"

$dashboardUpdate = Invoke-Request -Method "PUT" -Url "$BaseUrl/api/v1/users/me/preferences/leaderboard" -Headers $authHeaders -Body @{
  dashboard = @{
    period    = "1M"
    sortBy    = "PROFIT_LOSS"
    direction = "ASC"
  }
}
$dashboardUpdateJson = if ($dashboardUpdate.content) { $dashboardUpdate.content | ConvertFrom-Json } else { $null }
$dashboardLeaderboard = Get-ObjectPropertyValue -Object $dashboardUpdateJson -Name "leaderboard"
$dashboardPrefs = Get-ObjectPropertyValue -Object $dashboardLeaderboard -Name "dashboard"
$publicPrefsAfterDashboard = Get-ObjectPropertyValue -Object $dashboardLeaderboard -Name "publicPage"
Assert-Condition -Results $results -Name "Dashboard Preference Update" -Condition (
  $dashboardUpdate.status -eq 200 -and
  [string](Get-ObjectPropertyValue -Object $dashboardPrefs -Name "period") -eq "1M" -and
  [string](Get-ObjectPropertyValue -Object $dashboardPrefs -Name "sortBy") -eq "PROFIT_LOSS" -and
  [string](Get-ObjectPropertyValue -Object $dashboardPrefs -Name "direction") -eq "ASC" -and
  [string](Get-ObjectPropertyValue -Object $publicPrefsAfterDashboard -Name "sortBy") -eq "RETURN_PERCENTAGE" -and
  [string](Get-ObjectPropertyValue -Object $publicPrefsAfterDashboard -Name "direction") -eq "DESC"
) -Detail "status=$($dashboardUpdate.status)"

$publicUpdate = Invoke-Request -Method "PUT" -Url "$BaseUrl/api/v1/users/me/preferences/leaderboard" -Headers $authHeaders -Body @{
  publicPage = @{
    sortBy    = "TRUST_SCORE"
    direction = "ASC"
  }
}
$publicUpdateJson = if ($publicUpdate.content) { $publicUpdate.content | ConvertFrom-Json } else { $null }
$publicUpdateLeaderboard = Get-ObjectPropertyValue -Object $publicUpdateJson -Name "leaderboard"
$dashboardAfterPublic = Get-ObjectPropertyValue -Object $publicUpdateLeaderboard -Name "dashboard"
$publicAfterPublic = Get-ObjectPropertyValue -Object $publicUpdateLeaderboard -Name "publicPage"
Assert-Condition -Results $results -Name "Public Preference Partial Update" -Condition (
  $publicUpdate.status -eq 200 -and
  [string](Get-ObjectPropertyValue -Object $dashboardAfterPublic -Name "period") -eq "1M" -and
  [string](Get-ObjectPropertyValue -Object $dashboardAfterPublic -Name "sortBy") -eq "PROFIT_LOSS" -and
  [string](Get-ObjectPropertyValue -Object $dashboardAfterPublic -Name "direction") -eq "ASC" -and
  [string](Get-ObjectPropertyValue -Object $publicAfterPublic -Name "sortBy") -eq "TRUST_SCORE" -and
  [string](Get-ObjectPropertyValue -Object $publicAfterPublic -Name "direction") -eq "ASC"
) -Detail "status=$($publicUpdate.status)"

$reloadedPreferences = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers $authHeaders
$reloadedJson = if ($reloadedPreferences.content) { $reloadedPreferences.content | ConvertFrom-Json } else { $null }
$reloadedLeaderboard = Get-ObjectPropertyValue -Object $reloadedJson -Name "leaderboard"
$reloadedDashboard = Get-ObjectPropertyValue -Object $reloadedLeaderboard -Name "dashboard"
$reloadedPublic = Get-ObjectPropertyValue -Object $reloadedLeaderboard -Name "publicPage"
Assert-Condition -Results $results -Name "Reloaded Preference Continuity" -Condition (
  $reloadedPreferences.status -eq 200 -and
  [string](Get-ObjectPropertyValue -Object $reloadedDashboard -Name "period") -eq "1M" -and
  [string](Get-ObjectPropertyValue -Object $reloadedDashboard -Name "sortBy") -eq "PROFIT_LOSS" -and
  [string](Get-ObjectPropertyValue -Object $reloadedDashboard -Name "direction") -eq "ASC" -and
  [string](Get-ObjectPropertyValue -Object $reloadedPublic -Name "sortBy") -eq "TRUST_SCORE" -and
  [string](Get-ObjectPropertyValue -Object $reloadedPublic -Name "direction") -eq "ASC"
) -Detail "status=$($reloadedPreferences.status)"

$portfolioBoard = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/leaderboards?period=1M&sortBy=PROFIT_LOSS&direction=ASC&page=0&size=5"
Assert-Condition -Results $results -Name "Portfolio Leaderboard Query" -Condition ($portfolioBoard.status -eq 200) -Detail "status=$($portfolioBoard.status)"

$accountBoard = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/leaderboards/accounts?period=1M&sortBy=TRUST_SCORE&direction=ASC&page=0&size=5"
Assert-Condition -Results $results -Name "Account Leaderboard Query" -Condition ($accountBoard.status -eq 200) -Detail "status=$($accountBoard.status)"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
$reportPath = Join-Path $ReportsDir "leaderboard-preferences-staging-checklist-$timestamp.md"

$lines = @()
$lines += "# Leaderboard Preferences Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$(if ($allPassed) { 'PASSED' } else { 'FAILED' })**"
$lines += ""
$lines += "Coverage:"
$lines += "- default leaderboard preference payload"
$lines += "- dashboard preference persistence (`period`, `sortBy`, `direction`)"
$lines += "- public leaderboard preference partial update persistence"
$lines += "- reloaded preference continuity"
$lines += "- leaderboard endpoints remain compatible with the persisted lens values"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Leaderboard preferences staging checklist report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
