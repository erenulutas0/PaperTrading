param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 0,
  [int]$WebhookPort = 0,
  [int]$StartupTimeoutSec = 240,
  [int]$WebhookTimeoutSec = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ($listener.LocalEndpoint).Port
  $listener.Stop()
  return $port
}

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 4
      if ($response.status -eq "UP") {
        return $true
      }
    } catch {
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Force -Path $reportsDir
$orchestratorReportPath = Join-Path $reportsDir "ops-alert-webhook-skipapp-flow-$timestamp.md"
$appOutLog = Join-Path $reportsDir "ops-alert-skipapp-coreapi-$timestamp.log"
$appErrLog = Join-Path $reportsDir "ops-alert-skipapp-coreapi-$timestamp.err.log"

if ($ServerPort -le 0) {
  $ServerPort = Get-FreeTcpPort
}
if ($WebhookPort -le 0) {
  $WebhookPort = Get-FreeTcpPort
}

$coreApiPath = (Resolve-Path $CoreApiDir).Path
$appCommand = @(
  "`$env:SERVER_PORT='$ServerPort'",
  "`$env:APP_ALERTING_ENABLED='true'",
  "`$env:APP_ALERTING_COOLDOWN='PT1S'",
  "`$env:APP_ALERTING_WEBHOOK_URL='http://127.0.0.1:$WebhookPort/ops'",
  "`$env:APP_FEED_OBSERVABILITY_MIN_SAMPLES='1'",
  "`$env:APP_FEED_OBSERVABILITY_WARNING_P95_MS='0'",
  "`$env:APP_FEED_OBSERVABILITY_WARNING_P99_MS='0'",
  "`$env:APP_FEED_OBSERVABILITY_CRITICAL_P99_MS='0'",
  "`$env:APP_MARKET_WS_ENABLED='false'",
  "`$env:APP_SCHEDULING_ENABLED='false'",
  "`$env:MAVEN_OPTS='-Dmaven.repo.local=$coreApiPath\\.m2repo'",
  "mvn -q spring-boot:run"
) -join "; "

$appProcess = $null
$validationExit = 1
$validationReportPath = ""
$notes = @()

try {
  $appProcess = Start-Process -FilePath "pwsh" `
    -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
    -WorkingDirectory $coreApiPath `
    -RedirectStandardOutput $appOutLog `
    -RedirectStandardError $appErrLog `
    -PassThru

  if (-not (Wait-HttpHealthy -Url "http://localhost:$ServerPort/actuator/health" -TimeoutSec $StartupTimeoutSec)) {
    throw "Core API did not become healthy on port $ServerPort within $StartupTimeoutSec seconds."
  }

  $validationOutput = & "pwsh" -NoLogo -NoProfile -File "./infra/load-test/validate_ops_alert_webhook.ps1" `
    -SkipAppStart `
    -ServerPort $ServerPort `
    -WebhookPort $WebhookPort `
    -WebhookTimeoutSec $WebhookTimeoutSec 2>&1
  $validationExit = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { if ($?) { 0 } else { 1 } }

  foreach ($line in $validationOutput) {
    if ($line -match "Validation report created:\s+(.+)$") {
      $validationReportPath = $Matches[1].Trim()
      break
    }
  }

  if ($validationExit -ne 0) {
    $notes += "Inner skip-app validation returned non-zero exit code: $validationExit"
  }
} catch {
  $notes += $_.Exception.Message
} finally {
  if ($appProcess) {
    try {
      if (-not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
      }
    } catch {
    }
  }
}

$status = if ($validationExit -eq 0) { "PASSED" } else { "FAILED" }
$notesBlock = if ($notes.Count -eq 0) { "- none" } else { ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine }

$reportLines = @(
  "# Ops Alert Webhook Skip-App Flow Validation",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Core API dir: $coreApiPath",
  "- Server port: $ServerPort",
  "- Webhook port: $WebhookPort",
  "- Startup timeout seconds: $StartupTimeoutSec",
  "",
  "## Result",
  "- Status: **$status**",
  "- Inner validation exit code: $validationExit",
  "- Inner validation report: $validationReportPath",
  "- Core API stdout log: $appOutLog",
  "- Core API stderr log: $appErrLog",
  "",
  "## Notes",
  $notesBlock
)
$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $orchestratorReportPath -Value $report -Encoding UTF8

Write-Output "Orchestrator report created: $orchestratorReportPath"
Write-Output "Skip-app flow status: $status"

if ($validationExit -ne 0) {
  exit 1
}
