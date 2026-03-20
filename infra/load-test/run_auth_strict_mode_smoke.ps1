param(
  [string]$BaseUrl = "http://localhost:18080",
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

function Get-HeaderValue {
  param(
    [object]$Headers,
    [string]$Name
  )

  if ($null -eq $Headers) {
    return ""
  }

  foreach ($key in $Headers.Keys) {
    if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
      $value = $Headers[$key]
      if ($value -is [System.Array]) {
        return [string]($value -join ",")
      }
      return [string]$value
    }
  }

  return ""
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

$userARegister = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
  username = "strict_a_$suffix"
  email    = "strict_a_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$userAJson = if ($userARegister.content) { $userARegister.content | ConvertFrom-Json } else { $null }
$userAIdValue = Get-ObjectPropertyValue -Object $userAJson -Name "id"
$userATokenValue = Get-ObjectPropertyValue -Object $userAJson -Name "accessToken"
$userAId = if ($null -ne $userAIdValue) { [string]$userAIdValue } else { "" }
$userAToken = if ($null -ne $userATokenValue) { [string]$userATokenValue } else { "" }
Assert-Condition -Results $results -Name "Register User A" -Condition ($userARegister.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($userAToken)) -Detail "status=$($userARegister.status), body=$($userARegister.content)"

$userBRegister = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
  username = "strict_b_$suffix"
  email    = "strict_b_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$userBJson = if ($userBRegister.content) { $userBRegister.content | ConvertFrom-Json } else { $null }
$userBIdValue = Get-ObjectPropertyValue -Object $userBJson -Name "id"
$userBTokenValue = Get-ObjectPropertyValue -Object $userBJson -Name "accessToken"
$userBId = if ($null -ne $userBIdValue) { [string]$userBIdValue } else { "" }
$userBToken = if ($null -ne $userBTokenValue) { [string]$userBTokenValue } else { "" }
Assert-Condition -Results $results -Name "Register User B" -Condition ($userBRegister.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($userBToken)) -Detail "status=$($userBRegister.status), body=$($userBRegister.content)"

$legacyOnlyRequestId = "strict-legacy-only-$suffix"
$legacyOnly = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers @{
  "X-Request-Id" = $legacyOnlyRequestId
  "X-User-Id"    = $userAId
}
$legacyOnlyJson = if ($legacyOnly.content) { $legacyOnly.content | ConvertFrom-Json } else { $null }
Assert-Condition -Results $results -Name "Legacy Header Rejected" -Condition (
  $legacyOnly.status -eq 401 -and
  [string](Get-ObjectPropertyValue -Object $legacyOnlyJson -Name "code") -eq "unauthorized" -and
  [string](Get-ObjectPropertyValue -Object $legacyOnlyJson -Name "message") -eq "X-User-Id header is disabled. Use Bearer token" -and
  [string](Get-ObjectPropertyValue -Object $legacyOnlyJson -Name "requestId") -eq $legacyOnlyRequestId -and
  (Get-HeaderValue -Headers $legacyOnly.headers -Name "X-Request-Id") -like "*$legacyOnlyRequestId*"
) -Detail "status=$($legacyOnly.status), body=$($legacyOnly.content)"

$bearerOnly = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers @{
  "Authorization" = "Bearer $userAToken"
}
Assert-Condition -Results $results -Name "Bearer Preferences Allowed" -Condition ($bearerOnly.status -eq 200) -Detail "status=$($bearerOnly.status)"

$matchingHeader = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers @{
  "Authorization" = "Bearer $userAToken"
  "X-User-Id"     = $userAId
}
Assert-Condition -Results $results -Name "Bearer Matching Legacy Header Allowed" -Condition ($matchingHeader.status -eq 200) -Detail "status=$($matchingHeader.status)"

$mismatchRequestId = "strict-mismatch-$suffix"
$mismatch = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/users/me/preferences" -Headers @{
  "Authorization" = "Bearer $userAToken"
  "X-User-Id"     = $userBId
  "X-Request-Id"  = $mismatchRequestId
}
$mismatchJson = if ($mismatch.content) { $mismatch.content | ConvertFrom-Json } else { $null }
Assert-Condition -Results $results -Name "Bearer Legacy Mismatch Rejected" -Condition (
  $mismatch.status -eq 401 -and
  [string](Get-ObjectPropertyValue -Object $mismatchJson -Name "code") -eq "unauthorized" -and
  [string](Get-ObjectPropertyValue -Object $mismatchJson -Name "message") -eq "Authorization and X-User-Id mismatch" -and
  [string](Get-ObjectPropertyValue -Object $mismatchJson -Name "requestId") -eq $mismatchRequestId
) -Detail "status=$($mismatch.status), body=$($mismatch.content)"

$follow = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$userBId/follow" -Headers @{
  "Authorization" = "Bearer $userAToken"
}
Assert-Condition -Results $results -Name "Bearer Follow Allowed" -Condition ($follow.status -eq 200) -Detail "status=$($follow.status)"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "auth-strict-mode-smoke-$timestamp.md"

$lines = @()
$lines += "# Auth Strict Mode Smoke"
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

Write-Host "Auth strict-mode smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
