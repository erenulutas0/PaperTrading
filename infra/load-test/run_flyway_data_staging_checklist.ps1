param(
  [string]$PsqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe",
  [string]$DbHost = "localhost",
  [int]$DbPort = 5433,
  [string]$DbName = "finance_db",
  [string]$DbUser = "postgres",
  [string]$DbPassword = "password",
  [string]$ReportsDir = "infra/load-test/reports",
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-CheckResult {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  return [pscustomobject]@{
    Name   = $Name
    Passed = $Passed
    Detail = $Detail
  }
}

function Assert-Condition {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [bool]$Condition,
    [string]$Detail
  )

  $Results.Add((New-CheckResult -Name $Name -Passed $Condition -Detail $Detail)) | Out-Null
}

function Invoke-PsqlScalar {
  param(
    [string]$Sql
  )

  $tempFile = [System.IO.Path]::GetTempFileName()
  try {
    Set-Content -Path $tempFile -Value $Sql -Encoding UTF8
    $env:PGPASSWORD = $DbPassword
    $output = & $PsqlPath -X -A -t -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $tempFile 2>&1
    if ($LASTEXITCODE -ne 0) {
      throw "psql failed: $($output -join [Environment]::NewLine)"
    }
    return (($output -join "`n").Trim())
  } finally {
    Remove-Item -Path $tempFile -Force -ErrorAction SilentlyContinue
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'

if (-not (Test-Path $PsqlPath)) {
  throw "psql not found at $PsqlPath"
}

try {
  $v3Count = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM flyway_schema_history
WHERE version = '3'
  AND success = true;
"@
  Assert-Condition -Results $results -Name "Flyway V3 Present" -Condition ($v3Count -eq "1") -Detail "count=$v3Count"

  $v9Count = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM flyway_schema_history
WHERE version = '9'
  AND success = true;
"@
  Assert-Condition -Results $results -Name "Flyway V9 Present" -Condition ($v9Count -eq "1") -Detail "count=$v9Count"

  $v10Count = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM flyway_schema_history
WHERE version = '10'
  AND success = true;
"@
  Assert-Condition -Results $results -Name "Flyway V10 Present" -Condition ($v10Count -eq "1") -Detail "count=$v10Count"

  $v27Count = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM flyway_schema_history
WHERE version = '27'
  AND success = true;
"@
  Assert-Condition -Results $results -Name "Flyway V27 Present" -Condition ($v27Count -eq "1") -Detail "count=$v27Count"

  $commentConstraintCount = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM pg_constraint c
JOIN pg_class t ON t.oid = c.conrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'public'
  AND t.relname = 'interactions'
  AND c.conname = 'interactions_target_type_check'
  AND pg_get_constraintdef(c.oid) ILIKE '%COMMENT%';
"@
  Assert-Condition -Results $results -Name "Interaction Constraint Supports COMMENT" -Condition ($commentConstraintCount -eq "1") -Detail "count=$commentConstraintCount"

  $buyNullPnlCount = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM public.trade_activities
WHERE UPPER(type) LIKE 'BUY%'
  AND realized_pnl IS NULL;
"@
  Assert-Condition -Results $results -Name "BUY Rows Backfilled" -Condition ($buyNullPnlCount -eq "0") -Detail "null_buy_realized_pnl=$buyNullPnlCount"

  $zeroPortfolioCryptoCount = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM public.portfolio_items
WHERE quantity = 0
  AND UPPER(symbol) LIKE '%USDT';
"@
  Assert-Condition -Results $results -Name "No Zeroed Crypto Portfolio Items" -Condition ($zeroPortfolioCryptoCount -eq "0") -Detail "zeroed_crypto_portfolio_items=$zeroPortfolioCryptoCount"

  $zeroTradeCryptoCount = Invoke-PsqlScalar -Sql @"
SELECT COUNT(*)::text
FROM public.trade_activities
WHERE quantity = 0
  AND UPPER(symbol) LIKE '%USDT';
"@
  Assert-Condition -Results $results -Name "No Zeroed Crypto Trade Rows" -Condition ($zeroTradeCryptoCount -eq "0") -Detail "zeroed_crypto_trade_rows=$zeroTradeCryptoCount"

  $buySample = Invoke-PsqlScalar -Sql @"
SELECT COALESCE(string_agg(COALESCE(realized_pnl::text, 'NULL'), ', ' ORDER BY id), '')
FROM (
  SELECT realized_pnl, id
  FROM public.trade_activities
  WHERE UPPER(type) LIKE 'BUY%'
  ORDER BY id DESC
  LIMIT 5
) sample;
"@
  if (-not [string]::IsNullOrWhiteSpace($buySample)) {
    $notes.Add("Recent BUY realized_pnl samples: $buySample") | Out-Null
  }

  $zeroPortfolioSample = Invoke-PsqlScalar -Sql @"
SELECT COALESCE(string_agg(sample_row, ' | ' ORDER BY id DESC), '')
FROM (
  SELECT id,
         id::text || ':' || symbol || ':avg=' || COALESCE(average_price::text, 'NULL') AS sample_row
  FROM public.portfolio_items
  WHERE quantity = 0
    AND UPPER(symbol) LIKE '%USDT'
  ORDER BY id DESC
  LIMIT 5
) sample;
"@
  if (-not [string]::IsNullOrWhiteSpace($zeroPortfolioSample)) {
    $notes.Add("Recent zeroed crypto portfolio_items samples (USDT heuristic): $zeroPortfolioSample") | Out-Null
  }

  $zeroTradeSample = Invoke-PsqlScalar -Sql @"
SELECT COALESCE(string_agg(sample_row, ' | ' ORDER BY id DESC), '')
FROM (
  SELECT id,
         id::text || ':' || symbol || ':' || type || ':price=' || COALESCE(price::text, 'NULL') AS sample_row
  FROM public.trade_activities
  WHERE quantity = 0
    AND UPPER(symbol) LIKE '%USDT'
  ORDER BY id DESC
  LIMIT 5
) sample;
"@
  if (-not [string]::IsNullOrWhiteSpace($zeroTradeSample)) {
    $notes.Add("Recent zeroed crypto trade_activities samples (USDT heuristic): $zeroTradeSample") | Out-Null
  }
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$overallStatus = if ($failedResults.Count -eq 0 -and $results.Count -gt 0) { "PASSED" } else { "FAILED" }

New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
$reportPath = Join-Path $ReportsDir "flyway-data-staging-checklist-$timestamp.md"

$lines = @()
$lines += "# Flyway Data Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- DB Host: $DbHost"
$lines += "- DB Port: $DbPort"
$lines += "- DB Name: $DbName"
$lines += "- Status: **$overallStatus**"
$lines += ""
$lines += "Coverage:"
$lines += "- successful Flyway history rows for V3, V9, V10, V27"
$lines += '- `interactions_target_type_check` constraint includes `COMMENT`'
$lines += '- BUY trade history no longer has `NULL` `realized_pnl` after V9 backfill'
$lines += '- no legacy `quantity = 0` crypto rows remain in `portfolio_items` / `trade_activities` under the `USDT` symbol heuristic after V27 precision widening'
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($result.Detail.Replace('|', '/')) |"
}
if ($notes.Count -gt 0) {
  $lines += ""
  $lines += "## Notes"
  foreach ($note in $notes) {
    $lines += "- $($note.Replace('|', '/'))"
  }
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Flyway data staging checklist report created: $reportPath"
Write-Host "Status: $overallStatus | FailedChecks: $($failedResults.Count)"

if ($overallStatus -ne "PASSED" -and -not $NoFail) {
  exit 1
}
