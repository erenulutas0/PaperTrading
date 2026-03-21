$ErrorActionPreference = "Stop"

$backendDir = Join-Path $PSScriptRoot "..\\services\\core-api"

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/finance_db"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "password"
$env:REDIS_URL = "redis://localhost:6379"
$env:APP_CORS_ALLOWED_ORIGIN_PATTERNS = "http://localhost:3005"
$env:APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS = "http://localhost:3005"
$env:APP_ALERTING_ENABLED = "false"
$env:APP_FEED_OBSERVABILITY_ENABLED = "false"
$env:APP_SHEDLOCK_OBSERVABILITY_ENABLED = "false"
$env:APP_WEBSOCKET_OBSERVABILITY_ENABLED = "false"
$env:APP_WEBSOCKET_CANARY_ENABLED = "false"
$env:JWT_SECRET = "local-dev-secret-change-me-at-least-32-bytes"
$env:PORT = "8080"

Set-Location $backendDir

if (-not (Test-Path ".\\mvnw.cmd")) {
    throw "Backend Maven wrapper not found at $backendDir"
}

.\\mvnw.cmd spring-boot:run
