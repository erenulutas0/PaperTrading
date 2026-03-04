# 0. Create Portfolio
$createBody = @{ name = "My Crypto Fund"; ownerId = "user-123" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/portfolios" -Method Post -Body $createBody -ContentType "application/json"

# 1. List Portfolio to get ID
$p = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/portfolios?ownerId=user-123" -Method Get
$portfolioId = $p[0].id
Write-Output "Portfolio ID: $portfolioId"
Write-Output "Initial Balance: $($p[0].balance)"

# 2. Buy 0.01 BTCUSDT
$body = @{
    portfolioId = $portfolioId
    symbol = "BTCUSDT"
    quantity = 0.01
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trade/buy" -Method Post -Body $body -ContentType "application/json"

# 3. Check Result
$p_after = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/portfolios/$portfolioId" -Method Get
Write-Output "Final Balance: $($p_after.balance)"
Write-Output "Items:"
$p_after.items
