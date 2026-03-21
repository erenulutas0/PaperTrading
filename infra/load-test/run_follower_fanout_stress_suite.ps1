param(
    [string]$BaseUrl = "http://localhost:8080",
    [int[]]$FanoutStages = @(1000, 5000, 10000),
    [int]$SeedEvents = 200,
    [int]$Concurrency = 8,
    [int]$RequestsPerWorker = 120,
    [int]$Rounds = 3,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5433,
    [string]$DbName = "finance_db",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "password",
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$OutputDir = "infra/load-test/reports",
    [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($FanoutStages.Count -lt 1) {
    throw "FanoutStages must contain at least one follower count."
}
if ((@($FanoutStages | Where-Object { $_ -lt 1 })).Count -gt 0) {
    throw "All FanoutStages must be >= 1."
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

function Get-MetricFromMedianTable {
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

function Get-LatestReport {
    param(
        [string]$Directory,
        [string]$Pattern
    )

    $report = Get-ChildItem -Path $Directory -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $report) {
        return ""
    }
    return $report.FullName
}

function Get-NewReport {
    param(
        [string]$Directory,
        [string]$Pattern,
        [string]$BeforePath
    )

    $latest = Get-LatestReport -Directory $Directory -Pattern $Pattern
    if ([string]::IsNullOrWhiteSpace($latest)) {
        return ""
    }
    if ($latest -eq $BeforePath) {
        return ""
    }
    return $latest
}

function Get-StageResult {
    param(
        [int]$Followers,
        [string]$ReportPath
    )

    $lines = Get-Content -Path $ReportPath
    return [pscustomobject]@{
        followers      = $Followers
        successRate    = Get-MetricFromMedianTable -Lines $lines -Metric "success_rate"
        avgLatencyMs   = Get-MetricFromMedianTable -Lines $lines -Metric "avg_latency_ms"
        p95LatencyMs   = Get-MetricFromMedianTable -Lines $lines -Metric "p95_latency_ms"
        p99LatencyMs   = Get-MetricFromMedianTable -Lines $lines -Metric "p99_latency_ms"
        reportPath     = $ReportPath
    }
}

function Get-DeltaSummary {
    param(
        [double]$BaseValue,
        [double]$NextValue
    )

    $delta = $NextValue - $BaseValue
    $deltaPct = if ($BaseValue -eq 0) { 0.0 } else { ($delta / $BaseValue) * 100.0 }
    return [pscustomobject]@{
        Delta = $delta
        DeltaPct = $deltaPct
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repeatScript = Join-Path $scriptDir "repeat_baseline_median.ps1"
if (-not (Test-Path $repeatScript)) {
    throw "Missing dependency script: $repeatScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$stageResults = New-Object 'System.Collections.Generic.List[object]'
$stepResults = New-Object 'System.Collections.Generic.List[object]'

foreach ($stage in $FanoutStages) {
    Write-Host "Running fanout median suite for follower stage $stage..."
    $beforeReport = Get-LatestReport -Directory $resolvedOutputDir -Pattern "load-baseline-median-*.md"

    try {
        & powershell -ExecutionPolicy Bypass -File $repeatScript `
            -BaseUrl $BaseUrl `
            -SeedEvents $SeedEvents `
            -Concurrency $Concurrency `
            -RequestsPerWorker $RequestsPerWorker `
            -LoadProfile "fanout" `
            -FanoutFollowers $stage `
            -Rounds $Rounds `
            -DbHost $DbHost `
            -DbPort $DbPort `
            -DbName $DbName `
            -DbUser $DbUser `
            -DbPassword $DbPassword `
            -RedisHost $RedisHost `
            -RedisPort $RedisPort `
            -OutputDir $OutputDir | Out-Null

        $reportPath = Get-NewReport -Directory $resolvedOutputDir -Pattern "load-baseline-median-*.md" -BeforePath $beforeReport
        if ([string]::IsNullOrWhiteSpace($reportPath)) {
            throw "Median report not generated for follower stage $stage."
        }

        $stageResult = Get-StageResult -Followers $stage -ReportPath $reportPath
        $stageResults.Add($stageResult) | Out-Null
        $stepResults.Add([pscustomobject]@{
                Stage = $stage
                Status = "PASSED"
                Detail = "ok"
                ReportPath = $reportPath
            }) | Out-Null
    } catch {
        $stepResults.Add([pscustomobject]@{
                Stage = $stage
                Status = "FAILED"
                Detail = $_.Exception.Message
                ReportPath = ""
            }) | Out-Null

        if (-not $NoFail) {
            throw
        }
    }
}

$overallStatus = if ((@($stepResults | Where-Object { $_.Status -ne "PASSED" })).Count -eq 0) { "PASSED" } else { "FAILED" }
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "fanout-stress-suite-$timestamp.md"

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Follower Fanout Stress Suite")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("- Fanout stages: $($FanoutStages -join ' -> ')")
$lines.Add("- Seed events: $SeedEvents")
$lines.Add("- Concurrency: $Concurrency")
$lines.Add("- Requests per worker: $RequestsPerWorker")
$lines.Add("- Rounds per stage: $Rounds")
$lines.Add("")
$lines.Add("## Stage Runs")
$lines.Add("| followers | status | detail | report |")
$lines.Add("|---:|---|---|---|")
foreach ($step in $stepResults) {
    $reportValue = if ([string]::IsNullOrWhiteSpace($step.ReportPath)) { "n/a" } else { $step.ReportPath.Replace("|", "/") }
    $lines.Add("| $($step.Stage) | $($step.Status) | $($step.Detail.Replace('|', '/')) | $reportValue |")
}

if ($stageResults.Count -gt 0) {
    $lines.Add("")
    $lines.Add("## Median Summary")
    $lines.Add("| followers | success_rate | avg_latency_ms | p95_latency_ms | p99_latency_ms | report |")
    $lines.Add("|---:|---:|---:|---:|---:|---|")
    foreach ($result in $stageResults) {
        $lines.Add("| $($result.followers) | $([math]::Round($result.successRate, 2)) | $([math]::Round($result.avgLatencyMs, 2)) | $([math]::Round($result.p95LatencyMs, 2)) | $([math]::Round($result.p99LatencyMs, 2)) | $($result.reportPath.Replace('|', '/')) |")
    }

    if ($stageResults.Count -gt 1) {
        $lines.Add("")
        $lines.Add("## Stage Deltas")
        $lines.Add("| from -> to | p95_delta_ms | p95_delta_% | p99_delta_ms | p99_delta_% |")
        $lines.Add("|---|---:|---:|---:|---:|")
        for ($i = 1; $i -lt $stageResults.Count; $i++) {
            $previous = $stageResults[$i - 1]
            $current = $stageResults[$i]
            $p95 = Get-DeltaSummary -BaseValue $previous.p95LatencyMs -NextValue $current.p95LatencyMs
            $p99 = Get-DeltaSummary -BaseValue $previous.p99LatencyMs -NextValue $current.p99LatencyMs
            $lines.Add("| $($previous.followers) -> $($current.followers) | $([math]::Round($p95.Delta, 2)) | $([math]::Round($p95.DeltaPct, 2)) | $([math]::Round($p99.Delta, 2)) | $([math]::Round($p99.DeltaPct, 2)) |")
        }
    }
}

$lines | Set-Content -Path $summaryPath

Write-Host "Follower fanout stress suite report created: $summaryPath"
Write-Host "Status: $overallStatus | FailedStages: $((@($stepResults | Where-Object { $_.Status -ne 'PASSED' })).Count)"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
    exit 1
}
