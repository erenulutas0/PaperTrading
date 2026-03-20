param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [string]$PostgresContainer = "finance-app-postgres",
  [string]$DatabaseName = "finance_db",
  [string]$DatabaseHost = "localhost",
  [int]$DatabasePort = 5433,
  [string]$DatabaseUser = "postgres",
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

function Invoke-DockerSql {
  param(
    [string]$Sql
  )

  $output = & docker exec $PostgresContainer psql -U postgres -d $DatabaseName -t -A -c $Sql 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "docker exec psql failed: $output"
  }
  return $output.Trim()
}

function Invoke-PsqlSql {
  param(
    [string]$Sql
  )

  $psql = Get-Command psql -ErrorAction SilentlyContinue
  if ($null -eq $psql) {
    throw "psql not found on PATH"
  }

  $env:PGPASSWORD = ""
  $output = & $psql.Source -h $DatabaseHost -p $DatabasePort -U $DatabaseUser -d $DatabaseName -t -A -c $Sql 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "psql failed: $output"
  }
  return $output.Trim()
}

function Invoke-SeedSql {
  param(
    [string]$Sql
  )

  $psql = Get-Command psql -ErrorAction SilentlyContinue
  if ($null -ne $psql) {
    return Invoke-PsqlSql -Sql $Sql
  }
  return Invoke-DockerSql -Sql $Sql
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$registerBody = @{
  username = "idem_cleanup_$suffix"
  email    = "idem_cleanup_$suffix@test.com"
  password = "P@ssw0rd!123456"
}

$register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $registerBody
$registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
$accessToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
$userId = if ($null -ne $registerJson) { [string]$registerJson.id } else { "" }
Assert-Condition -Results $results -Name "Register User" -Condition ($register.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken)) -Detail "status=$($register.status)"

$requestId = "idem-cleanup-$suffix"
$portfolioBody = @{
  name       = "Idem Cleanup $suffix"
  ownerId    = $userId
  visibility = "PUBLIC"
}
$idemHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "Idempotency-Key" = "idem-cleanup-key-$suffix"
  "X-Request-Id"    = $requestId
}

$createOne = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $idemHeaders -Body $portfolioBody
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($createOne.status -eq 200) -Detail "status=$($createOne.status)"

$replayHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "Idempotency-Key" = "idem-cleanup-key-$suffix"
  "X-Request-Id"    = "$requestId-replay-before"
}
$createReplayBefore = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $replayHeaders -Body $portfolioBody
Assert-Condition -Results $results -Name "Replay Before Cleanup" -Condition ($createReplayBefore.status -eq 200 -and (Get-HeaderValue -Headers $createReplayBefore.headers -Name "X-Idempotent-Replay") -eq "true") -Detail "status=$($createReplayBefore.status), replay=$(Get-HeaderValue -Headers $createReplayBefore.headers -Name "X-Idempotent-Replay")"

$expiredId = [guid]::NewGuid().ToString()
$expiredKey = "expired-cleanup-$suffix"
$insertSql = @"
INSERT INTO idempotency_keys (
  id,
  actor_scope,
  idempotency_key,
  request_method,
  request_path,
  request_hash,
  status,
  response_status,
  response_content_type,
  response_body,
  created_at,
  completed_at,
  expires_at
) VALUES (
  '$expiredId',
  'cleanup-smoke',
  '$expiredKey',
  'POST',
  '/api/v1/interactions/demo/comments',
  'hash-cleanup',
  'COMPLETED',
  200,
  'application/json',
  '{}',
  NOW() - INTERVAL '2 hours',
  NOW() - INTERVAL '90 minutes',
  NOW() - INTERVAL '10 minutes'
);
"@

Invoke-SeedSql -Sql $insertSql | Out-Null

$beforeCleanup = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/idempotency"
$beforeCleanupJson = if ($beforeCleanup.content) { $beforeCleanup.content | ConvertFrom-Json } else { $null }
$beforeExpired = [int](Get-ObjectPropertyValue -Object $beforeCleanupJson -Name "expiredRecords")
Assert-Condition -Results $results -Name "Expired Record Visible" -Condition ($beforeCleanup.status -eq 200 -and $beforeExpired -ge 1) -Detail "status=$($beforeCleanup.status), expired=$beforeExpired"

$cleanup = Invoke-Request -Method "POST" -Url "$BaseUrl/actuator/idempotency"
$cleanupJson = if ($cleanup.content) { $cleanup.content | ConvertFrom-Json } else { $null }
$cleanupDeleted = [int](Get-ObjectPropertyValue -Object $cleanupJson -Name "lastCleanupDeletedCount")
$cleanupExpired = [int](Get-ObjectPropertyValue -Object $cleanupJson -Name "expiredRecords")
Assert-Condition -Results $results -Name "Cleanup Write Operation" -Condition ($cleanup.status -eq 200 -and $cleanupDeleted -ge 1 -and $cleanupExpired -eq 0) -Detail "status=$($cleanup.status), deleted=$cleanupDeleted, expired=$cleanupExpired"

$afterCleanup = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/idempotency"
$afterCleanupJson = if ($afterCleanup.content) { $afterCleanup.content | ConvertFrom-Json } else { $null }
$afterExpired = [int](Get-ObjectPropertyValue -Object $afterCleanupJson -Name "expiredRecords")
$afterTotal = [int](Get-ObjectPropertyValue -Object $afterCleanupJson -Name "totalRecords")
Assert-Condition -Results $results -Name "Cleanup Snapshot Stable" -Condition ($afterCleanup.status -eq 200 -and $afterExpired -eq 0 -and $afterTotal -ge 1) -Detail "status=$($afterCleanup.status), total=$afterTotal, expired=$afterExpired"

$replayAfterHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "Idempotency-Key" = "idem-cleanup-key-$suffix"
  "X-Request-Id"    = "$requestId-replay-after"
}
$createReplayAfter = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $replayAfterHeaders -Body $portfolioBody
$replayAfterHeader = Get-HeaderValue -Headers $createReplayAfter.headers -Name "X-Idempotent-Replay"
Assert-Condition -Results $results -Name "Replay After Cleanup" -Condition ($createReplayAfter.status -eq 200 -and $replayAfterHeader -eq "true") -Detail "status=$($createReplayAfter.status), replay=$replayAfterHeader"

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "idempotency-cleanup-smoke-$timestamp.md"

$lines = @()
$lines += "# Idempotency Cleanup Smoke"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Postgres container: $PostgresContainer"
$lines += "- Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' })"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Idempotency cleanup smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
