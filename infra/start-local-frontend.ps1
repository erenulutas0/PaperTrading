$ErrorActionPreference = "Stop"

$frontendDir = Join-Path $PSScriptRoot "..\\apps\\web"

$env:API_BASE_URL = "http://localhost:8080"
$env:NEXT_PUBLIC_API_BASE_URL = "http://localhost:8080"
$env:NEXT_PUBLIC_WS_BROKER_URL = "ws://localhost:8080"
$env:NEXT_PUBLIC_WS_HTTP_URL = "http://localhost:8080"

Set-Location $frontendDir

if (-not (Test-Path ".\\package.json")) {
    throw "Frontend package.json not found at $frontendDir"
}

npm run dev
