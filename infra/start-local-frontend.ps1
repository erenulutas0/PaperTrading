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

$nextCli = ".\\node_modules\\next\\dist\\bin\\next"
if (-not (Test-Path $nextCli)) {
    throw "Next.js CLI not found. Run npm install inside apps/web first."
}

node $nextCli dev -p 3005
