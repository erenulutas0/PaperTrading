$ErrorActionPreference = "Stop"

function Test-TcpPort {
    param(
        [string]$TargetHost,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(1000, $false)
        if (-not $ok) {
            $client.Close()
            return $false
        }
        $client.EndConnect($async)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Get-HttpStatus {
    param(
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return [string]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [string][int]$_.Exception.Response.StatusCode
        }
        return "DOWN"
    }
}

$postgresUp = Test-TcpPort -TargetHost "localhost" -Port 5433
$redisUp = Test-TcpPort -TargetHost "localhost" -Port 6379
$backendHealth = Get-HttpStatus -Url "http://localhost:8080/actuator/health"
$frontendHealth = Get-HttpStatus -Url "http://localhost:3005"

Write-Host "Local stack status"
Write-Host "------------------"
Write-Host ("Postgres : {0}" -f ($(if ($postgresUp) { "UP" } else { "DOWN" })))
Write-Host ("Redis    : {0}" -f ($(if ($redisUp) { "UP" } else { "DOWN" })))
Write-Host ("Backend  : {0}" -f $backendHealth)
Write-Host ("Frontend : {0}" -f $frontendHealth)
