$headers = @{ "Content-Type" = "application/json" }
$body = @{
    name = "My First Portfolio"
    ownerId = "user-123"
} | ConvertTo-Json

Write-Host "Creating Portfolio..."
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/portfolios" -Method Post -Headers $headers -Body $body
$response

Write-Host "Listing Portfolios..."
$list = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/portfolios?ownerId=user-123" -Method Get
$list
