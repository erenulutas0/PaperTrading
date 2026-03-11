param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$RequestTimeoutSec = 15,
  [int]$InvalidJwtAttempts = 80,
  [int]$HeaderMismatchAttempts = 60,
  [int]$InvalidRefreshAttempts = 80,
  [int]$CanaryProbeAttempts = 12,
  [int]$CanaryProbeIntervalMs = 500,
  [switch]$FailOnAnyFailure,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Percentile {
  param(
    [double[]]$Values,
    [double]$Percentile
  )

  if ($null -eq $Values -or $Values.Count -eq 0) {
    return 0.0
  }
  $sorted = $Values | Sort-Object
  $rank = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
  if ($rank -lt 0) { $rank = 0 }
  if ($rank -ge $sorted.Count) { $rank = $sorted.Count - 1 }
  return [double]$sorted[$rank]
}

function To-DoubleSafe {
  param([object]$Value)
  if ($null -eq $Value) {
    return 0.0
  }
  try {
    return [double]$Value
  } catch {
    return 0.0
  }
}

function Get-CanaryAlertState {
  param([object]$Payload)

  if ($null -eq $Payload) {
    return ""
  }

  $direct = $Payload.PSObject.Properties["alertState"]
  if ($null -ne $direct) {
    return [string]$direct.Value
  }

  $details = $Payload.PSObject.Properties["details"]
  if ($null -ne $details -and $null -ne $details.Value) {
    $nested = $details.Value.PSObject.Properties["alertState"]
    if ($null -ne $nested) {
      return [string]$nested.Value
    }
  }

  return ""
}

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int]$TimeoutSec = 15
  )

  $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
  try {
    $params = @{
      Method             = $Method
      Uri                = $Url
      TimeoutSec         = $TimeoutSec
      ErrorAction        = "Stop"
      SkipHttpErrorCheck = $true
    }
    if ($Headers.Count -gt 0) {
      $params.Headers = $Headers
    }
    if ($null -ne $Body) {
      $params.Body = ($Body | ConvertTo-Json -Depth 10)
      $params.ContentType = "application/json"
    }

    $response = Invoke-WebRequest @params
    $stopwatch.Stop()
    return [pscustomobject]@{
      status    = [int]$response.StatusCode
      latencyMs = [double]$stopwatch.Elapsed.TotalMilliseconds
      content   = $response.Content
      ok        = $true
      error     = ""
    }
  } catch {
    $stopwatch.Stop()
    return [pscustomobject]@{
      status    = 0
      latencyMs = [double]$stopwatch.Elapsed.TotalMilliseconds
      content   = ""
      ok        = $false
      error     = $_.Exception.Message
    }
  }
}

function Invoke-HealthProbe {
  param(
    [string]$BaseUrl,
    [int]$TimeoutSec
  )

  $attempts = 5
  for ($i = 1; $i -le $attempts; $i++) {
    $response = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health" -TimeoutSec $TimeoutSec
    if ($response.status -ne 429 -and $response.status -ne 503) {
      return $response
    }
    if ($i -lt $attempts) {
      Start-Sleep -Seconds 2
    }
  }

  return $response
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

  $response = Invoke-Request -Method "GET" -Url $endpoint -TimeoutSec $TimeoutSec
  if (-not $response.ok -or $response.status -lt 200 -or $response.status -ge 300) {
    return @{
      ok    = $false
      value = 0.0
      error = if ($response.error) { $response.error } else { "HTTP $($response.status)" }
    }
  }

  try {
    $payload = if ([string]::IsNullOrWhiteSpace($response.content)) { $null } else { $response.content | ConvertFrom-Json }
    if ($null -eq $payload -or $null -eq $payload.measurements -or $payload.measurements.Count -eq 0) {
      return @{ ok = $true; value = 0.0; error = "" }
    }
    $sum = ($payload.measurements | Measure-Object -Property value -Sum).Sum
    return @{
      ok    = $true
      value = [double](if ($null -eq $sum) { 0.0 } else { $sum })
      error = ""
    }
  } catch {
    return @{
      ok    = $false
      value = 0.0
      error = "Metric parse error: $($_.Exception.Message)"
    }
  }
}

function Register-TestUser {
  param(
    [string]$BaseUrl,
    [string]$Suffix,
    [string]$Label,
    [int]$TimeoutSec
  )

  $payload = @{
    username = "atk_${Label}_$Suffix"
    email    = "atk_${Label}_$Suffix@test.com"
    password = "P@ssw0rd!123456"
  }
  $response = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $payload -TimeoutSec $TimeoutSec
  if ($response.status -ne 200) {
    throw "User registration failed for ${Label}: status=$($response.status), error=$($response.error)"
  }
  $json = $response.content | ConvertFrom-Json
  if ($null -eq $json.accessToken -or [string]::IsNullOrWhiteSpace([string]$json.accessToken)) {
    throw "User registration did not return accessToken for $Label"
  }
  return [pscustomobject]@{
    id          = [string]$json.id
    username    = [string]$json.username
    accessToken = [string]$json.accessToken
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Path $reportsDir -Force
$reportPath = Join-Path $reportsDir "auth-attack-scenarios-$timestamp.md"
$notes = New-Object System.Collections.Generic.List[string]

$health = Invoke-HealthProbe -BaseUrl $BaseUrl -TimeoutSec $RequestTimeoutSec
if (($health.status -lt 200 -or $health.status -ge 300) -and $health.status -ne 429 -and $health.status -ne 503) {
  $notes.Add("Health endpoint unavailable: status=$($health.status), error=$($health.error)")
  $status = "UNAVAILABLE"
  $report = @(
    "# Auth Attack Scenario Run",
    "",
    "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
    "",
    "## Result",
    "- Status: **$status**",
    "",
    "## Notes",
    ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine
  ) -join [Environment]::NewLine
  Set-Content -Path $reportPath -Value $report -Encoding UTF8
  Write-Output "Auth attack scenario report created: $reportPath"
  Write-Output "Status: $status"
  if (-not $NoFail) { exit 2 }
  return
}
if ($health.status -eq 429) {
  $notes.Add("Health endpoint returned 429 during warmup; continuing attack run because app-level rate limiting is active on the deployed instance.")
}
if ($health.status -eq 503) {
  $notes.Add("Health endpoint returned 503 during warmup; continuing attack run because non-auth health contributors may be degraded while auth paths remain reachable.")
}

$suffix = "$(Get-Date -Format 'yyyyMMddHHmmss')_$([guid]::NewGuid().ToString('N').Substring(0,8))"
$userA = Register-TestUser -BaseUrl $BaseUrl -Suffix $suffix -Label "a" -TimeoutSec $RequestTimeoutSec
$userB = Register-TestUser -BaseUrl $BaseUrl -Suffix $suffix -Label "b" -TimeoutSec $RequestTimeoutSec

$metricBeforeInvalidToken = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.requests.total" -Tags @{
  mode   = "bearer"
  result = "rejected_invalid_token"
} -TimeoutSec $RequestTimeoutSec
$metricBeforeMismatch = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.requests.total" -Tags @{
  mode   = "bearer"
  result = "rejected_header_token_mismatch"
} -TimeoutSec $RequestTimeoutSec
$metricBeforeRefreshInvalid = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.sessions.total" -Tags @{
  operation = "refresh"
  result    = "invalid"
} -TimeoutSec $RequestTimeoutSec

$scenarioRows = New-Object System.Collections.Generic.List[object]

function Run-StatusScenario {
  param(
    [string]$Name,
    [int]$Attempts,
    [int[]]$ExpectedStatuses,
    [scriptblock]$RequestFactory
  )

  $latencies = New-Object System.Collections.Generic.List[double]
  $expectedCount = 0
  $unexpectedCount = 0
  $firstUnexpected = ""

  for ($i = 1; $i -le $Attempts; $i++) {
    $response = & $RequestFactory $i
    $latencies.Add([double]$response.latencyMs)

    if ($ExpectedStatuses -contains $response.status) {
      $expectedCount++
    } else {
      $unexpectedCount++
      if ([string]::IsNullOrWhiteSpace($firstUnexpected)) {
        $firstUnexpected = "attempt=$i,status=$($response.status),error=$($response.error)"
      }
    }
  }

  return [pscustomobject]@{
    name            = $Name
    attempts        = $Attempts
    expectedStatus  = ($ExpectedStatuses -join "/")
    expectedCount   = $expectedCount
    unexpectedCount = $unexpectedCount
    avgLatencyMs    = [Math]::Round((($latencies | Measure-Object -Average).Average), 2)
    p95LatencyMs    = [Math]::Round((Get-Percentile -Values $latencies.ToArray() -Percentile 95), 2)
    p99LatencyMs    = [Math]::Round((Get-Percentile -Values $latencies.ToArray() -Percentile 99), 2)
    firstUnexpected = $firstUnexpected
  }
}

$invalidJwtScenario = Run-StatusScenario -Name "invalid-jwt-flood" -Attempts $InvalidJwtAttempts -ExpectedStatuses @(401) -RequestFactory {
  param($i)
  Invoke-Request -Method "GET" `
    -Url "$BaseUrl/api/v1/users/me/preferences" `
    -Headers @{ Authorization = "Bearer invalid-token-$i" } `
    -TimeoutSec $RequestTimeoutSec
}
$scenarioRows.Add($invalidJwtScenario)

$mismatchScenario = Run-StatusScenario -Name "bearer-header-mismatch-flood" -Attempts $HeaderMismatchAttempts -ExpectedStatuses @(401) -RequestFactory {
  param($i)
  Invoke-Request -Method "GET" `
    -Url "$BaseUrl/api/v1/users/me/preferences" `
    -Headers @{
      Authorization = "Bearer $($userA.accessToken)"
      "X-User-Id"   = $userB.id
    } `
    -TimeoutSec $RequestTimeoutSec
}
$scenarioRows.Add($mismatchScenario)

$invalidRefreshScenario = Run-StatusScenario -Name "invalid-refresh-flood" -Attempts $InvalidRefreshAttempts -ExpectedStatuses @(401, 429) -RequestFactory {
  param($i)
  Invoke-Request -Method "POST" `
    -Url "$BaseUrl/api/v1/auth/refresh" `
    -Body @{ refreshToken = "invalid-refresh-token-$i-$suffix" } `
    -TimeoutSec $RequestTimeoutSec
}
$scenarioRows.Add($invalidRefreshScenario)

$validBearerRead = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/unread-count" -Headers @{
  Authorization = "Bearer $($userA.accessToken)"
} -TimeoutSec $RequestTimeoutSec

$canaryAvailable = $true
$canaryProbeFailures = 0
$canaryStateWarnings = 0
$canaryStateCritical = 0
$canaryLatencies = New-Object System.Collections.Generic.List[double]
$canaryFirstError = ""

$canaryWarmup = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/websocketcanary" -TimeoutSec $RequestTimeoutSec
if ($canaryWarmup.status -lt 200 -or $canaryWarmup.status -ge 300) {
  $canaryAvailable = $false
  $notes.Add("WebSocket canary endpoint unavailable. status=$($canaryWarmup.status) error=$($canaryWarmup.error)")
} else {
  for ($i = 1; $i -le $CanaryProbeAttempts; $i++) {
    $probe = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/websocketcanary" -TimeoutSec $RequestTimeoutSec
    $canaryLatencies.Add([double]$probe.latencyMs)
    if ($probe.status -ne 200) {
      $canaryProbeFailures++
      if ([string]::IsNullOrWhiteSpace($canaryFirstError)) {
        $canaryFirstError = "attempt=$i,status=$($probe.status),error=$($probe.error)"
      }
    } else {
      try {
        $payload = $probe.content | ConvertFrom-Json
        $alertState = Get-CanaryAlertState -Payload $payload
        if ($alertState -eq "WARNING") { $canaryStateWarnings++ }
        if ($alertState -eq "CRITICAL") { $canaryStateCritical++ }
      } catch {
        $canaryProbeFailures++
        if ([string]::IsNullOrWhiteSpace($canaryFirstError)) {
          $canaryFirstError = "attempt=$i,parse-error=$($_.Exception.Message)"
        }
      }
    }
    if ($CanaryProbeIntervalMs -gt 0 -and $i -lt $CanaryProbeAttempts) {
      Start-Sleep -Milliseconds $CanaryProbeIntervalMs
    }
  }
}

$metricAfterInvalidToken = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.requests.total" -Tags @{
  mode   = "bearer"
  result = "rejected_invalid_token"
} -TimeoutSec $RequestTimeoutSec
$metricAfterMismatch = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.requests.total" -Tags @{
  mode   = "bearer"
  result = "rejected_header_token_mismatch"
} -TimeoutSec $RequestTimeoutSec
$metricAfterRefreshInvalid = Get-MetricValue -BaseUrl $BaseUrl -MetricName "app.auth.sessions.total" -Tags @{
  operation = "refresh"
  result    = "invalid"
} -TimeoutSec $RequestTimeoutSec

$deltaInvalidToken = [Math]::Round((To-DoubleSafe $metricAfterInvalidToken.value) - (To-DoubleSafe $metricBeforeInvalidToken.value), 2)
$deltaMismatch = [Math]::Round((To-DoubleSafe $metricAfterMismatch.value) - (To-DoubleSafe $metricBeforeMismatch.value), 2)
$deltaRefreshInvalid = [Math]::Round((To-DoubleSafe $metricAfterRefreshInvalid.value) - (To-DoubleSafe $metricBeforeRefreshInvalid.value), 2)

$scenarioUnexpectedTotal = ($scenarioRows | Measure-Object -Property unexpectedCount -Sum).Sum
$status = "PASSED"
if ($validBearerRead.status -ne 200) {
  $status = "FAILED"
  $notes.Add("Bearer-only notification unread-count request failed: status=$($validBearerRead.status), error=$($validBearerRead.error)")
}
if ($scenarioUnexpectedTotal -gt 0) {
  $status = "FAILED"
}
if ($canaryAvailable -and $canaryProbeFailures -gt 0) {
  $status = "FAILED"
}

$scenarioTable = @()
$scenarioTable += "| Scenario | Attempts | ExpectedStatus | ExpectedCount | UnexpectedCount | Avg(ms) | P95(ms) | P99(ms) | FirstUnexpected |"
$scenarioTable += "|----------|----------|----------------|---------------|-----------------|---------|---------|---------|-----------------|"
foreach ($row in $scenarioRows) {
  $scenarioTable += "| $($row.name) | $($row.attempts) | $($row.expectedStatus) | $($row.expectedCount) | $($row.unexpectedCount) | $($row.avgLatencyMs) | $($row.p95LatencyMs) | $($row.p99LatencyMs) | $($row.firstUnexpected) |"
}

$canaryLines = @()
if (-not $canaryAvailable) {
  $canaryLines += "- unavailable"
} else {
  $avgCanary = if ($canaryLatencies.Count -gt 0) { [Math]::Round((($canaryLatencies | Measure-Object -Average).Average), 2) } else { 0.0 }
  $p95Canary = [Math]::Round((Get-Percentile -Values $canaryLatencies.ToArray() -Percentile 95), 2)
  $p99Canary = [Math]::Round((Get-Percentile -Values $canaryLatencies.ToArray() -Percentile 99), 2)
  $canaryLines += "- canary probes: $CanaryProbeAttempts"
  $canaryLines += "- probe failures: $canaryProbeFailures"
  $canaryLines += "- warning states: $canaryStateWarnings"
  $canaryLines += "- critical states: $canaryStateCritical"
  $canaryLines += "- avg latency ms: $avgCanary"
  $canaryLines += "- p95 latency ms: $p95Canary"
  $canaryLines += "- p99 latency ms: $p99Canary"
  $canaryLines += "- first probe error: $canaryFirstError"
}

$metricsNotes = @()
if (-not $metricBeforeInvalidToken.ok -or -not $metricAfterInvalidToken.ok) {
  $metricsNotes += "- invalid-token metric unavailable"
}
if (-not $metricBeforeMismatch.ok -or -not $metricAfterMismatch.ok) {
  $metricsNotes += "- header-mismatch metric unavailable"
}
if (-not $metricBeforeRefreshInvalid.ok -or -not $metricAfterRefreshInvalid.ok) {
  $metricsNotes += "- invalid-refresh metric unavailable"
}
if ($metricsNotes.Count -eq 0) {
  $metricsNotes += "- none"
}

$notesBlock = if ($notes.Count -eq 0) {
  "- none"
} else {
  ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine
}

$reportLines = @(
  "# Auth Attack Scenario Run",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- Invalid JWT attempts: $InvalidJwtAttempts",
  "- Header mismatch attempts: $HeaderMismatchAttempts",
  "- Invalid refresh attempts: $InvalidRefreshAttempts",
  "- WebSocket canary probe attempts: $CanaryProbeAttempts",
  "- Request timeout seconds: $RequestTimeoutSec",
  "",
  "## Result",
  "- Status: **$status**",
  "- Bearer-only notifications unread-count status: $($validBearerRead.status)",
  "",
  "## HTTP Attack Outcomes",
  ($scenarioTable -join [Environment]::NewLine),
  "",
  "## Auth Metrics Delta",
  "- app.auth.requests.total {mode=bearer,result=rejected_invalid_token}: $deltaInvalidToken",
  "- app.auth.requests.total {mode=bearer,result=rejected_header_token_mismatch}: $deltaMismatch",
  "- app.auth.sessions.total {operation=refresh,result=invalid}: $deltaRefreshInvalid",
  "",
  "## WebSocket Canary Probe Stress",
  ($canaryLines -join [Environment]::NewLine),
  "",
  "## Metrics Collection Notes",
  ($metricsNotes -join [Environment]::NewLine),
  "",
  "## Notes",
  $notesBlock
)

$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "Auth attack scenario report created: $reportPath"
Write-Output "Status: $status | Unexpected HTTP outcomes: $scenarioUnexpectedTotal | CanaryFailures: $canaryProbeFailures"

if ($status -eq "FAILED" -and $FailOnAnyFailure -and -not $NoFail) {
  exit 1
}
