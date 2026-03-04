param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$RequestTimeoutSec = 15,
  [double]$MaxLegacyAccepted = 0,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-JsonGet {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  return Invoke-RestMethod -Method Get -Uri $Url -TimeoutSec $TimeoutSec -ErrorAction Stop
}

function Get-MetricValue {
  param(
    [string]$BaseUrl,
    [string]$MetricName,
    [hashtable]$Tags,
    [int]$TimeoutSec
  )

  $endpoint = "$BaseUrl/actuator/metrics/$MetricName"
  $queryParts = @()
  foreach ($key in $Tags.Keys) {
    $queryParts += "tag=$([uri]::EscapeDataString("${key}:$($Tags[$key])"))"
  }
  if ($queryParts.Count -gt 0) {
    $endpoint = "${endpoint}?$(($queryParts -join "&"))"
  }

  try {
    $metric = Invoke-JsonGet -Url $endpoint -TimeoutSec $TimeoutSec
    if ($null -eq $metric.measurements -or $metric.measurements.Count -eq 0) {
      return @{
        value = 0.0
        ok    = $true
        error = ""
      }
    }
    $sum = ($metric.measurements | Measure-Object -Property value -Sum).Sum
    if ($null -eq $sum) {
      return @{
        value = 0.0
        ok    = $true
        error = ""
      }
    }
    return @{
      value = [double]$sum
      ok    = $true
      error = ""
    }
  } catch {
    return @{
      value = 0.0
      ok    = $false
      error = $_.Exception.Message
    }
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Path $reportsDir -Force
$reportPath = Join-Path $reportsDir "auth-legacy-usage-$timestamp.md"

$metricName = "app.auth.requests.total"
$metricEndpoint = "$BaseUrl/actuator/metrics/$metricName"

$snapshot = $null
$availableTagsText = "- unavailable"
$notes = New-Object System.Collections.Generic.List[string]
$snapshotOk = $true

try {
  $snapshot = Invoke-JsonGet -Url $metricEndpoint -TimeoutSec $RequestTimeoutSec
  if ($null -ne $snapshot.availableTags) {
    $tagLines = @()
    foreach ($tag in $snapshot.availableTags) {
      $values = if ($null -ne $tag.values) { ($tag.values -join ", ") } else { "" }
      $tagLines += "- $($tag.tag): $values"
    }
    if ($tagLines.Count -gt 0) {
      $availableTagsText = $tagLines -join [Environment]::NewLine
    } else {
      $availableTagsText = "- none"
    }
  }
} catch {
  $snapshotOk = $false
  $notes.Add("Failed to fetch metric snapshot: $($_.Exception.Message)")
}

$legacyAcceptedResult = Get-MetricValue -BaseUrl $BaseUrl -MetricName $metricName -Tags @{
  mode   = "legacy-header"
  result = "accepted"
} -TimeoutSec $RequestTimeoutSec

$legacyRejectedDisabledResult = Get-MetricValue -BaseUrl $BaseUrl -MetricName $metricName -Tags @{
  mode   = "legacy-header"
  result = "rejected_disabled"
} -TimeoutSec $RequestTimeoutSec

$bearerAcceptedResult = Get-MetricValue -BaseUrl $BaseUrl -MetricName $metricName -Tags @{
  mode   = "bearer"
  result = "accepted"
} -TimeoutSec $RequestTimeoutSec

$anonymousAcceptedResult = Get-MetricValue -BaseUrl $BaseUrl -MetricName $metricName -Tags @{
  mode   = "anonymous"
  result = "accepted"
} -TimeoutSec $RequestTimeoutSec

$legacyAccepted = [double]$legacyAcceptedResult.value
$legacyRejectedDisabled = [double]$legacyRejectedDisabledResult.value
$bearerAccepted = [double]$bearerAcceptedResult.value
$anonymousAccepted = [double]$anonymousAcceptedResult.value

$metricAvailable = $snapshotOk -and (
  [bool]$legacyAcceptedResult.ok -or
  [bool]$legacyRejectedDisabledResult.ok -or
  [bool]$bearerAcceptedResult.ok -or
  [bool]$anonymousAcceptedResult.ok
)

if (-not $metricAvailable) {
  $status = "UNAVAILABLE"
} elseif ($legacyAccepted -le $MaxLegacyAccepted) {
  $status = "READY"
} else {
  $status = "NOT_READY"
}

$thresholdBreached = $metricAvailable -and ($legacyAccepted -gt $MaxLegacyAccepted)

if (-not $legacyAcceptedResult.ok) {
  $notes.Add("Failed to fetch legacy accepted slice: $($legacyAcceptedResult.error)")
}
if (-not $legacyRejectedDisabledResult.ok) {
  $notes.Add("Failed to fetch legacy rejected slice: $($legacyRejectedDisabledResult.error)")
}
if (-not $bearerAcceptedResult.ok) {
  $notes.Add("Failed to fetch bearer accepted slice: $($bearerAcceptedResult.error)")
}
if (-not $anonymousAcceptedResult.ok) {
  $notes.Add("Failed to fetch anonymous accepted slice: $($anonymousAcceptedResult.error)")
}

$notesBlock = if ($notes.Count -eq 0) {
  "- none"
} else {
  ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine
}

$reportLines = @(
  "# Auth Legacy Usage Check",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- Metric endpoint: $metricEndpoint",
  "- Max legacy accepted threshold: $MaxLegacyAccepted",
  "- Request timeout seconds: $RequestTimeoutSec",
  "",
  "## Result",
  "- Status: **$status**",
  "- Legacy accepted (`mode=legacy-header,result=accepted`): $([Math]::Round($legacyAccepted, 2))",
  "- Legacy rejected disabled (`mode=legacy-header,result=rejected_disabled`): $([Math]::Round($legacyRejectedDisabled, 2))",
  "- Bearer accepted (`mode=bearer,result=accepted`): $([Math]::Round($bearerAccepted, 2))",
  "- Anonymous accepted (`mode=anonymous,result=accepted`): $([Math]::Round($anonymousAccepted, 2))",
  "",
  "## Available Tags",
  $availableTagsText,
  "",
  "## Notes",
  $notesBlock
)

$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "Auth legacy usage report created: $reportPath"
Write-Output "Status: $status | LegacyAccepted: $legacyAccepted | Threshold: $MaxLegacyAccepted"

if ($thresholdBreached -and -not $NoFail) {
  exit 1
}
if ($status -eq "UNAVAILABLE" -and -not $NoFail) {
  exit 2
}
