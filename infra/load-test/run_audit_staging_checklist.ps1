param(
  [string]$BaseUrl,
  [switch]$SkipContractSmoke,
  [switch]$SkipWriteCapture,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the audit staging checklist."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$suiteScript = Join-Path $scriptDir "run_audit_validation_suite.ps1"

if (-not (Test-Path $suiteScript)) {
  throw "Audit validation suite script not found: $suiteScript"
}

$args = @(
  "-BaseUrl", $BaseUrl
)

if ($SkipContractSmoke) { $args += "-SkipContractSmoke" }
if ($SkipWriteCapture) { $args += "-SkipWriteCapture" }
if ($NoFail) { $args += "-NoFail" }

Write-Host "Running audit staging checklist..."
Write-Host "Base URL: $BaseUrl"
Write-Host "Contract smoke: $(if ($SkipContractSmoke) { 'skipped' } else { 'enabled' })"
Write-Host "Write capture: $(if ($SkipWriteCapture) { 'skipped' } else { 'enabled' })"

& powershell -ExecutionPolicy Bypass -File $suiteScript @args
