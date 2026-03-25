param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 18086,
  [int]$StartupTimeoutSec = 180,
  [int]$TimeoutSec = 20,
  [int]$PollAttempts = 12,
  [int]$PollIntervalSec = 5,
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
$httpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)

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

function Get-LatestReportSnapshot {
  param(
    [string]$Directory,
    [string]$Pattern
  )

  $file = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
  if ($null -eq $file) {
    return $null
  }

  return [pscustomobject]@{
    Path             = $file.FullName
    LastWriteTimeUtc = $file.LastWriteTimeUtc
  }
}

function Resolve-NewReportPath {
  param(
    [object]$BeforeSnapshot,
    [object]$AfterSnapshot
  )

  if ($null -eq $AfterSnapshot) {
    return ""
  }

  if ($null -eq $BeforeSnapshot) {
    return [string]$AfterSnapshot.Path
  }

  if ($AfterSnapshot.Path -ne $BeforeSnapshot.Path) {
    return [string]$AfterSnapshot.Path
  }

  if ($AfterSnapshot.LastWriteTimeUtc -gt $BeforeSnapshot.LastWriteTimeUtc) {
    return [string]$AfterSnapshot.Path
  }

  return ""
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
$reportPath = Join-Path $ReportsDir "strategy-bot-summary-precompute-local-runtime-check-$timestamp.md"
$appLogPath = Join-Path $ReportsDir "strategy-bot-summary-precompute-local-runtime-app-$timestamp.log"
$appErrLogPath = Join-Path $ReportsDir "strategy-bot-summary-precompute-local-runtime-app-$timestamp.err.log"

$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$appProcess = $null
$appStartedByScript = $false
$childReportPath = ""
$childSmokeStatus = "UNKNOWN"
$baseUrl = "http://localhost:$ServerPort"

$healthUrl = "$baseUrl/actuator/health"

try {
  if (-not $SkipAppStart) {
    if (Test-PortOpen -Port $ServerPort) {
      $portConflictMessage = "Requested server port $ServerPort is already in use. This local runtime wrapper requires an exclusive backend instance so the temporary runtime owns the summary-refresh scheduler tick window. Stop the competing backend, choose a free -ServerPort, or rerun with -SkipAppStart against the already running instance."
      Assert-Condition -Results $results -Name "Requested Server Port Available" -Condition $false -Detail "port=$ServerPort"
      throw $portConflictMessage
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
      "`$env:APP_CORS_ALLOWED_ORIGIN_PATTERNS='http://localhost:3005'",
      "`$env:APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS='http://localhost:3005'",
      "`$env:APP_ALERTING_ENABLED='false'",
      "`$env:APP_FEED_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_SHEDLOCK_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_WEBSOCKET_OBSERVABILITY_ENABLED='false'",
      "`$env:APP_WEBSOCKET_CANARY_ENABLED='false'",
      "`$env:APP_MARKET_WS_ENABLED='false'",
      "`$env:APP_STRATEGY_BOTS_SUMMARY_REFRESH_INTERVAL='PT5S'",
      "`$env:APP_STRATEGY_BOTS_SUMMARY_REFRESH_ACTIVITY_WINDOW='PT24H'",
      "`$env:APP_STRATEGY_BOTS_SUMMARY_REFRESH_BATCH_SIZE='50'",
      "`$env:APP_STRATEGY_BOTS_SUMMARY_REFRESH_STALE_THRESHOLD='PT20S'",
      "`$env:MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS='always'",
      "`$env:MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS='always'",
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

  $beforeChild = Get-LatestReportSnapshot -Directory $ReportsDir -Pattern "strategy-bot-summary-precompute-smoke-*.md"

  & (Join-Path $PSScriptRoot "run_strategy_bot_summary_precompute_smoke.ps1") `
    -BaseUrl $baseUrl `
    -OutputDir $ReportsDir `
    -TimeoutSec $TimeoutSec `
    -PollAttempts $PollAttempts `
    -PollIntervalSec $PollIntervalSec `
    -NoFail

  $afterChild = Get-LatestReportSnapshot -Directory $ReportsDir -Pattern "strategy-bot-summary-precompute-smoke-*.md"
  $childReportPath = Resolve-NewReportPath -BeforeSnapshot $beforeChild -AfterSnapshot $afterChild
  if (-not [string]::IsNullOrWhiteSpace($childReportPath)) {
    $childReportContent = Get-ContentText -Path $childReportPath
    if ($childReportContent -match "- Status:\s+(?:\*\*)?(PASSED|FAILED)(?:\*\*)?") {
      $childSmokeStatus = $Matches[1]
    }
  }

  Assert-Condition -Results $results -Name "Child Smoke Report Created" -Condition (-not [string]::IsNullOrWhiteSpace($childReportPath)) -Detail "$(if ($childReportPath) { $childReportPath } else { '<none>' })"
  Assert-Condition -Results $results -Name "Child Smoke Passed" -Condition ($childSmokeStatus -eq "PASSED") -Detail "status=$childSmokeStatus"
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
$lines += "# Strategy Bot Summary Precompute Local Runtime Check"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $baseUrl"
$lines += "- Status: **$overallStatus**"
$lines += "- Child Smoke Status: **$childSmokeStatus**"
$lines += "- Child Smoke Report: $(if ($childReportPath) { $childReportPath } else { '<none>' })"
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

Write-Host "Strategy bot summary precompute local runtime report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
  exit 1
}
