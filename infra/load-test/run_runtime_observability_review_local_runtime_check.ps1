param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 18091,
  [int]$StartupTimeoutSec = 180,
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$SkipAppStart,
  [switch]$PreserveAppAfterRun,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds(20)

function Test-PortOpen {
  param([int]$Port)

  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    $ok = $async.AsyncWaitHandle.WaitOne(200)
    if (-not $ok) {
      return $false
    }
    $client.EndConnect($async) | Out-Null
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url
  )

  try {
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

    return [pscustomobject]@{
      status  = [int]$response.StatusCode
      content = $content
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      status  = 0
      content = ""
      error   = $_.Exception.Message
    }
  }
}

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $response = Invoke-Request -Method "GET" -Url $Url
    if ($response.status -eq 200) {
      try {
        $json = $response.content | ConvertFrom-Json
        if ([string]$json.status -eq "UP") {
          return $true
        }
      } catch {
      }
    }
    Start-Sleep -Seconds 2
  }

  return $false
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

function Get-LatestReport {
  param(
    [string]$Directory,
    [string]$Pattern
  )

  $file = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
  return $file
}

function Get-ContentText {
  param([string]$Path)

  if (-not (Test-Path $Path)) {
    return ""
  }

  return Get-Content -Path $Path -Raw
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "runtime-observability-review-local-runtime-check-$timestamp.md"
$appLogPath = Join-Path $ReportsDir "runtime-observability-review-local-runtime-app-$timestamp.log"
$appErrLogPath = Join-Path $ReportsDir "runtime-observability-review-local-runtime-app-$timestamp.err.log"

$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$appProcess = $null
$appStartedByScript = $false
$childReportPath = ""
$childStatus = "UNKNOWN"
$baseUrl = "http://localhost:$ServerPort"
$healthUrl = "$baseUrl/actuator/health"

try {
  if (-not $SkipAppStart) {
    if (Test-PortOpen -Port $ServerPort) {
      Assert-Condition -Results $results -Name "Requested Server Port Available" -Condition $false -Detail "port=$ServerPort"
      throw "Requested server port $ServerPort is already in use. Stop the competing backend, choose a free -ServerPort, or rerun with -SkipAppStart against the already running instance."
    }

    $coreApiPath = Resolve-Path $CoreApiDir
    $repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
    $mavenUserHome = Join-Path $repoRoot ".m2"
    $mavenRepoPath = Join-Path $repoRoot ".m2\repository"
    $null = New-Item -ItemType Directory -Path $mavenUserHome -Force
    $null = New-Item -ItemType Directory -Path $mavenRepoPath -Force

    $appCommand = @(
      "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/finance_db'",
      "`$env:SPRING_DATASOURCE_USERNAME='postgres'",
      "`$env:SPRING_DATASOURCE_PASSWORD='password'",
      "`$env:REDIS_URL='redis://localhost:6379'",
      "`$env:APP_ALERTING_ENABLED='false'",
      "`$env:APP_MARKET_WS_ENABLED='false'",
      "`$env:JWT_SECRET='local-dev-secret-change-me-at-least-32-bytes'",
      "`$env:PORT='$ServerPort'",
      "`$env:MAVEN_USER_HOME='$mavenUserHome'",
      "`$env:MAVEN_OPTS='-Dmaven.repo.local=$mavenRepoPath'",
      "`$env:JAVA_TOOL_OPTIONS='-Duser.home=$repoRoot'",
      "Set-Location '$coreApiPath'",
      ".\\mvnw.cmd -q '-Dmaven.repo.local=$mavenRepoPath' spring-boot:run"
    ) -join "; "

    $appProcess = Start-Process -FilePath "powershell" `
      -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
      -WorkingDirectory $coreApiPath `
      -RedirectStandardOutput $appLogPath `
      -RedirectStandardError $appErrLogPath `
      -PassThru
    $appStartedByScript = $true
  }

  $healthy = Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec
  Assert-Condition -Results $results -Name "Backend Health" -Condition $healthy -Detail "url=$healthUrl"
  if (-not $healthy) {
    throw "Core API did not become healthy at $healthUrl within $StartupTimeoutSec seconds."
  }

  $beforeChild = Get-LatestReport -Directory $ReportsDir -Pattern "runtime-observability-review-checklist-*.md"
  $beforeTimestamp = if ($null -ne $beforeChild) { $beforeChild.LastWriteTimeUtc } else { [datetime]::MinValue }

  & (Join-Path $PSScriptRoot "run_runtime_observability_review_checklist.ps1") `
    -BaseUrl $baseUrl `
    -ReportsDir $ReportsDir `
    -NoFail | Out-Null

  $afterChild = Get-LatestReport -Directory $ReportsDir -Pattern "runtime-observability-review-checklist-*.md"
  if ($null -ne $afterChild -and $afterChild.LastWriteTimeUtc -ge $beforeTimestamp) {
    $childReportPath = $afterChild.FullName
    $childReportContent = Get-ContentText -Path $childReportPath
    if ($childReportContent -match "- Status:\s+\*\*(PASSED|FAILED|READY|CONDITIONAL_READY|NOT_READY|UNKNOWN|SKIPPED)\*\*") {
      $childStatus = $Matches[1]
    }
  }

  Assert-Condition -Results $results -Name "Child Checklist Report Created" -Condition (-not [string]::IsNullOrWhiteSpace($childReportPath)) -Detail "$(if ($childReportPath) { $childReportPath } else { '<none>' })"
  Assert-Condition -Results $results -Name "Child Checklist Passed" -Condition ($childStatus -eq "PASSED") -Detail "status=$childStatus"
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes.Add($_.InvocationInfo.PositionMessage) | Out-Null
  }
} finally {
  if ($appStartedByScript -and $appProcess) {
    try {
      if (-not $PreserveAppAfterRun -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
      }
    } catch {
    }
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$overallStatus = if ($failedResults.Count -eq 0) { "PASSED" } else { "FAILED" }

$lines = @()
$lines += "# Runtime Observability Review Local Runtime Check"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $baseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Child Checklist Status: **$childStatus**"
$lines += "- Child Checklist Report: $(if ($childReportPath) { $childReportPath } else { '<none>' })"
$lines += "- App Log: $appLogPath"
$lines += "- App Error Log: $appErrLogPath"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $detail = [string]$result.Detail
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($detail.Replace('|', '/')) |"
}
if ($notes.Count -gt 0) {
  $lines += ""
  $lines += "## Notes"
  foreach ($note in $notes) {
    $lines += "- $($note.Replace('|', '/'))"
  }
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Runtime observability review local runtime report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
