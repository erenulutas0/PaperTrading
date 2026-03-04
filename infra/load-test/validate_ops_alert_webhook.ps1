param(
  [string]$CoreApiDir = "services/core-api",
  [int]$ServerPort = 18080,
  [int]$WebhookPort = 19099,
  [int]$StartupTimeoutSec = 180,
  [int]$WebhookTimeoutSec = 60,
  [switch]$SkipAppStart,
  [switch]$PreserveAppAfterRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $null = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 5
      return $true
    } catch {
      Start-Sleep -Seconds 2
    }
  }
  return $false
}

function Invoke-IgnoreError {
  param([string]$Url)
  try {
    $null = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 10
  } catch {
    # feed path can fail on cold startup edge cases; we only need uri metrics to be observed
  }
}

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

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ($listener.LocalEndpoint).Port
  $listener.Stop()
  return $port
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Force -Path $reportsDir
$reportPath = Join-Path $reportsDir "ops-alert-webhook-validation-$timestamp.md"
$appLogPath = Join-Path $reportsDir "ops-alert-app-$timestamp.log"
$appErrLogPath = Join-Path $reportsDir "ops-alert-app-$timestamp.err.log"
$payloadPath = Join-Path $reportsDir "ops-alert-webhook-payload-$timestamp.json"
$listenerLogPath = Join-Path $reportsDir "ops-alert-webhook-listener-$timestamp.log"
$listenerErrLogPath = Join-Path $reportsDir "ops-alert-webhook-listener-$timestamp.err.log"
$listenerScriptPath = Join-Path $reportsDir "ops-alert-webhook-listener-$timestamp.py"

$portAutoSwitchNote = ""
if (-not $SkipAppStart -and (Test-PortOpen -Port $ServerPort)) {
  $requestedPort = $ServerPort
  $ServerPort = Get-FreeTcpPort
  $portAutoSwitchNote = "Requested server port $requestedPort was in use; switched to $ServerPort."
}

$baseUrl = "http://localhost:$ServerPort"
$healthUrl = "$baseUrl/actuator/health"
$feedUrl = "$baseUrl/api/v1/feed/global?page=0&size=20"
$feedLatencyUrl = "$baseUrl/actuator/feedlatency"
$webhookUrl = "http://127.0.0.1:$WebhookPort/ops"

$listenerProcess = $null
$appProcess = $null
$appStartedByScript = $false
$validationStatus = "FAILED"
$notes = @()
$payloadContent = ""

if ($portAutoSwitchNote) {
  $notes += $portAutoSwitchNote
}

try {
  $listenerScript = @'
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

port = int(sys.argv[1])
output_path = sys.argv[2]

class Handler(BaseHTTPRequestHandler):
    def _read_request_body(self):
        transfer_encoding = (self.headers.get("Transfer-Encoding") or "").lower()
        if "chunked" in transfer_encoding:
            chunks = []
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                chunk_size = int(line.split(b";")[0], 16)
                if chunk_size == 0:
                    while True:
                        trailer = self.rfile.readline()
                        if trailer in (b"\r\n", b"\n", b""):
                            break
                    break
                chunks.append(self.rfile.read(chunk_size))
                self.rfile.read(2)
            return b"".join(chunks)

        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0:
            return b""
        return self.rfile.read(content_length)

    def do_POST(self):
        body = self._read_request_body().decode("utf-8", errors="replace")
        with open(output_path, "w", encoding="utf-8") as f:
            f.write(body)
        response_body = b'{"ok":true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(response_body)
        self.wfile.flush()
        time.sleep(0.2)
        self.server.should_exit = True

    def log_message(self, format, *args):
        return

httpd = HTTPServer(("127.0.0.1", port), Handler)
httpd.should_exit = False
while not httpd.should_exit:
    httpd.handle_request()
'@
  Set-Content -Path $listenerScriptPath -Value $listenerScript -Encoding UTF8

  $listenerProcess = Start-Process -FilePath "python" `
    -ArgumentList $listenerScriptPath, "$WebhookPort", $payloadPath `
    -RedirectStandardOutput $listenerLogPath `
    -RedirectStandardError $listenerErrLogPath `
    -PassThru

  if (-not $SkipAppStart) {
    $coreApiPath = Resolve-Path $CoreApiDir
    $appCommand = @(
      "`$env:SERVER_PORT='$ServerPort'",
      "`$env:APP_ALERTING_ENABLED='true'",
      "`$env:APP_ALERTING_COOLDOWN='PT1S'",
      "`$env:APP_ALERTING_WEBHOOK_URL='$webhookUrl'",
      "`$env:APP_FEED_OBSERVABILITY_MIN_SAMPLES='1'",
      "`$env:APP_FEED_OBSERVABILITY_WARNING_P95_MS='0'",
      "`$env:APP_FEED_OBSERVABILITY_WARNING_P99_MS='0'",
      "`$env:APP_FEED_OBSERVABILITY_CRITICAL_P99_MS='0'",
      "`$env:APP_MARKET_WS_ENABLED='false'",
      "`$env:APP_SCHEDULING_ENABLED='false'",
      "`$env:MAVEN_OPTS='-Dmaven.repo.local=$coreApiPath\\.m2repo'",
      "mvn -q spring-boot:run"
    ) -join "; "

    $appProcess = Start-Process -FilePath "pwsh" `
      -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
      -WorkingDirectory $coreApiPath `
      -RedirectStandardOutput $appLogPath `
      -RedirectStandardError $appErrLogPath `
      -PassThru
    $appStartedByScript = $true

    if (-not (Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec)) {
      throw "Core API did not become healthy at $healthUrl within $StartupTimeoutSec seconds."
    }
  } else {
    if (-not (Wait-HttpHealthy -Url $healthUrl -TimeoutSec 20)) {
      throw "Core API is not reachable at $healthUrl. Start app or remove -SkipAppStart."
    }
  }

  1..5 | ForEach-Object { Invoke-IgnoreError -Url $feedUrl }
  $null = Invoke-WebRequest -Uri $feedLatencyUrl -Method Get -TimeoutSec 10

  if (-not $listenerProcess.WaitForExit($WebhookTimeoutSec * 1000)) {
    throw "Webhook listener did not receive a payload within $WebhookTimeoutSec seconds."
  }
  if ($listenerProcess.ExitCode -ne 0) {
    throw "Webhook listener exited with code $($listenerProcess.ExitCode). Check $listenerErrLogPath"
  }

  if (-not (Test-Path $payloadPath)) {
    throw "Webhook payload file was not created."
  }

  $payloadContent = (Get-Content -Raw -Path $payloadPath).Trim()
  if (-not $payloadContent) {
    throw "Webhook payload file is empty."
  }

  $payload = $payloadContent | ConvertFrom-Json
  $component = [string]$payload.component
  $severity = [string]$payload.severity
  if (($component -ne "feed-latency") -and ($component -ne "shedlock")) {
    $notes += "Unexpected component value: $component"
  }
  if (-not $severity) {
    $notes += "Missing severity in payload"
  }

  $validationStatus = "PASSED"
} catch {
  $notes += $_.Exception.Message
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes += $_.InvocationInfo.PositionMessage
  }
  if ($_.ScriptStackTrace) {
    $notes += $_.ScriptStackTrace
  }
} finally {
  if ($listenerProcess) {
    try {
      if (-not $listenerProcess.HasExited) {
        Stop-Process -Id $listenerProcess.Id -Force
      }
    } catch {
    }
  }

  if ($appStartedByScript -and $appProcess) {
    try {
      if (-not $PreserveAppAfterRun -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
      }
    } catch {
    }
  }

  if (Test-Path $listenerScriptPath) {
    try {
      Remove-Item -Path $listenerScriptPath -Force
    } catch {
    }
  }
}

$notesBlock = if ($notes.Count -eq 0) { "- none" } else { ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine }
$payloadEscaped = if ($payloadContent) { $payloadContent } else { "{}" }
$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$startupMode = if ($SkipAppStart) { "reuse-existing-app" } else { "script-started-app" }
$appLogValue = if ($appStartedByScript) { "$appLogPath (stderr: $appErrLogPath)" } else { "n/a (app not started by script)" }
$appProcessValue = if ($appStartedByScript -and $appProcess) { [string]$appProcess.Id } else { "n/a" }
$appLifecycleValue = if ($appStartedByScript) { $(if ($PreserveAppAfterRun) { "preserved" } else { "stopped-on-exit" }) } else { "n/a" }

$reportLines = @(
  "# Ops Alert Webhook Validation",
  "",
  "Generated at: $generatedAt",
  "",
  "## Scenario",
  "- Base URL: $baseUrl",
  "- Startup mode: $startupMode",
  "- Alert webhook target: $webhookUrl",
  "- Forced feed thresholds:",
  "  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1",
  "  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0",
  "  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0",
  "  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0",
  "",
  "## Result",
  "- Validation: **$validationStatus**",
  "- App process id: $appProcessValue",
  "- App lifecycle: $appLifecycleValue",
  "- Payload file: $payloadPath",
  "- App log: $appLogValue",
  "",
  "## Notes",
  $notesBlock,
  "",
  "## Captured Payload",
  '```json',
  $payloadEscaped,
  '```'
)
$report = $reportLines -join [Environment]::NewLine

Set-Content -Path $reportPath -Value $report -Encoding UTF8
Write-Output "Validation report created: $reportPath"
Write-Output "Validation status: $validationStatus"

if ($validationStatus -ne "PASSED") {
  exit 1
}
