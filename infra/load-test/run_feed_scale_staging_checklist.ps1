param(
    [string]$BaseUrl = "http://localhost:8080",
    [int[]]$FanoutStages = @(1000, 5000, 10000),
    [int]$SeedEvents = 200,
    [int]$Concurrency = 8,
    [int]$RequestsPerWorker = 120,
    [int]$Rounds = 3,
    [string]$ReportsGlob = "infra/load-test/reports/load-baseline-median-*.md",
    [double]$WarningMultiplier = 1.25,
    [double]$CriticalMultiplier = 1.60,
    [double]$Percentile = 0.90,
    [string]$OutputDir = "infra/load-test/reports",
    [switch]$SkipFanoutSuite,
    [switch]$SkipRecalibration,
    [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function Parse-MarkdownStatus {
    param([string]$ReportPath)

    if (-not (Test-Path $ReportPath)) {
        return ""
    }

    $content = Get-Content -Path $ReportPath -Raw
    $patternFlags = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline
    $match = [regex]::Match($content, "^\s*-\s+Status:\s+\*\*(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\*\*\s*$", $patternFlags)
    if ($match.Success -and $match.Groups.Count -ge 2) {
        return $match.Groups[1].Value.Trim()
    }

    $lineMatch = [regex]::Match($content, "^\s*-\s+Status:\s+(PASSED|FAILED|UNKNOWN|READY|CONDITIONAL_READY|NOT_READY|SKIPPED)\b", $patternFlags)
    if ($lineMatch.Success -and $lineMatch.Groups.Count -ge 2) {
        return $lineMatch.Groups[1].Value.Trim()
    }

    return ""
}

function Invoke-ScriptStep {
    param(
        [string]$ScriptPath,
        [hashtable]$Parameters,
        [string]$ReportPattern,
        [string]$ReportsDirectory
    )

    $before = Get-LatestReport -Directory $ReportsDirectory -Pattern $ReportPattern
    try {
        & $ScriptPath @Parameters | Out-Null

        $report = Get-LatestReport -Directory $ReportsDirectory -Pattern $ReportPattern
        if ([string]::IsNullOrWhiteSpace($report)) {
            return [pscustomobject]@{
                Status     = "FAILED"
                Detail     = "report_not_found"
                ReportPath = ""
            }
        }

        $status = Parse-MarkdownStatus -ReportPath $report
        if ([string]::IsNullOrWhiteSpace($status)) {
            $status = "PASSED"
        }

        return [pscustomobject]@{
            Status     = $status
            Detail     = "ok"
            ReportPath = $report
        }
    } catch {
        $report = Get-LatestReport -Directory $ReportsDirectory -Pattern $ReportPattern
        if ($report -eq $before) {
            $report = ""
        }
        return [pscustomobject]@{
            Status     = "FAILED"
            Detail     = $_.Exception.Message
            ReportPath = $report
        }
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$suiteScript = Join-Path $scriptDir "run_feed_scale_validation_suite.ps1"
if (-not (Test-Path $suiteScript)) {
    throw "Missing dependency script: $suiteScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$suiteParams = @{
    BaseUrl            = $BaseUrl
    FanoutStages       = @($FanoutStages)
    SeedEvents         = $SeedEvents
    Concurrency        = $Concurrency
    RequestsPerWorker  = $RequestsPerWorker
    Rounds             = $Rounds
    ReportsGlob        = $ReportsGlob
    WarningMultiplier  = $WarningMultiplier
    CriticalMultiplier = $CriticalMultiplier
    Percentile         = $Percentile
    OutputDir          = $OutputDir
    NoFail             = $true
}
if ($SkipFanoutSuite) { $suiteParams["SkipFanoutSuite"] = $true }
if ($SkipRecalibration) { $suiteParams["SkipRecalibration"] = $true }

$suiteResult = Invoke-ScriptStep `
    -ScriptPath $suiteScript `
    -Parameters $suiteParams `
    -ReportPattern "feed-scale-validation-suite-*.md" `
    -ReportsDirectory $resolvedOutputDir

$suiteStatus = $suiteResult.Status
$suiteDetail = $suiteResult.Detail
$suiteReport = $suiteResult.ReportPath

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "feed-scale-staging-checklist-$timestamp.md"

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Feed Scale Staging Checklist")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$suiteStatus**")
$lines.Add("- Fanout stages: $($FanoutStages -join ' -> ')")
$lines.Add("- Seed events: $SeedEvents")
$lines.Add("- Concurrency: $Concurrency")
$lines.Add("- Requests per worker: $RequestsPerWorker")
$lines.Add("- Rounds: $Rounds")
$lines.Add("")
$lines.Add("Coverage:")
$lines.Add("- staged follower-fanout median ladder")
$lines.Add("- linked median reports for each follower stage")
$lines.Add("- feed threshold recalibration summary from the same telemetry window")
$lines.Add("- one attachable wrapper report for the remaining feed-scale staging backlog")
$lines.Add("")
$lines.Add("| Step | Status | Detail | Report |")
$lines.Add("|---|---|---|---|")
$lines.Add("| Feed scale validation suite | $suiteStatus | $($suiteDetail.Replace('|','/')) | $(if ([string]::IsNullOrWhiteSpace($suiteReport)) { 'n/a' } else { $suiteReport.Replace('|','/') }) |")
$lines.Add("")
$lines.Add("Notes:")
$lines.Add("- This wrapper keeps the staging/default fanout ladder explicit at `1000 -> 5000 -> 10000` unless overridden.")
$lines.Add("- Use `-SkipRecalibration` only when you want raw median ladder evidence without threshold guidance.")

$lines | Set-Content -Path $summaryPath

Write-Host "Feed scale staging checklist report created: $summaryPath"
Write-Host "Status: $suiteStatus | Detail: $suiteDetail"

if ($suiteStatus -ne "PASSED" -and -not $NoFail) {
    exit 1
}
