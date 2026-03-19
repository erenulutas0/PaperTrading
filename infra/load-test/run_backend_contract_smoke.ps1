param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutputDir = "infra/load-test/reports",
  [int]$TimeoutSec = 15,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )

  $params = @{
    Method      = $Method
    Uri         = $Url
    TimeoutSec  = $TimeoutSec
    ErrorAction = "Stop"
  }

  if ($PSVersionTable.PSVersion.Major -ge 7) {
    $params.SkipHttpErrorCheck = $true
  } else {
    $params.UseBasicParsing = $true
  }

  if ($Headers.Count -gt 0) {
    $params.Headers = $Headers
  }

  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 10)
    $params.ContentType = "application/json"
  }

  try {
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{
      ok      = $true
      status  = [int]$response.StatusCode
      content = $response.Content
      headers = $response.Headers
      error   = ""
    }
  } catch {
    $statusCode = 0
    $content = ""
    $headers = @{}

    if ($_.Exception.PSObject.Properties["Response"] -and $null -ne $_.Exception.Response) {
      try {
        if ($_.Exception.Response.PSObject.Properties["StatusCode"]) {
          $statusCode = [int]$_.Exception.Response.StatusCode
        }
        if ($_.Exception.Response.PSObject.Properties["Headers"]) {
          $headers = $_.Exception.Response.Headers
        }
        if ($_.Exception.Response.PSObject.Methods.Name -contains "GetResponseStream") {
          $stream = $_.Exception.Response.GetResponseStream()
          if ($null -ne $stream) {
            $reader = New-Object System.IO.StreamReader($stream)
            $content = $reader.ReadToEnd()
            $reader.Dispose()
            $stream.Dispose()
          }
        }
      } catch {
      }
    }

    return [pscustomobject]@{
      ok      = $false
      status  = $statusCode
      content = $content
      headers = $headers
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

$health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

$registerBody = @{
  username = "contract_$suffix"
  email    = "contract_$suffix@test.com"
  password = "P@ssw0rd!123456"
}

$register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $registerBody
$registerJson = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
$accessToken = if ($null -ne $registerJson) { [string]$registerJson.accessToken } else { "" }
$userId = if ($null -ne $registerJson) { [string]$registerJson.id } else { "" }

Assert-Condition -Results $results -Name "Register User" -Condition ($register.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken)) -Detail "status=$($register.status)"

$requestId = "contract-smoke-$suffix"
$portfolioBody = @{
  name       = "Contract Smoke $suffix"
  ownerId    = $userId
  visibility = "PUBLIC"
}

$createHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "Idempotency-Key" = "contract-idem-$suffix"
  "X-Request-Id"    = $requestId
}

$createOne = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $createHeaders -Body $portfolioBody
$createOneJson = if ($createOne.content) { $createOne.content | ConvertFrom-Json } else { $null }
Assert-Condition -Results $results -Name "Create Portfolio" -Condition ($createOne.status -eq 200) -Detail "status=$($createOne.status)"

$replayHeaders = @{
  "Authorization"   = "Bearer $accessToken"
  "Idempotency-Key" = "contract-idem-$suffix"
  "X-Request-Id"    = "$requestId-replay"
}

$createReplay = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/portfolios" -Headers $replayHeaders -Body $portfolioBody
$replayHeader = Get-HeaderValue -Headers $createReplay.headers -Name "X-Idempotent-Replay"
$replayRequestId = Get-HeaderValue -Headers $createReplay.headers -Name "X-Request-Id"
Assert-Condition -Results $results -Name "Idempotent Replay" -Condition ($createReplay.status -eq 200 -and $replayHeader -eq "true") -Detail "status=$($createReplay.status), replay=$replayHeader"
Assert-Condition -Results $results -Name "Replay Request Id Echo" -Condition ($replayRequestId -eq "$requestId-replay") -Detail "header=$replayRequestId"

$notification401 = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/unread-count" -Headers @{ "X-Request-Id" = "contract-auth-$suffix" }
$notification401Json = if ($notification401.content) { $notification401.content | ConvertFrom-Json } else { $null }
$notifHeaderRequestId = Get-HeaderValue -Headers $notification401.headers -Name "X-Request-Id"
$notificationCode = [string](Get-ObjectPropertyValue -Object $notification401Json -Name "code")
$notificationRequestId = [string](Get-ObjectPropertyValue -Object $notification401Json -Name "requestId")
Assert-Condition -Results $results -Name "Unauthorized Contract" -Condition ($notification401.status -eq 401 -and $notificationCode -eq "unauthorized") -Detail "status=$($notification401.status), code=$notificationCode"
Assert-Condition -Results $results -Name "Unauthorized Request Id Echo" -Condition ($notifHeaderRequestId -eq "contract-auth-$suffix" -and $notificationRequestId -eq "contract-auth-$suffix") -Detail "header=$notifHeaderRequestId body=$notificationRequestId"

$idempotencyActuator = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/idempotency"
$idempotencyJson = if ($idempotencyActuator.content) { $idempotencyActuator.content | ConvertFrom-Json } else { $null }
$idempotencyTotal = Get-ObjectPropertyValue -Object $idempotencyJson -Name "totalRecords"
Assert-Condition -Results $results -Name "Idempotency Actuator" -Condition ($idempotencyActuator.status -eq 200 -and $null -ne $idempotencyTotal) -Detail "status=$($idempotencyActuator.status), total=$idempotencyTotal"

$auditOps = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/ops/auditlog?requestId=$([System.Uri]::EscapeDataString($requestId))"
$auditOpsJson = if ($auditOps.content) { $auditOps.content | ConvertFrom-Json } else { $null }
$auditOpsCountValue = Get-ObjectPropertyValue -Object $auditOpsJson -Name "count"
$auditOpsCount = if ($null -ne $auditOpsCountValue) { [int]$auditOpsCountValue } else { -1 }
Assert-Condition -Results $results -Name "Audit Ops REST" -Condition ($auditOps.status -eq 200 -and $auditOpsCount -ge 1) -Detail "status=$($auditOps.status), count=$auditOpsCount"

$auditActuator = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/auditlog?requestId=$([System.Uri]::EscapeDataString($requestId))"
$auditActuatorJson = if ($auditActuator.content) { $auditActuator.content | ConvertFrom-Json } else { $null }
$auditActuatorCountValue = Get-ObjectPropertyValue -Object $auditActuatorJson -Name "count"
$auditActuatorCount = if ($null -ne $auditActuatorCountValue) { [int]$auditActuatorCountValue } else { -1 }
Assert-Condition -Results $results -Name "Audit Actuator" -Condition ($auditActuator.status -eq 200 -and $auditActuatorCount -ge 1) -Detail "status=$($auditActuator.status), count=$auditActuatorCount"

$allPassed = ($results | Where-Object { -not $_.Passed }).Count -eq 0

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$reportPath = Join-Path $OutputDir "backend-contract-smoke-$timestamp.md"

$lines = @()
$lines += "# Backend Contract Smoke"
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

Write-Host "Backend contract smoke report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $(($results | Where-Object { -not $_.Passed }).Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
