$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not installed or not on PATH."
}

docker compose -f $composeFile up -d postgres redis

Write-Host "Local infra is up."
Write-Host "Postgres: localhost:5433"
Write-Host "Redis:    localhost:6379"
