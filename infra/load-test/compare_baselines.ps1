param(
    [Parameter(Mandatory = $true)]
    [string]$BaseReport,
    [Parameter(Mandatory = $true)]
    [string]$StressReport,
    [string]$OutputDir = "infra/load-test/reports"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function Get-ScenarioValue {
    param(
        [string[]]$Lines,
        [string]$Label
    )

    $pattern = "^- $([regex]::Escape($Label)):\s*(.+)$"
    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            return $Matches[1].Trim()
        }
    }
    return ""
}

function Delta-Pct {
    param(
        [double]$Base,
        [double]$Stress
    )

    $delta = $Stress - $Base
    $pct = if ($Base -eq 0) { 0.0 } else { ($delta / $Base) * 100.0 }
    return [pscustomobject]@{
        delta = $delta
        pct = $pct
    }
}

if (-not (Test-Path $BaseReport)) {
    throw "Base report not found: $BaseReport"
}
if (-not (Test-Path $StressReport)) {
    throw "Stress report not found: $StressReport"
}

$baseLines = Get-Content $BaseReport
$stressLines = Get-Content $StressReport

$metrics = @(
    "success_rate",
    "avg_latency_ms",
    "p50_latency_ms",
    "p95_latency_ms",
    "p99_latency_ms",
    "max_latency_ms"
)

$baseValues = @{}
$stressValues = @{}
foreach ($m in $metrics) {
    $baseValues[$m] = Get-MetricFromTable -Lines $baseLines -Metric $m
    $stressValues[$m] = Get-MetricFromTable -Lines $stressLines -Metric $m
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outRoot = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $outRoot -ItemType Directory -Force | Out-Null
$outputPath = Join-Path $outRoot "load-comparison-$timestamp.md"

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Load Baseline Comparison")
$lines.Add("")
$lines.Add("Generated at: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))")
$lines.Add("")
$lines.Add("## Inputs")
$lines.Add("- Base report: $BaseReport")
$lines.Add("- Stress report: $StressReport")
$lines.Add("")
$lines.Add("## Scenario Delta")
$lines.Add("| field | base | stress |")
$lines.Add("|---|---:|---:|")
$lines.Add("| Seed events | $(Get-ScenarioValue -Lines $baseLines -Label 'Seed events') | $(Get-ScenarioValue -Lines $stressLines -Label 'Seed events') |")
$lines.Add("| Concurrency | $(Get-ScenarioValue -Lines $baseLines -Label 'Concurrency') | $(Get-ScenarioValue -Lines $stressLines -Label 'Concurrency') |")
$lines.Add("| Requests per worker | $(Get-ScenarioValue -Lines $baseLines -Label 'Requests per worker') | $(Get-ScenarioValue -Lines $stressLines -Label 'Requests per worker') |")
$lines.Add("| Total requests | $(Get-ScenarioValue -Lines $baseLines -Label 'Total requests') | $(Get-ScenarioValue -Lines $stressLines -Label 'Total requests') |")
$lines.Add("")
$lines.Add("## HTTP Trend")
$lines.Add("| metric | base | stress | delta | delta_% |")
$lines.Add("|---|---:|---:|---:|---:|")
foreach ($m in $metrics) {
    $baseVal = [double]$baseValues[$m]
    $stressVal = [double]$stressValues[$m]
    $deltaObj = Delta-Pct -Base $baseVal -Stress $stressVal
    $lines.Add("| $m | $([math]::Round($baseVal, 2)) | $([math]::Round($stressVal, 2)) | $([math]::Round($deltaObj.delta, 2)) | $([math]::Round($deltaObj.pct, 2)) |")
}
$lines.Add("")

$p95Delta = Delta-Pct -Base ([double]$baseValues["p95_latency_ms"]) -Stress ([double]$stressValues["p95_latency_ms"])
$p99Delta = Delta-Pct -Base ([double]$baseValues["p99_latency_ms"]) -Stress ([double]$stressValues["p99_latency_ms"])
$lines.Add("## Readout")
$lines.Add("- p95 trend: $([math]::Round($p95Delta.delta, 2)) ms ($([math]::Round($p95Delta.pct, 2))%)")
$lines.Add("- p99 trend: $([math]::Round($p99Delta.delta, 2)) ms ($([math]::Round($p99Delta.pct, 2))%)")
$lines.Add("")

$lines | Set-Content -Path $outputPath

Write-Host "Comparison report created:"
Write-Host $outputPath
