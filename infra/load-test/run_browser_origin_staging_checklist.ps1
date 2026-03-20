param(
  [string]$BaseUrl,
  [string]$FrontendOrigin,
  [string]$ReportsDir = "infra/load-test/reports",
  [string]$RelayBrokerRestartCommand = "",
  [switch]$SkipRelay,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required."
}
if ([string]::IsNullOrWhiteSpace($FrontendOrigin)) {
  throw "FrontendOrigin is required."
}

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds(20)

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
      status  = [int]$response.StatusCode
      content = $content
      headers = $headerMap
      ok      = $response.IsSuccessStatusCode
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      status  = 0
      content = ""
      headers = @{}
      ok      = $false
      error   = $_.Exception.Message
    }
  }
}

function Get-HeaderValue {
  param(
    [hashtable]$Headers,
    [string]$Name
  )

  foreach ($key in $Headers.Keys) {
    if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
      return [string]$Headers[$key]
    }
  }
  return ""
}

function New-StepResult {
  param(
    [string]$Name,
    [string]$Status,
    [string]$Detail,
    [string]$ReportPath = ""
  )

  return [pscustomobject]@{
    Name       = $Name
    Status     = $Status
    Detail     = $Detail
    ReportPath = $ReportPath
  }
}

function Get-LatestReport {
  param(
    [string]$Directory,
    [string]$Pattern
  )

  $report = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($null -eq $report) {
    return ""
  }
  return $report.FullName
}

function Parse-MarkdownStatus {
  param([string]$ReportPath)

  if (-not (Test-Path $ReportPath)) {
    return ""
  }

  $content = Get-Content -Path $ReportPath -Raw
  $match = [regex]::Match($content, "- Status:\s+\*\*(.+?)\*\*", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if ($match.Success -and $match.Groups.Count -ge 2) {
    return $match.Groups[1].Value.Trim()
  }

  $lineMatch = [regex]::Match($content, "Status:\s+([A-Z_]+)", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if ($lineMatch.Success -and $lineMatch.Groups.Count -ge 2) {
    return $lineMatch.Groups[1].Value.Trim()
  }

  return ""
}

function Invoke-ScriptStep {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [string[]]$Arguments,
    [string]$ReportPattern
  )

  $before = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
  try {
    & powershell -ExecutionPolicy Bypass -File $ScriptPath @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) {
      $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
      if ($report -eq $before) {
        $report = ""
      }
      return New-StepResult -Name $Name -Status "FAILED" -Detail "exit_code=$LASTEXITCODE" -ReportPath $report
    }

    $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
    if ([string]::IsNullOrWhiteSpace($report)) {
      return New-StepResult -Name $Name -Status "FAILED" -Detail "report_not_found" -ReportPath ""
    }

    $status = Parse-MarkdownStatus -ReportPath $report
    if ([string]::IsNullOrWhiteSpace($status)) {
      $status = "PASSED"
    }

    return New-StepResult -Name $Name -Status $status -Detail "ok" -ReportPath $report
  } catch {
    $report = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
    if ($report -eq $before) {
      $report = ""
    }
    return New-StepResult -Name $Name -Status "FAILED" -Detail $_.Exception.Message -ReportPath $report
  }
}

$null = New-Item -ItemType Directory -Path $ReportsDir -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportsDir "browser-origin-staging-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'
$suffix = Get-Date -Format "yyyyMMddHHmmss"

$preflight = Invoke-Request -Method "OPTIONS" -Url "$BaseUrl/api/v1/auth/register" -Headers @{
  "Origin" = $FrontendOrigin
  "Access-Control-Request-Method" = "POST"
  "Access-Control-Request-Headers" = "content-type,authorization"
}
$preflightOrigin = Get-HeaderValue -Headers $preflight.headers -Name "Access-Control-Allow-Origin"
$results.Add((New-StepResult -Name "CORS Preflight" -Status $(if (($preflight.status -in 200,204) -and $preflightOrigin -eq $FrontendOrigin) { "PASSED" } else { "FAILED" }) -Detail "status=$($preflight.status), allowOrigin=$preflightOrigin")) | Out-Null

$register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Headers @{
  "Origin" = $FrontendOrigin
} -Body @{
  username = "origin_$suffix"
  email    = "origin_$suffix@test.com"
  password = "P@ssw0rd!123456"
}
$registerOrigin = Get-HeaderValue -Headers $register.headers -Name "Access-Control-Allow-Origin"
$registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
$accessToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
$email = if ($null -ne $registerJson) { [string]$registerJson.email } else { "" }
$results.Add((New-StepResult -Name "Register With Origin" -Status $(if ($register.status -eq 200 -and $registerOrigin -eq $FrontendOrigin -and -not [string]::IsNullOrWhiteSpace($accessToken)) { "PASSED" } else { "FAILED" }) -Detail "status=$($register.status), allowOrigin=$registerOrigin")) | Out-Null

$login = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Headers @{
  "Origin" = $FrontendOrigin
} -Body @{
  email = $email
  password = "P@ssw0rd!123456"
}
$loginOrigin = Get-HeaderValue -Headers $login.headers -Name "Access-Control-Allow-Origin"
$loginJson = if ($login.content) { $login.content | ConvertFrom-Json } else { $null }
$loginAccessToken = if ($null -ne $loginJson) { [string]$loginJson.accessToken } else { "" }
$results.Add((New-StepResult -Name "Login With Origin" -Status $(if ($login.status -eq 200 -and $loginOrigin -eq $FrontendOrigin -and -not [string]::IsNullOrWhiteSpace($loginAccessToken)) { "PASSED" } else { "FAILED" }) -Detail "status=$($login.status), allowOrigin=$loginOrigin")) | Out-Null

$protected = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/unread-count" -Headers @{
  "Origin" = $FrontendOrigin
  "Authorization" = "Bearer $loginAccessToken"
}
$protectedOrigin = Get-HeaderValue -Headers $protected.headers -Name "Access-Control-Allow-Origin"
$results.Add((New-StepResult -Name "Protected Read With Origin" -Status $(if ($protected.status -eq 200 -and $protectedOrigin -eq $FrontendOrigin) { "PASSED" } else { "FAILED" }) -Detail "status=$($protected.status), allowOrigin=$protectedOrigin")) | Out-Null

if (-not $SkipRelay) {
  $relayArgs = @(
    "-SkipAppStart",
    "-BaseUrl", $BaseUrl,
    "-OriginHeader", $FrontendOrigin
  )
  if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $relayArgs += @("-BrokerRestartCommand", $RelayBrokerRestartCommand)
  }
  $relayArgs += "-NoFail"

  $results.Add((Invoke-ScriptStep `
        -Name "WebSocket Origin Relay" `
        -ScriptPath (Join-Path $PSScriptRoot "validate_websocket_relay_smoke.ps1") `
        -Arguments $relayArgs `
        -ReportPattern "websocket-relay-smoke-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Browser Origin Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Frontend Origin: $FrontendOrigin"
$lines += "- Status: **$overallStatus**"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Browser origin staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
