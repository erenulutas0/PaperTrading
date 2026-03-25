param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$TimeoutSec = 20,
  [int]$PageSize = 5,
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

function Get-ContentItems {
  param([object]$Json)

  $content = Get-ObjectPropertyValue -Object $Json -Name "content"
  if ($null -eq $content) {
    return @()
  }

  return @($content)
}

function Format-Decimal {
  param([object]$Value)

  if ($null -eq $Value) {
    return "n/a"
  }

  try {
    return ([decimal]$Value).ToString("0.####", [System.Globalization.CultureInfo]::InvariantCulture)
  } catch {
    return [string]$Value
  }
}

function Format-TopEntrySummary {
  param([object]$Entry)

  if ($null -eq $Entry) {
    return "none"
  }

  $rank = Get-ObjectPropertyValue -Object $Entry -Name "rank"
  $portfolioName = [string](Get-ObjectPropertyValue -Object $Entry -Name "portfolioName")
  $ownerName = [string](Get-ObjectPropertyValue -Object $Entry -Name "ownerName")
  $returnPercentage = Format-Decimal -Value (Get-ObjectPropertyValue -Object $Entry -Name "returnPercentage")
  $profitLoss = Format-Decimal -Value (Get-ObjectPropertyValue -Object $Entry -Name "profitLoss")
  $totalEquity = Format-Decimal -Value (Get-ObjectPropertyValue -Object $Entry -Name "totalEquity")

  return "rank=$rank portfolio=$portfolioName owner=$ownerName return=$returnPercentage profitLoss=$profitLoss equity=$totalEquity"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'
$periodRows = New-Object 'System.Collections.Generic.List[object]'

$periods = @("1D", "1W", "1M", "ALL")
$sorts = @("RETURN_PERCENTAGE", "PROFIT_LOSS")

foreach ($period in $periods) {
  foreach ($sort in $sorts) {
    $url = "$BaseUrl/api/v1/leaderboards?period=$period&sortBy=$sort&direction=DESC&page=0&size=$PageSize"
    $response = Invoke-Request -Method "GET" -Url $url
    $json = if ($response.content) { $response.content | ConvertFrom-Json } else { $null }
    $contentProp = if ($null -ne $json) { $json.PSObject.Properties["content"] } else { $null }
    $entries = Get-ContentItems -Json $json
    $page = Get-ObjectPropertyValue -Object $json -Name "page"
    $totalElements = Get-ObjectPropertyValue -Object $page -Name "totalElements"
    $topEntry = if ($entries.Count -gt 0) { $entries[0] } else { $null }
    $topSummary = Format-TopEntrySummary -Entry $topEntry

    Assert-Condition `
      -Results $results `
      -Name "Portfolio Leaderboard $period $sort" `
      -Condition ($response.status -eq 200 -and $null -ne $contentProp) `
      -Detail "status=$($response.status); count=$($entries.Count); totalElements=$totalElements"

    $periodRows.Add([pscustomobject]@{
        Period        = $period
        SortBy        = $sort
        Status        = $response.status
        EntryCount    = $entries.Count
        TotalElements = $totalElements
        TopEntry      = $topSummary
      }) | Out-Null
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$allChecksPassed = $failedResults.Count -eq 0
$nonEmptyRows = @($periodRows | Where-Object { $_.EntryCount -gt 0 })

$overallStatus = if (-not $allChecksPassed) {
  "FAILED"
} elseif ($nonEmptyRows.Count -gt 0) {
  "PASSED"
} else {
  "CONDITIONAL_READY"
}

New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
$reportPath = Join-Path $ReportsDir "leaderboard-period-validation-staging-checklist-$timestamp.md"

$lines = @()
$lines += "# Leaderboard Period Validation Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Page Size: $PageSize"
$lines += ""
$lines += "Coverage:"
$lines += '- portfolio leaderboard queries for `1D`, `1W`, `1M`, `ALL`'
$lines += '- both `RETURN_PERCENTAGE` and `PROFIT_LOSS` ranking lenses'
$lines += "- captured top-row samples for product review of period semantics"
$lines += "- intended to replace ad hoc manual endpoint probing with a stable attachable report"
$lines += ""
$lines += "Product review prompts:"
$lines += "- confirm the ranking semantics reflect portfolio-level ROI/return rather than position-level ROE"
$lines += "- compare whether top rows differ across periods in ways the product expects"
$lines += "- attach this report to the remaining staging/product decision item when run against live data"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}
$lines += ""
$lines += "| Period | Sort | Status | Entry Count | Total Elements | Top Entry |"
$lines += "|---|---|---|---:|---:|---|"
foreach ($row in $periodRows) {
  $lines += "| $($row.Period) | $($row.SortBy) | $($row.Status) | $($row.EntryCount) | $($row.TotalElements) | $($row.TopEntry.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Leaderboard period validation staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count) | NonEmptySlices: $($nonEmptyRows.Count)"

if ($overallStatus -ne "PASSED" -and -not $NoFail) {
  exit 1
}
