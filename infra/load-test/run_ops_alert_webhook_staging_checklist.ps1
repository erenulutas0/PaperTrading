param(
  [string]$BaseUrl,
  [string]$Component = "ops-alert-validation",
  [string]$Severity = "warning",
  [string]$Message = "Ops webhook staging checklist",
  [int]$PollAttempts = 8,
  [int]$PollIntervalSec = 2,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the ops alert webhook staging checklist."
}

function Get-MetricValue {
  param(
    [string]$MetricUrl
  )

  try {
    $response = Invoke-RestMethod -Uri $MetricUrl -Method Get -TimeoutSec 10
    $measurements = @($response.measurements)
    if ($measurements.Count -eq 0) {
      return 0.0
    }
    return [double](($measurements | Measure-Object -Property value -Sum).Sum)
  } catch {
    if ($_.Exception.Response) {
      $statusCode = [int]$_.Exception.Response.StatusCode
      if ($statusCode -eq 404) {
        return 0.0
      }
    }
    throw "Metric request failed for $MetricUrl : $($_.Exception.Message)"
  }
}

function Build-MetricUrl {
  param(
    [string]$BaseUrl,
    [string]$Component,
    [string]$Severity,
    [string]$Channel,
    [string]$Result
  )

  $builder = [System.UriBuilder]::new("$BaseUrl/actuator/metrics/app.ops.alerts.total")
  $queryParts = @(
    "tag=component:$Component",
    "tag=severity:$Severity",
    "tag=channel:$Channel",
    "tag=result:$Result"
  )
  $builder.Query = ($queryParts | ForEach-Object { [System.Uri]::EscapeDataString($_) }) -join "&"
  return $builder.Uri.AbsoluteUri
}

$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Force -Path $reportsDir
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $reportsDir "ops-alert-webhook-staging-checklist-$timestamp.md"
$status = "FAILED"
$notes = New-Object 'System.Collections.Generic.List[string]'
$childData = @{}

$severityTag = $Severity.Trim().ToLowerInvariant()
$alertKey = "ops-webhook-check-" + ([guid]::NewGuid().ToString("N"))
$statusUrl = "$BaseUrl/actuator/opsalerts"

$logSentUrl = Build-MetricUrl -BaseUrl $BaseUrl -Component $Component -Severity $severityTag -Channel "log" -Result "sent"
$webhookSentUrl = Build-MetricUrl -BaseUrl $BaseUrl -Component $Component -Severity $severityTag -Channel "webhook" -Result "sent"
$webhookFailedUrl = Build-MetricUrl -BaseUrl $BaseUrl -Component $Component -Severity $severityTag -Channel "webhook" -Result "failed"
$webhookSuppressedUrl = Build-MetricUrl -BaseUrl $BaseUrl -Component $Component -Severity $severityTag -Channel "webhook" -Result "suppressed"

try {
  $statusResponse = Invoke-RestMethod -Uri $statusUrl -Method Get -TimeoutSec 10
  if (-not $statusResponse.enabled) {
    throw "Ops alerting is disabled at runtime."
  }
  if (-not $statusResponse.webhookConfigured) {
    throw "Ops alert webhook is not configured at runtime."
  }

  $baseline = [ordered]@{
    logSent           = Get-MetricValue -MetricUrl $logSentUrl
    webhookSent       = Get-MetricValue -MetricUrl $webhookSentUrl
    webhookFailed     = Get-MetricValue -MetricUrl $webhookFailedUrl
    webhookSuppressed = Get-MetricValue -MetricUrl $webhookSuppressedUrl
  }

  $triggerBody = @{
    component = $Component
    severity  = $Severity
    alertKey  = $alertKey
    message   = $Message
  } | ConvertTo-Json
  $triggerResponse = Invoke-RestMethod -Uri $statusUrl -Method Post -TimeoutSec 10 -ContentType "application/json" -Body $triggerBody

  if (-not $triggerResponse.accepted) {
    $errorCode = if ($triggerResponse.error) { [string]$triggerResponse.error } else { "unknown" }
    throw "Ops alert trigger was not accepted: $errorCode"
  }

  $post = $null
  for ($attempt = 1; $attempt -le $PollAttempts; $attempt++) {
    $current = [ordered]@{
      logSent           = Get-MetricValue -MetricUrl $logSentUrl
      webhookSent       = Get-MetricValue -MetricUrl $webhookSentUrl
      webhookFailed     = Get-MetricValue -MetricUrl $webhookFailedUrl
      webhookSuppressed = Get-MetricValue -MetricUrl $webhookSuppressedUrl
    }
    $deltas = [ordered]@{
      logSent           = $current.logSent - $baseline.logSent
      webhookSent       = $current.webhookSent - $baseline.webhookSent
      webhookFailed     = $current.webhookFailed - $baseline.webhookFailed
      webhookSuppressed = $current.webhookSuppressed - $baseline.webhookSuppressed
    }

    $post = [ordered]@{
      attempt = $attempt
      counts  = $current
      deltas  = $deltas
    }

    if ($deltas.webhookSent -ge 1 -and $deltas.logSent -ge 1) {
      $status = "PASSED"
      break
    }

    if ($attempt -lt $PollAttempts) {
      Start-Sleep -Seconds $PollIntervalSec
    }
  }

  if ($null -eq $post) {
    throw "Unable to collect post-trigger metric snapshot."
  }

  $childData = [ordered]@{
    runtime = [ordered]@{
      enabled           = [bool]$statusResponse.enabled
      webhookConfigured = [bool]$statusResponse.webhookConfigured
      cooldownSeconds   = $statusResponse.cooldownSeconds
    }
    trigger = [ordered]@{
      component = $Component
      severity  = $Severity.ToUpperInvariant()
      alertKey  = $alertKey
      message   = $Message
      accepted  = [bool]$triggerResponse.accepted
    }
    baseline = $baseline
    post     = $post
  }

  if ($status -ne "PASSED") {
    if ($post.deltas.webhookFailed -ge 1) {
      $notes.Add("Webhook metric moved to failed after the validation alert.")
    } elseif ($post.deltas.webhookSuppressed -ge 1) {
      $notes.Add("Webhook metric moved to suppressed; check cooldown/key reuse behavior.")
    } else {
      $notes.Add("Webhook sent metric did not increase within the polling window.")
    }
  }
} catch {
  $notes.Add($_.Exception.Message)
}

$notesBlock = if ($notes.Count -eq 0) { "- none" } else { ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine }
$baselineLog = if ($childData.baseline) { [string]$childData.baseline.logSent } else { "n/a" }
$baselineWebhookSent = if ($childData.baseline) { [string]$childData.baseline.webhookSent } else { "n/a" }
$baselineWebhookFailed = if ($childData.baseline) { [string]$childData.baseline.webhookFailed } else { "n/a" }
$baselineWebhookSuppressed = if ($childData.baseline) { [string]$childData.baseline.webhookSuppressed } else { "n/a" }
$postAttempt = if ($childData.post) { [string]$childData.post.attempt } else { "n/a" }
$postLogDelta = if ($childData.post) { [string]$childData.post.deltas.logSent } else { "n/a" }
$postWebhookSentDelta = if ($childData.post) { [string]$childData.post.deltas.webhookSent } else { "n/a" }
$postWebhookFailedDelta = if ($childData.post) { [string]$childData.post.deltas.webhookFailed } else { "n/a" }
$postWebhookSuppressedDelta = if ($childData.post) { [string]$childData.post.deltas.webhookSuppressed } else { "n/a" }

$lines = @(
  "# Ops Alert Webhook Staging Checklist",
  "",
  "- Timestamp: $(Get-Date -Format s)",
  "- Base URL: $BaseUrl",
  "- Status: **$status**",
  "- Component: $Component",
  "- Severity: $($Severity.ToUpperInvariant())",
  "- Poll attempts: $PollAttempts",
  "- Poll interval seconds: $PollIntervalSec",
  "",
  "## Runtime",
  "- Alerting enabled: $(if ($childData.runtime) { $childData.runtime.enabled } else { 'n/a' })",
  "- Webhook configured: $(if ($childData.runtime) { $childData.runtime.webhookConfigured } else { 'n/a' })",
  "- Cooldown seconds: $(if ($childData.runtime) { $childData.runtime.cooldownSeconds } else { 'n/a' })",
  "",
  "## Trigger",
  "- Alert key: $alertKey",
  "- Message: $Message",
  "",
  "## Baseline Metrics",
  "- Log sent: $baselineLog",
  "- Webhook sent: $baselineWebhookSent",
  "- Webhook failed: $baselineWebhookFailed",
  "- Webhook suppressed: $baselineWebhookSuppressed",
  "",
  "## Post-Trigger Delta",
  "- Sample attempt: $postAttempt",
  "- Log sent delta: $postLogDelta",
  "- Webhook sent delta: $postWebhookSentDelta",
  "- Webhook failed delta: $postWebhookFailedDelta",
  "- Webhook suppressed delta: $postWebhookSuppressedDelta",
  "",
  "## Notes",
  $notesBlock
)

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Ops alert webhook staging checklist report created: $reportPath"
Write-Host "Status: $status"

if ($status -ne "PASSED" -and -not $NoFail) {
  exit 1
}
