$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/market/prices" -Method Get
Write-Output "Crypto Prices:"
$response
