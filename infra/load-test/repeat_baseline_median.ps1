param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$SeedEvents = 200,
    [int]$Concurrency = 8,
    [int]$RequestsPerWorker = 100,
    [string]$LoadProfile = "global",
    [int]$FanoutFollowers = 1000,
    [int]$GlobalReadWeight = 70,
    [int]$PersonalizedReadWeight = 20,
    [int]$UserReadWeight = 10,
    [int]$WriteWeight = 0,
    [int]$Rounds = 3,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5433,
    [string]$DbName = "finance_db",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "password",
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$OutputDir = "infra/load-test/reports"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Rounds -lt 1) {
    throw "Rounds must be >= 1"
}

function Parse-Double {
    param([string]$Raw)
    if ([string]::IsNullOrWhiteSpace($Raw)) { return 0.0 }
    $normalized = $Raw.Trim().Replace("%", "").Replace(",", ".")
    $value = 0.0
    if ([double]::TryParse(
            $normalized,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$value
        )) {
        return $value
    }
    return 0.0
}

function Get-MetricFromTable {
    param(
        [string[]]$Lines,
        [string]$Metric
    )

    $pattern = "^\|\s*$([regex]::Escape($Metric))\s*\|\s*([^|]+)\s*\|$"
    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            return Parse-Double -Raw $Matches[1]
        }
    }
    return 0.0
}

function Get-Median {
    param([double[]]$Values)

    if ($Values.Count -eq 0) { return 0.0 }
    $sorted = @($Values | Sort-Object)
    $mid = [int][math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return [double]$sorted[$mid]
    }
    return ([double]$sorted[$mid - 1] + [double]$sorted[$mid]) / 2.0
}

function Resolve-ReportRoot {
    param([string]$Relative)
    return Resolve-Path "." | ForEach-Object { Join-Path $_.Path $Relative }
}

$reportRoot = Resolve-ReportRoot -Relative $OutputDir
New-Item -Path $reportRoot -ItemType Directory -Force | Out-Null

$baselineScript = Resolve-Path "." | ForEach-Object { Join-Path $_.Path "infra/load-test/lightweight_baseline.ps1" }
if (-not (Test-Path $baselineScript)) {
    throw "Baseline script not found: $baselineScript"
}

$roundResults = New-Object System.Collections.Generic.List[object]

for ($round = 1; $round -le $Rounds; $round++) {
    Write-Host "Running baseline round $round/$Rounds..."
    & $baselineScript `
        -BaseUrl $BaseUrl `
        -SeedEvents $SeedEvents `
        -Concurrency $Concurrency `
        -RequestsPerWorker $RequestsPerWorker `
        -LoadProfile $LoadProfile `
        -FanoutFollowers $FanoutFollowers `
        -GlobalReadWeight $GlobalReadWeight `
        -PersonalizedReadWeight $PersonalizedReadWeight `
        -UserReadWeight $UserReadWeight `
        -WriteWeight $WriteWeight `
        -DbHost $DbHost `
        -DbPort $DbPort `
        -DbName $DbName `
        -DbUser $DbUser `
        -DbPassword $DbPassword `
        -RedisHost $RedisHost `
        -RedisPort $RedisPort `
        -OutputDir $OutputDir | Out-Null

    $latest = Get-ChildItem -Path $reportRoot -Filter "load-baseline-*.md" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) {
        throw "No baseline report found after round $round"
    }

    $lines = Get-Content $latest.FullName
    $roundResults.Add([pscustomobject]@{
            round           = $round
            report          = $latest.FullName
            success_rate    = Get-MetricFromTable -Lines $lines -Metric "success_rate"
            avg_latency_ms  = Get-MetricFromTable -Lines $lines -Metric "avg_latency_ms"
            p50_latency_ms  = Get-MetricFromTable -Lines $lines -Metric "p50_latency_ms"
            p95_latency_ms  = Get-MetricFromTable -Lines $lines -Metric "p95_latency_ms"
            p99_latency_ms  = Get-MetricFromTable -Lines $lines -Metric "p99_latency_ms"
            max_latency_ms  = Get-MetricFromTable -Lines $lines -Metric "max_latency_ms"
        })
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $reportRoot "load-baseline-median-$timestamp.md"

$p95Median = Get-Median -Values @($roundResults | ForEach-Object { [double]$_.p95_latency_ms })
$p99Median = Get-Median -Values @($roundResults | ForEach-Object { [double]$_.p99_latency_ms })
$avgMedian = Get-Median -Values @($roundResults | ForEach-Object { [double]$_.avg_latency_ms })
$successMedian = Get-Median -Values @($roundResults | ForEach-Object { [double]$_.success_rate })

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Baseline Median Summary")
$lines.Add("")
$lines.Add("Generated at: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))")
$lines.Add("")
$lines.Add("## Scenario")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Seed events: $SeedEvents")
$lines.Add("- Concurrency: $Concurrency")
$lines.Add("- Requests per worker: $RequestsPerWorker")
$lines.Add("- Load profile: $LoadProfile")
$lines.Add("- Fanout followers: $(if ($LoadProfile -eq 'fanout') { $FanoutFollowers } else { 0 })")
$lines.Add("- Weights (global/personalized/user/write): $GlobalReadWeight/$PersonalizedReadWeight/$UserReadWeight/$WriteWeight")
$lines.Add("- Rounds: $Rounds")
$lines.Add("")
$lines.Add("## Per-Round Results")
$lines.Add("| round | success_rate | avg_latency_ms | p50_latency_ms | p95_latency_ms | p99_latency_ms | max_latency_ms | report |")
$lines.Add("|---:|---:|---:|---:|---:|---:|---:|---|")
foreach ($item in $roundResults) {
    $lines.Add("| $($item.round) | $([math]::Round($item.success_rate, 2)) | $([math]::Round($item.avg_latency_ms, 2)) | $([math]::Round($item.p50_latency_ms, 2)) | $([math]::Round($item.p95_latency_ms, 2)) | $([math]::Round($item.p99_latency_ms, 2)) | $([math]::Round($item.max_latency_ms, 2)) | $($item.report) |")
}
$lines.Add("")
$lines.Add("## Median Readout")
$lines.Add("| metric | median |")
$lines.Add("|---|---:|")
$lines.Add("| success_rate | $([math]::Round($successMedian, 2)) |")
$lines.Add("| avg_latency_ms | $([math]::Round($avgMedian, 2)) |")
$lines.Add("| p95_latency_ms | $([math]::Round($p95Median, 2)) |")
$lines.Add("| p99_latency_ms | $([math]::Round($p99Median, 2)) |")
$lines.Add("")

$lines | Set-Content -Path $summaryPath

Write-Host "Median summary report created:"
Write-Host $summaryPath
