param(
  [string]$BaseUrl,
  [string]$ForwardedFor = "198.51.100.42",
  [int]$CommentAttempts = 18,
  [int]$ReplyAttempts = 18,
  [int]$FollowAttempts = 28,
  [int]$RefreshAttempts = 28,
  [int]$ReadProbeCount = 5,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "BaseUrl is required for the rate-limit staging checklist."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$smokeScript = Join-Path $scriptDir "run_rate_limit_profile_smoke.ps1"

if (-not (Test-Path $smokeScript)) {
  throw "Rate-limit smoke script not found: $smokeScript"
}

$args = @(
  "-BaseUrl", $BaseUrl,
  "-ForwardedFor", $ForwardedFor,
  "-CommentAttempts", "$CommentAttempts",
  "-ReplyAttempts", "$ReplyAttempts",
  "-FollowAttempts", "$FollowAttempts",
  "-RefreshAttempts", "$RefreshAttempts",
  "-ReadProbeCount", "$ReadProbeCount"
)

if ($NoFail) {
  $args += "-NoFail"
}

Write-Host "Running rate-limit staging checklist..."
Write-Host "Base URL: $BaseUrl"
Write-Host "Forwarded identity: $ForwardedFor"
Write-Host "Burst profile: comments=$CommentAttempts replies=$ReplyAttempts follows=$FollowAttempts refreshes=$RefreshAttempts readProbes=$ReadProbeCount"

& powershell -ExecutionPolicy Bypass -File $smokeScript @args
