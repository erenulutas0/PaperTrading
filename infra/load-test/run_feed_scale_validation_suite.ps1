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

function Add-StepResult {
    param(
        [System.Collections.Generic.List[object]]$Results,
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$ReportPath = ""
    )

    $Results.Add([pscustomobject]@{
            Name = $Name
            Status = $Status
            Detail = $Detail
            ReportPath = $ReportPath
        }) | Out-Null
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
$fanoutScript = Join-Path $scriptDir "run_follower_fanout_stress_suite.ps1"
$recalibrationScript = Join-Path $scriptDir "run_feed_latency_recalibration_checklist.ps1"

if (-not $SkipFanoutSuite -and -not (Test-Path $fanoutScript)) {
    throw "Missing dependency script: $fanoutScript"
}
if (-not $SkipRecalibration -and -not (Test-Path $recalibrationScript)) {
    throw "Missing dependency script: $recalibrationScript"
}

$resolvedOutputDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
New-Item -Path $resolvedOutputDir -ItemType Directory -Force | Out-Null

$results = New-Object 'System.Collections.Generic.List[object]'

if (-not $SkipFanoutSuite) {
    $fanoutParams = @{
        BaseUrl           = $BaseUrl
        FanoutStages      = @($FanoutStages)
        SeedEvents        = $SeedEvents
        Concurrency       = $Concurrency
        RequestsPerWorker = $RequestsPerWorker
        Rounds            = $Rounds
        NoFail            = $true
    }
    $fanoutResult = Invoke-ScriptStep `
        -ScriptPath $fanoutScript `
        -Parameters $fanoutParams `
        -ReportPattern "fanout-stress-suite-*.md" `
        -ReportsDirectory $resolvedOutputDir
    Add-StepResult -Results $results -Name "Follower Fanout Suite" -Status $fanoutResult.Status -Detail $fanoutResult.Detail -ReportPath $fanoutResult.ReportPath
}

if (-not $SkipRecalibration) {
    $recalibrationParams = @{
        ReportsGlob        = $ReportsGlob
        WarningMultiplier  = $WarningMultiplier
        CriticalMultiplier = $CriticalMultiplier
        Percentile         = $Percentile
        OutputDir          = $OutputDir
        NoFail             = $true
    }
    $recalibrationResult = Invoke-ScriptStep `
        -ScriptPath $recalibrationScript `
        -Parameters $recalibrationParams `
        -ReportPattern "feed-latency-recalibration-checklist-*.md" `
        -ReportsDirectory $resolvedOutputDir
    Add-StepResult -Results $results -Name "Feed Threshold Recalibration" -Status $recalibrationResult.Status -Detail $recalibrationResult.Detail -ReportPath $recalibrationResult.ReportPath
}

$failedCount = (@($results | Where-Object { $_.Status -ne "PASSED" })).Count
$overallStatus = if ($failedCount -eq 0) { "PASSED" } else { "FAILED" }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $resolvedOutputDir "feed-scale-validation-suite-$timestamp.md"

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add("# Feed Scale Validation Suite")
$lines.Add("")
$lines.Add("- Timestamp: $(Get-Date -Format s)")
$lines.Add("- Base URL: $BaseUrl")
$lines.Add("- Status: **$overallStatus**")
$lines.Add("- Fanout stages: $($FanoutStages -join ' -> ')")
$lines.Add("- Seed events: $SeedEvents")
$lines.Add("- Concurrency: $Concurrency")
$lines.Add("- Requests per worker: $RequestsPerWorker")
$lines.Add("- Rounds: $Rounds")
$lines.Add("- Reports glob: $ReportsGlob")
$lines.Add("")
$lines.Add("| Step | Status | Detail | Report |")
$lines.Add("|---|---|---|---|")
foreach ($result in $results) {
    $reportValue = if ([string]::IsNullOrWhiteSpace($result.ReportPath)) { "n/a" } else { $result.ReportPath.Replace("|", "/") }
    $lines.Add("| $($result.Name) | $($result.Status) | $($result.Detail.Replace('|', '/')) | $reportValue |")
}
$lines.Add("")
$lines.Add("## Notes")
$lines.Add("- This suite narrows feed-scale validation to one command and one summary report.")
$lines.Add("- The follower fanout suite captures median ladder behavior; the recalibration checklist turns the resulting median history into recommended `APP_FEED_OBSERVABILITY_*` overrides.")

$lines | Set-Content -Path $summaryPath

Write-Host "Feed scale validation suite report created: $summaryPath"
Write-Host "Status: $overallStatus | FailedSteps: $failedCount"

if ($overallStatus -eq "FAILED" -and -not $NoFail) {
    exit 1
}
