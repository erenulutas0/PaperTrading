param(
  [string]$BaseUrl,
  [string]$StrictSmokeBaseUrl = "",
  [double]$MaxLegacyAccepted = 0,
  [int]$BaselineSeedEvents = 80,
  [int]$BaselineConcurrency = 6,
  [int]$BaselineRequestsPerWorker = 40,
  [string]$RelayBrokerRestartCommand = "",
  [switch]$SkipLegacyUsage,
  [switch]$SkipStrictSmoke,
  [switch]$SkipAuthAttack,
  [switch]$SkipBaseline,
  [switch]$SkipRelay,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the staging checklist."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$suiteScript = Join-Path $scriptDir "run_auth_strict_mode_validation_suite.ps1"

if (-not (Test-Path $suiteScript)) {
  throw "Validation suite script not found: $suiteScript"
}

$args = @(
  "-BaseUrl", $BaseUrl,
  "-MaxLegacyAccepted", "$MaxLegacyAccepted",
  "-BaselineSeedEvents", "$BaselineSeedEvents",
  "-BaselineConcurrency", "$BaselineConcurrency",
  "-BaselineRequestsPerWorker", "$BaselineRequestsPerWorker"
)

if (-not [string]::IsNullOrWhiteSpace($StrictSmokeBaseUrl)) {
  $args += @("-StrictSmokeBaseUrl", $StrictSmokeBaseUrl)
} elseif (-not $SkipStrictSmoke) {
  $args += "-SkipStrictSmoke"
}

if (-not [string]::IsNullOrWhiteSpace($RelayBrokerRestartCommand)) {
  $args += @("-RelayBrokerRestartCommand", $RelayBrokerRestartCommand)
}

if ($SkipLegacyUsage) { $args += "-SkipLegacyUsage" }
if ($SkipStrictSmoke) { $args += "-SkipStrictSmoke" }
if ($SkipAuthAttack) { $args += "-SkipAuthAttack" }
if ($SkipBaseline) { $args += "-SkipBaseline" }
if ($SkipRelay) { $args += "-SkipRelay" }
if ($NoFail) { $args += "-NoFail" }

Write-Host "Running strict-mode staging checklist..."
Write-Host "Base URL: $BaseUrl"
Write-Host "Strict smoke base URL: $(if ([string]::IsNullOrWhiteSpace($StrictSmokeBaseUrl)) { 'skipped-by-default' } else { $StrictSmokeBaseUrl })"
Write-Host "Baseline profile: seed=$BaselineSeedEvents concurrency=$BaselineConcurrency requestsPerWorker=$BaselineRequestsPerWorker"

& powershell -ExecutionPolicy Bypass -File $suiteScript @args
