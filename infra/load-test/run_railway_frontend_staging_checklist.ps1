param(
  [string]$FrontendBaseUrl,
  [string]$BackendBaseUrl,
  [string]$ReportsDir = "infra/load-test/reports",
  [string]$RelayBrokerRestartCommand = "",
  [switch]$SkipRelay,
  [switch]$SkipOriginChecklist,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($FrontendBaseUrl)) {
  throw "FrontendBaseUrl is required."
}
if ([string]::IsNullOrWhiteSpace($BackendBaseUrl) -and -not $SkipOriginChecklist) {
  throw "BackendBaseUrl is required unless -SkipOriginChecklist is supplied."
}

$frontendRoot = $FrontendBaseUrl.TrimEnd("/")
$backendRoot = $BackendBaseUrl.TrimEnd("/")
$frontendOrigin = ([uri]$frontendRoot).GetLeftPart([System.UriPartial]::Authority)

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )

  try {
    $requestParams = @{
      Uri         = $Url
      Method      = $Method
      UseBasicParsing = $true
      TimeoutSec  = 20
      Headers     = @{}
    }
    foreach ($key in $Headers.Keys) {
      $requestParams.Headers[$key] = [string]$Headers[$key]
    }
    if ($null -ne $Body) {
      $requestParams.ContentType = "application/json"
      $requestParams.Body = ($Body | ConvertTo-Json -Depth 12)
    }
    $response = Invoke-WebRequest @requestParams
    $statusCode = [int]$response.StatusCode
    [pscustomobject]@{
      status  = $statusCode
      content = [string]$response.Content
      ok      = ($statusCode -ge 200 -and $statusCode -lt 300)
      error   = ""
    }
  } catch {
    $exceptionResponse = $null
    if ($_.Exception -and $_.Exception.PSObject.Properties["Response"]) {
      $exceptionResponse = $_.Exception.Response
    }
    if ($null -ne $exceptionResponse) {
      $response = $exceptionResponse
      $bodyText = ""
      try {
        $stream = $response.GetResponseStream()
        if ($null -ne $stream) {
          $reader = New-Object System.IO.StreamReader($stream)
          $bodyText = $reader.ReadToEnd()
        }
      } catch {
      }
      return [pscustomobject]@{
        status  = [int]$response.StatusCode
        content = $bodyText
        ok      = $false
        error   = $_.Exception.Message
      }
    }
    [pscustomobject]@{
      status  = 0
      content = ""
      ok      = $false
      error   = $_.Exception.Message
    }
  }
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
  $patternFlags = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline
  $match = [regex]::Match($content, "^\s*-\s+Status:\s+\*\*(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\*\*\s*$", $patternFlags)
  if ($match.Success -and $match.Groups.Count -ge 2) {
    return $match.Groups[1].Value.Trim()
  }

  $lineMatch = [regex]::Match($content, "^\s*-\s+Status:\s+(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\b", $patternFlags)
  if ($lineMatch.Success -and $lineMatch.Groups.Count -ge 2) {
    return $lineMatch.Groups[1].Value.Trim()
  }

  return ""
}

function Invoke-ScriptStep {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [hashtable]$Parameters,
    [string]$ReportPattern
  )

  $before = Get-LatestReport -Directory $ReportsDir -Pattern $ReportPattern
  try {
    & $ScriptPath @Parameters | Out-Null

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

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "railway-frontend-staging-checklist-$timestamp.md"
$results = New-Object 'System.Collections.Generic.List[object]'

$loginPage = Invoke-Request -Method "GET" -Url "$frontendRoot/auth/login"
$loginPageOkay = $loginPage.status -eq 200 -and $loginPage.content -match "<!DOCTYPE html|<html"
$results.Add((New-StepResult -Name "Frontend Login Page" -Status $(if ($loginPageOkay) { "PASSED" } else { "FAILED" }) -Detail "status=$($loginPage.status), error=$($loginPage.error)")) | Out-Null

$leaderboardProxy = Invoke-Request -Method "GET" -Url "$frontendRoot/api/v1/leaderboards?period=ALL&sortBy=PROFIT_LOSS&direction=DESC&page=0&size=5"
$leaderboardJson = if ($leaderboardProxy.content) { try { $leaderboardProxy.content | ConvertFrom-Json } catch { $null } } else { $null }
$leaderboardContent = if ($null -ne $leaderboardJson) { @($leaderboardJson.content) } else { @() }
$leaderboardProxyOkay = $leaderboardProxy.status -eq 200 -and $null -ne $leaderboardJson -and $leaderboardJson.PSObject.Properties.Name -contains "content"
$results.Add((New-StepResult -Name "Frontend Leaderboard Proxy" -Status $(if ($leaderboardProxyOkay) { "PASSED" } else { "FAILED" }) -Detail "status=$($leaderboardProxy.status), contentCount=$(@($leaderboardContent).Count), error=$($leaderboardProxy.error)")) | Out-Null

$email = "railway_frontend_$suffix@test.com"
$password = "P@ssw0rd!123456"
$registerProxy = Invoke-Request -Method "POST" -Url "$frontendRoot/api/v1/auth/register" -Body @{
  username = "railway_frontend_$suffix"
  email    = $email
  password = $password
}
$registerJson = if ($registerProxy.content) { try { $registerProxy.content | ConvertFrom-Json } catch { $null } } else { $null }
$registerToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
$registerOkay = $registerProxy.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($registerToken)
$results.Add((New-StepResult -Name "Frontend Register Proxy" -Status $(if ($registerOkay) { "PASSED" } else { "FAILED" }) -Detail "status=$($registerProxy.status), error=$($registerProxy.error)")) | Out-Null

$loginProxy = Invoke-Request -Method "POST" -Url "$frontendRoot/api/v1/auth/login" -Body @{
  email    = $email
  password = $password
}
$loginJson = if ($loginProxy.content) { try { $loginProxy.content | ConvertFrom-Json } catch { $null } } else { $null }
$loginToken = if ($null -ne $loginJson) { [string]$loginJson.accessToken } else { "" }
$loginOkay = $loginProxy.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($loginToken)
$results.Add((New-StepResult -Name "Frontend Login Proxy" -Status $(if ($loginOkay) { "PASSED" } else { "FAILED" }) -Detail "status=$($loginProxy.status), error=$($loginProxy.error)")) | Out-Null

$protectedRead = Invoke-Request -Method "GET" -Url "$frontendRoot/api/v1/notifications/unread-count" -Headers @{
  "Authorization" = "Bearer $loginToken"
}
$protectedJson = if ($protectedRead.content) { try { $protectedRead.content | ConvertFrom-Json } catch { $null } } else { $null }
$protectedCount = if ($null -ne $protectedJson -and $protectedJson.PSObject.Properties.Name -contains "count") { [int]$protectedJson.count } else { -1 }
$protectedOkay = $protectedRead.status -eq 200 -and $protectedCount -ge 0
$results.Add((New-StepResult -Name "Frontend Protected Read Proxy" -Status $(if ($protectedOkay) { "PASSED" } else { "FAILED" }) -Detail "status=$($protectedRead.status), count=$protectedCount, error=$($protectedRead.error)")) | Out-Null

if (-not $SkipOriginChecklist) {
  $originParams = @{
    BaseUrl        = $backendRoot
    FrontendOrigin = $frontendOrigin
    NoFail         = $true
  }
  if ($SkipRelay) {
    $originParams["SkipRelay"] = $true
  } elseif (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
    $originParams["RelayBrokerRestartCommand"] = $RelayBrokerRestartCommand
  }

  $results.Add((Invoke-ScriptStep `
        -Name "Backend Browser-Origin Checklist" `
        -ScriptPath (Join-Path $PSScriptRoot "run_browser_origin_staging_checklist.ps1") `
        -Parameters $originParams `
        -ReportPattern "browser-origin-staging-checklist-*.md")) | Out-Null
}

$failed = @($results | Where-Object { $_.Status -ne "PASSED" })
$overallStatus = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Railway Frontend Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Frontend Base URL: $frontendRoot"
$lines += "- Backend Base URL: $(if ([string]::IsNullOrWhiteSpace($backendRoot)) { 'n/a' } else { $backendRoot })"
$lines += "- Frontend Origin: $frontendOrigin"
$lines += "- Status: **$overallStatus**"
$lines += "- Relay restart command supplied: $(if ([string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) { 'no' } else { 'yes' })"
$lines += ""
$lines += "Coverage:"
$lines += '- frontend route availability (`/auth/login`)'
$lines += '- Next proxy reachability for `/api/v1/leaderboards`'
$lines += "- proxied auth register/login roundtrip through the frontend domain"
$lines += "- proxied protected API read through the frontend domain"
$lines += "- backend browser-origin + optional relay validation using the real frontend origin"
$lines += ""
$lines += "| Step | Status | Detail | Report |"
$lines += "|---|---|---|---|"
foreach ($result in $results) {
  $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
  $lines += "| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Railway frontend staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedSteps: $($failed.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
