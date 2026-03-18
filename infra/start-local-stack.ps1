$ErrorActionPreference = "Stop"

$rootDir = Split-Path $PSScriptRoot -Parent
$infraScript = Join-Path $PSScriptRoot "start-local-infra.ps1"
$backendScript = Join-Path $PSScriptRoot "start-local-backend.ps1"
$frontendScript = Join-Path $PSScriptRoot "start-local-frontend.ps1"

Write-Host "Starting local infra..."
& $infraScript

Write-Host "Starting backend in a new PowerShell window..."
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $backendScript
)

Write-Host "Starting frontend in a new PowerShell window..."
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $frontendScript
)

Write-Host ""
Write-Host "Stack launch triggered."
Write-Host "Wait 10-20 seconds, then run:"
Write-Host "   powershell -ExecutionPolicy Bypass -File .\\infra\\check-local-stack.ps1"
Write-Host ""
Write-Host "Frontend target: http://localhost:3005"
Write-Host "Backend target:  http://localhost:8080"
