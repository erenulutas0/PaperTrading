$ErrorActionPreference = "Stop"

$rootDir = Split-Path $PSScriptRoot -Parent
$infraScript = Join-Path $PSScriptRoot "start-local-infra.ps1"

Write-Host "Starting local infra..."
& $infraScript

Write-Host ""
Write-Host "Next steps:"
Write-Host "1. Start backend in a new terminal:"
Write-Host "   powershell -ExecutionPolicy Bypass -File .\\infra\\start-local-backend.ps1"
Write-Host "2. Start frontend in another terminal:"
Write-Host "   powershell -ExecutionPolicy Bypass -File .\\infra\\start-local-frontend.ps1"
Write-Host "3. Check stack health:"
Write-Host "   powershell -ExecutionPolicy Bypass -File .\\infra\\check-local-stack.ps1"
Write-Host ""
Write-Host "Frontend target: http://localhost:3005"
Write-Host "Backend target:  http://localhost:8080"
