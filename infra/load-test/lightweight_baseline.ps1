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

$normalizedProfile = $LoadProfile.Trim().ToLowerInvariant()
if ($normalizedProfile -ne "global" -and $normalizedProfile -ne "mixed" -and $normalizedProfile -ne "fanout") {
    throw "LoadProfile must be one of: global, mixed, fanout"
}
if ($GlobalReadWeight -lt 0 -or $PersonalizedReadWeight -lt 0 -or $UserReadWeight -lt 0 -or $WriteWeight -lt 0) {
    throw "Weights must be >= 0"
}
if ($normalizedProfile -eq "mixed" -and (($GlobalReadWeight + $PersonalizedReadWeight + $UserReadWeight + $WriteWeight) -eq 0)) {
    throw "For mixed profile, at least one weight must be > 0"
}
if ($normalizedProfile -eq "fanout" -and $FanoutFollowers -lt 1) {
    throw "For fanout profile, FanoutFollowers must be >= 1"
}

function Test-Command {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-PgSnapshot {
    param(
        [string]$DbHostName,
        [int]$Port,
        [string]$Database,
        [string]$User,
        [string]$Password
    )

    if (-not (Test-Command "psql")) {
        return $null
    }

    $env:PGPASSWORD = $Password
    try {
        $sql = @"
SELECT to_jsonb(t) FROM (
  SELECT xact_commit, xact_rollback, blks_read, blks_hit, tup_returned, tup_fetched,
         tup_inserted, tup_updated, tup_deleted, deadlocks, temp_bytes
  FROM pg_stat_database
  WHERE datname = '$Database'
) t;
"@
        $raw = & psql -h $DbHostName -p $Port -U $User -d $Database -t -A -c $sql 2>$null
        $line = ($raw | Select-Object -First 1).Trim()
        if ([string]::IsNullOrWhiteSpace($line)) {
            return $null
        }
        return $line | ConvertFrom-Json
    } catch {
        return $null
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Parse-RedisInfo {
    param([string[]]$Lines)
    $map = @{}
    foreach ($line in $Lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.StartsWith("#")) { continue }
        $parts = $line.Split(":", 2)
        if ($parts.Count -eq 2) {
            $map[$parts[0]] = $parts[1].Trim()
        }
    }
    return $map
}

function Get-ActuatorMetricValue {
    param(
        [string]$ApiBase,
        [string]$MetricName,
        [string]$Statistic = "",
        [string[]]$Tags = @()
    )

    try {
        $uri = "$ApiBase/actuator/metrics/$MetricName"
        if ($Tags.Count -gt 0) {
            $query = @($Tags | ForEach-Object {
                    "tag=$([System.Uri]::EscapeDataString($_))"
                }) -join "&"
            $uri = "$uri`?$query"
        }

        $payload = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 3
        if ($null -eq $payload -or $null -eq $payload.measurements) {
            return $null
        }

        if (-not [string]::IsNullOrWhiteSpace($Statistic)) {
            $measurement = $payload.measurements | Where-Object { $_.statistic -eq $Statistic } | Select-Object -First 1
            if ($null -ne $measurement) {
                return [double]$measurement.value
            }
        }

        $fallback = $payload.measurements | Select-Object -First 1
        if ($null -eq $fallback) {
            return $null
        }
        return [double]$fallback.value
    } catch {
        return $null
    }
}

function Get-RedisSnapshotFromActuator {
    param([string]$ApiBase)

    try {
        $index = Invoke-RestMethod -Method Get -Uri "$ApiBase/actuator/metrics" -TimeoutSec 3
        if ($null -eq $index -or $null -eq $index.names) {
            return $null
        }

        $names = @($index.names)
        $redisNames = @($names | Where-Object {
                $_ -like "redis.*" -or $_ -like "spring.data.redis.*"
            })
        $cacheNames = @($names | Where-Object { $_ -like "cache.*" })
        $appRedisNames = @($names | Where-Object { $_ -like "app.redis.*" })
        if ($redisNames.Count -eq 0 -and $cacheNames.Count -eq 0 -and $appRedisNames.Count -eq 0) {
            return $null
        }

        $commands = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "redis.commands" -Statistic "COUNT"
        if ($null -eq $commands) {
            $commands = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "spring.data.redis.commands" -Statistic "COUNT"
        }
        $appCommandsTotal = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT"
        $appCommandsSuccess = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("result:success")
        $appCommandsError = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("result:error")
        $appCommandsHit = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("result:hit")
        $appCommandsMiss = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("result:miss")
        $appGetHits = (To-Number (Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("command:get", "result:hit"))) +
            (To-Number (Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("command:get_typed", "result:hit")))
        $appGetMisses = (To-Number (Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("command:get", "result:miss"))) +
            (To-Number (Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.calls" -Statistic "COUNT" -Tags @("command:get_typed", "result:miss")))
        $appLatencyMaxSeconds = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "app.redis.command.latency" -Statistic "MAX"

        $activeConnections = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "redis.connections.active" -Statistic "VALUE"
        if ($null -eq $activeConnections) {
            $activeConnections = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "spring.data.redis.connections.active" -Statistic "VALUE"
        }

        $memoryUsed = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "redis.memory.used" -Statistic "VALUE"
        if ($null -eq $memoryUsed) {
            $memoryUsed = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "spring.data.redis.memory.used" -Statistic "VALUE"
        }

        $cacheGetsHit = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "cache.gets" -Statistic "COUNT" -Tags @("result:hit")
        $cacheGetsMiss = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "cache.gets" -Statistic "COUNT" -Tags @("result:miss")
        $cachePuts = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "cache.puts" -Statistic "COUNT"
        $cacheRemovals = Get-ActuatorMetricValue -ApiBase $ApiBase -MetricName "cache.removals" -Statistic "COUNT"

        if ($null -eq $commands) {
            if ($null -ne $appCommandsTotal) {
                $commands = $appCommandsTotal
            } else {
                $commands = (To-Number $cacheGetsHit) + (To-Number $cacheGetsMiss) + (To-Number $cachePuts)
            }
        }

        return [pscustomobject]@{
            metrics_source            = "actuator"
            redis_version             = if ($redisNames.Count -gt 0) { "actuator-redis" } elseif ($appRedisNames.Count -gt 0) { "actuator-app-redis" } else { "actuator-cache" }
            total_commands_processed  = [long]$(if ($null -eq $commands) { 0 } else { $commands })
            keyspace_hits             = [long]$(if ($null -eq $cacheGetsHit) { 0 } else { $cacheGetsHit })
            keyspace_misses           = [long]$(if ($null -eq $cacheGetsMiss) { 0 } else { $cacheGetsMiss })
            evicted_keys              = [long]$(if ($null -eq $cacheRemovals) { 0 } else { $cacheRemovals })
            used_memory               = [long]$(if ($null -eq $memoryUsed) { 0 } else { $memoryUsed })
            connected_clients         = [long]$(if ($null -eq $activeConnections) { 0 } else { $activeConnections })
            app_command_success       = [long]$(To-Number $appCommandsSuccess)
            app_command_error         = [long]$(To-Number $appCommandsError)
            app_command_hit           = [long]$(To-Number $appCommandsHit)
            app_command_miss          = [long]$(To-Number $appCommandsMiss)
            app_get_hits              = [long]$appGetHits
            app_get_misses            = [long]$appGetMisses
            app_latency_max_ms        = [double]([math]::Round((To-Number $appLatencyMaxSeconds) * 1000, 3))
            observed_redis_metrics    = $redisNames.Count + $cacheNames.Count + $appRedisNames.Count
        }
    } catch {
        return $null
    }
}

function Get-RedisSnapshot {
    param(
        [string]$RedisHostName,
        [int]$Port,
        [string]$ApiBase
    )

    if (Test-Command "redis-cli") {
        try {
            $stats = Parse-RedisInfo (& redis-cli -h $RedisHostName -p $Port INFO stats 2>$null)
            $memory = Parse-RedisInfo (& redis-cli -h $RedisHostName -p $Port INFO memory 2>$null)
            $clients = Parse-RedisInfo (& redis-cli -h $RedisHostName -p $Port INFO clients 2>$null)
            $server = Parse-RedisInfo (& redis-cli -h $RedisHostName -p $Port INFO server 2>$null)

            return [pscustomobject]@{
                metrics_source           = "redis-cli"
                redis_version            = $server["redis_version"]
                total_commands_processed = [long]($stats["total_commands_processed"] ?? 0)
                keyspace_hits            = [long]($stats["keyspace_hits"] ?? 0)
                keyspace_misses          = [long]($stats["keyspace_misses"] ?? 0)
                evicted_keys             = [long]($stats["evicted_keys"] ?? 0)
                used_memory              = [long]($memory["used_memory"] ?? 0)
                connected_clients        = [long]($clients["connected_clients"] ?? 0)
                observed_redis_metrics   = 7
            }
        } catch {
            # Fall through to actuator fallback
        }
    }

    return Get-RedisSnapshotFromActuator -ApiBase $ApiBase
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [int]$Percentile
    )

    if ($Values.Count -eq 0) { return 0.0 }
    $sorted = $Values | Sort-Object
    $rank = [math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    if ($rank -lt 0) { $rank = 0 }
    if ($rank -ge $sorted.Count) { $rank = $sorted.Count - 1 }
    return [double]$sorted[$rank]
}

function To-Number {
    param($Value)
    if ($null -eq $Value) { return 0.0 }
    $normalized = $Value.ToString().Trim().Replace(",", ".")
    $parsed = 0.0
    if ([double]::TryParse(
            $normalized,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed
        )) {
        return $parsed
    }
    return 0.0
}

function Parse-LatencyMs {
    param([string]$Raw)
    if ([string]::IsNullOrWhiteSpace($Raw)) { return 0.0 }

    $normalized = $Raw.Trim().Replace(",", ".")
    $parsed = 0.0
    if ([double]::TryParse(
            $normalized,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed
        )) {
        return $parsed
    }

    return 0.0
}

function Get-DeltaObject {
    param($Before, $After)
    if ($null -eq $Before -or $null -eq $After) { return $null }

    $result = [ordered]@{}
    foreach ($prop in $After.PSObject.Properties.Name) {
        if ($prop -eq "redis_version" -or $prop -eq "metrics_source") { continue }
        $afterVal = To-Number $After.$prop
        $beforeVal = To-Number $Before.$prop
        $result[$prop] = $afterVal - $beforeVal
    }
    return [pscustomobject]$result
}

function Register-User {
    param(
        [string]$ApiBase,
        [string]$Username,
        [string]$Email,
        [string]$Password
    )

    $payload = @{
        username = $Username
        email    = $Email
        password = $Password
    } | ConvertTo-Json

    return Invoke-RestMethod -Method Post -Uri "$ApiBase/api/v1/auth/register" -ContentType "application/json" -Body $payload
}

function New-Portfolio {
    param(
        [string]$ApiBase,
        [string]$OwnerId,
        [string]$Name
    )

    $payload = @{
        name       = $Name
        ownerId    = $OwnerId
        visibility = "PUBLIC"
    } | ConvertTo-Json

    return Invoke-RestMethod -Method Post -Uri "$ApiBase/api/v1/portfolios" -ContentType "application/json" -Body $payload
}

function Follow-User {
    param(
        [string]$ApiBase,
        [string]$FollowerId,
        [string]$FollowingId
    )

    Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/api/v1/users/$FollowingId/follow" `
        -Headers @{ "X-User-Id" = $FollowerId } | Out-Null
}

function Initialize-FanoutFollowers {
    param(
        [string]$ApiBase,
        [string]$FollowingId,
        [int]$Count
    )

    $followerIds = New-Object System.Collections.Generic.List[string]
    $seed = [guid]::NewGuid().ToString("N").Substring(0, 8)
    for ($i = 1; $i -le $Count; $i++) {
        $username = "fan_f$($i)_$seed"
        $email = "fan_f$($i)_$seed@test.com"
        $follower = Register-User -ApiBase $ApiBase -Username $username -Email $email -Password "pass"
        Follow-User -ApiBase $ApiBase -FollowerId $follower.id -FollowingId $FollowingId
        $followerIds.Add($follower.id) | Out-Null

        if (($i % 100) -eq 0 -or $i -eq $Count) {
            Write-Host "Fanout setup progress: $i / $Count followers ready"
        }
    }

    return @($followerIds)
}

function Seed-FeedEvents {
    param(
        [string]$ApiBase,
        [string]$ActorId,
        [string]$PortfolioId,
        [int]$Count
    )

    for ($i = 1; $i -le $Count; $i++) {
        $payload = @{
            targetType = "PORTFOLIO"
            content    = "load-seed-comment-$i"
        } | ConvertTo-Json

        Invoke-RestMethod -Method Post `
            -Uri "$ApiBase/api/v1/interactions/$PortfolioId/comments" `
            -Headers @{ "X-User-Id" = $ActorId } `
            -ContentType "application/json" `
            -Body $payload | Out-Null
    }
}

function Invoke-WorkerLoad {
    param(
        [string]$ApiBase,
        [int]$Requests,
        [string]$OutFile
    )

    $rows = New-Object System.Collections.Generic.List[object]
    for ($i = 1; $i -le $Requests; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $ok = 1
        try {
            Invoke-RestMethod -Method Get -Uri "$ApiBase/api/v1/feed/global?page=0&size=20" | Out-Null
        } catch {
            $ok = 0
        } finally {
            $sw.Stop()
        }
        $rows.Add([pscustomobject]@{
                latency_ms = [double]$sw.Elapsed.TotalMilliseconds
                success    = $ok
            })
    }
    $rows | Export-Csv -NoTypeInformation -Path $OutFile
}

function Write-Report {
    param(
        [string]$Path,
        [hashtable]$Meta,
        [pscustomobject]$Summary,
        $PgBefore,
        $PgAfter,
        $PgDelta,
        $RedisBefore,
        $RedisAfter,
        $RedisDelta
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Lightweight Load Baseline")
    $lines.Add("")
    $lines.Add("Generated at: $($Meta["generated_at"])")
    $lines.Add("")
    $lines.Add("## Scenario")
    $lines.Add("- Base URL: $($Meta["base_url"])")
    $lines.Add("- Seed events: $($Meta["seed_events"])")
    $lines.Add("- Concurrency: $($Meta["concurrency"])")
    $lines.Add("- Requests per worker: $($Meta["requests_per_worker"])")
    $lines.Add("- Load profile: $($Meta["load_profile"])")
    $lines.Add("- Fanout followers: $($Meta["fanout_followers"])")
    $lines.Add("- Fanout setup duration ms: $($Meta["fanout_setup_ms"])")
    $lines.Add("- Weights (global/personalized/user/write): $($Meta["weights"])")
    $lines.Add("- Total requests: $($Summary.total_requests)")
    $lines.Add("")
    $lines.Add("## HTTP Results (feed operations)")
    $lines.Add("| metric | value |")
    $lines.Add("|---|---:|")
    $lines.Add("| success_count | $($Summary.success_count) |")
    $lines.Add("| failure_count | $($Summary.failure_count) |")
    $lines.Add("| success_rate | $([math]::Round($Summary.success_rate, 2))% |")
    $lines.Add("| avg_latency_ms | $([math]::Round($Summary.avg_latency_ms, 2)) |")
    $lines.Add("| p50_latency_ms | $([math]::Round($Summary.p50_latency_ms, 2)) |")
    $lines.Add("| p95_latency_ms | $([math]::Round($Summary.p95_latency_ms, 2)) |")
    $lines.Add("| p99_latency_ms | $([math]::Round($Summary.p99_latency_ms, 2)) |")
    $lines.Add("| max_latency_ms | $([math]::Round($Summary.max_latency_ms, 2)) |")
    if ($Summary.operation_counts) {
        foreach ($op in $Summary.operation_counts.PSObject.Properties.Name) {
            $lines.Add("| op_$op | $($Summary.operation_counts.$op) |")
        }
    }
    $lines.Add("")

    $lines.Add("## PostgreSQL Snapshot")
    if ($null -eq $PgBefore -or $null -eq $PgAfter -or $null -eq $PgDelta) {
        $lines.Add('PostgreSQL metrics unavailable (`psql` missing or query failed).')
    } else {
        $lines.Add("| metric | before | after | delta |")
        $lines.Add("|---|---:|---:|---:|")
        foreach ($prop in $PgAfter.PSObject.Properties.Name) {
            $before = [double]$PgBefore.$prop
            $after = [double]$PgAfter.$prop
            $delta = [double]$PgDelta.$prop
            $lines.Add("| $prop | $before | $after | $delta |")
        }
    }
    $lines.Add("")

    $lines.Add("## Redis Snapshot")
    if ($null -eq $RedisBefore -or $null -eq $RedisAfter -or $null -eq $RedisDelta) {
        $lines.Add('Redis metrics unavailable (`redis-cli` and Actuator metrics path unavailable).')
    } else {
        $lines.Add("- metrics_source: $($RedisAfter.metrics_source)")
        $lines.Add("- redis_version: $($RedisAfter.redis_version)")
        $lines.Add("")
        $lines.Add("| metric | before | after | delta |")
        $lines.Add("|---|---:|---:|---:|")
        foreach ($prop in $RedisAfter.PSObject.Properties.Name) {
            if ($prop -eq "redis_version" -or $prop -eq "metrics_source") { continue }
            $before = To-Number $RedisBefore.$prop
            $after = To-Number $RedisAfter.$prop
            $delta = To-Number $RedisDelta.$prop
            $lines.Add("| $prop | $before | $after | $delta |")
        }
    }
    $lines.Add("")

    $lines | Set-Content -Path $Path
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportDir = Resolve-Path "." | ForEach-Object { Join-Path $_.Path $OutputDir }
$tmpDir = Join-Path $reportDir "tmp-$timestamp"
$reportPath = Join-Path $reportDir "load-baseline-$timestamp.md"

New-Item -Path $reportDir -ItemType Directory -Force | Out-Null
New-Item -Path $tmpDir -ItemType Directory -Force | Out-Null

Write-Host "Collecting pre-load DB/Redis snapshots..."
$pgBefore = Get-PgSnapshot -DbHostName $DbHost -Port $DbPort -Database $DbName -User $DbUser -Password $DbPassword
$redisBefore = Get-RedisSnapshot -RedisHostName $RedisHost -Port $RedisPort -ApiBase $BaseUrl

Write-Host "Preparing seed data..."
$suffix = [guid]::NewGuid().ToString("N").Substring(0, 10)
$owner = Register-User -ApiBase $BaseUrl -Username "load_owner_$suffix" -Email "load_owner_$suffix@test.com" -Password "pass"
$actor = Register-User -ApiBase $BaseUrl -Username "load_actor_$suffix" -Email "load_actor_$suffix@test.com" -Password "pass"
$portfolio = New-Portfolio -ApiBase $BaseUrl -OwnerId $owner.id -Name "Load Portfolio $suffix"
Seed-FeedEvents -ApiBase $BaseUrl -ActorId $actor.id -PortfolioId $portfolio.id -Count $SeedEvents

$fanoutFollowerIds = @()
$fanoutSetupMs = 0.0
if ($normalizedProfile -eq "mixed") {
    Follow-User -ApiBase $BaseUrl -FollowerId $owner.id -FollowingId $actor.id
} elseif ($normalizedProfile -eq "fanout") {
    $fanoutSetupSw = [System.Diagnostics.Stopwatch]::StartNew()
    $fanoutFollowerIds = Initialize-FanoutFollowers -ApiBase $BaseUrl -FollowingId $actor.id -Count $FanoutFollowers
    $fanoutSetupSw.Stop()
    $fanoutSetupMs = [math]::Round($fanoutSetupSw.Elapsed.TotalMilliseconds, 2)
}

Write-Host "Warming feed cache..."
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/feed/global?page=0&size=20" | Out-Null
if ($normalizedProfile -eq "mixed") {
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/feed?page=0&size=20" -Headers @{ "X-User-Id" = $owner.id } | Out-Null
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/feed/user/$($actor.id)?page=0&size=20" | Out-Null
} elseif ($normalizedProfile -eq "fanout" -and $fanoutFollowerIds.Count -gt 0) {
    $sampleFollower = $fanoutFollowerIds[0]
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/feed?page=0&size=20" -Headers @{ "X-User-Id" = $sampleFollower } | Out-Null
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/feed/user/$($actor.id)?page=0&size=20" | Out-Null
}

$effectiveGlobalWeight = $GlobalReadWeight
$effectivePersonalizedWeight = $PersonalizedReadWeight
$effectiveUserWeight = $UserReadWeight
$effectiveWriteWeight = $WriteWeight
if ($normalizedProfile -eq "fanout") {
    $effectiveGlobalWeight = 0
    $effectivePersonalizedWeight = 0
    $effectiveUserWeight = 0
    $effectiveWriteWeight = 100
}

Write-Host "Running load workers..."
$jobs = @()
for ($w = 1; $w -le $Concurrency; $w++) {
    $outFile = Join-Path $tmpDir "worker-$w.csv"
    $jobs += Start-Job -ScriptBlock {
        param(
            $ApiBase,
            $Requests,
            $OutFile,
            $Profile,
            $OwnerId,
            $ActorId,
            $PortfolioId,
            $GlobalWeight,
            $PersonalizedWeight,
            $UserWeight,
            $WriteWeight
        )
        Set-StrictMode -Version Latest
        $ErrorActionPreference = "Stop"

        $rows = New-Object System.Collections.Generic.List[object]
        $totalWeight = $GlobalWeight + $PersonalizedWeight + $UserWeight + $WriteWeight
        for ($i = 1; $i -le $Requests; $i++) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $ok = 1
            $operation = "global"

            if ($Profile -eq "fanout") {
                $operation = "write"
            } elseif ($Profile -eq "mixed") {
                if ($totalWeight -le 0) {
                    $operation = "global"
                } else {
                    $pick = Get-Random -Minimum 1 -Maximum ($totalWeight + 1)
                    if ($pick -le $GlobalWeight) {
                        $operation = "global"
                    } elseif ($pick -le ($GlobalWeight + $PersonalizedWeight)) {
                        $operation = "personalized"
                    } elseif ($pick -le ($GlobalWeight + $PersonalizedWeight + $UserWeight)) {
                        $operation = "user"
                    } else {
                        $operation = "write"
                    }
                }
            }

            try {
                if ($operation -eq "global") {
                    Invoke-RestMethod -Method Get -Uri "$ApiBase/api/v1/feed/global?page=0&size=20" | Out-Null
                } elseif ($operation -eq "personalized") {
                    Invoke-RestMethod -Method Get -Uri "$ApiBase/api/v1/feed?page=0&size=20" -Headers @{ "X-User-Id" = $OwnerId } | Out-Null
                } elseif ($operation -eq "user") {
                    Invoke-RestMethod -Method Get -Uri "$ApiBase/api/v1/feed/user/$($ActorId)?page=0&size=20" | Out-Null
                } else {
                    $payload = @{
                        targetType = "PORTFOLIO"
                        content    = "load-mixed-comment-$i"
                    } | ConvertTo-Json
                    Invoke-RestMethod -Method Post `
                        -Uri "$ApiBase/api/v1/interactions/$PortfolioId/comments" `
                        -Headers @{ "X-User-Id" = $ActorId } `
                        -ContentType "application/json" `
                        -Body $payload | Out-Null
                }
            } catch {
                $ok = 0
            } finally {
                $sw.Stop()
            }
            $rows.Add([pscustomobject]@{
                    latency_ms = [double]$sw.Elapsed.TotalMilliseconds
                    success    = $ok
                    operation  = $operation
                })
        }
        $rows | Export-Csv -NoTypeInformation -Path $OutFile
    } -ArgumentList $BaseUrl, $RequestsPerWorker, $outFile, $normalizedProfile, $owner.id, $actor.id, $portfolio.id, $effectiveGlobalWeight, $effectivePersonalizedWeight, $effectiveUserWeight, $effectiveWriteWeight
}

Wait-Job -Job $jobs | Out-Null
$jobs | Receive-Job | Out-Null
$jobs | Remove-Job | Out-Null

$allRows = Get-ChildItem -Path $tmpDir -Filter "*.csv" | ForEach-Object { Import-Csv $_.FullName }
$latencies = @($allRows | ForEach-Object { Parse-LatencyMs -Raw $_.latency_ms })
$successCount = @($allRows | Where-Object { [int]$_.success -eq 1 }).Count
$failureCount = @($allRows | Where-Object { [int]$_.success -eq 0 }).Count
$total = $allRows.Count
$operationCounts = @{}
foreach ($row in $allRows) {
    $operation = if ([string]::IsNullOrWhiteSpace($row.operation)) { "unknown" } else { $row.operation }
    if (-not $operationCounts.ContainsKey($operation)) {
        $operationCounts[$operation] = 0
    }
    $operationCounts[$operation] += 1
}

$summary = [pscustomobject]@{
    total_requests  = $total
    success_count   = $successCount
    failure_count   = $failureCount
    success_rate    = if ($total -eq 0) { 0.0 } else { ($successCount * 100.0) / $total }
    avg_latency_ms  = if ($latencies.Count -eq 0) { 0.0 } else { ($latencies | Measure-Object -Average).Average }
    p50_latency_ms  = Get-Percentile -Values $latencies -Percentile 50
    p95_latency_ms  = Get-Percentile -Values $latencies -Percentile 95
    p99_latency_ms  = Get-Percentile -Values $latencies -Percentile 99
    max_latency_ms  = if ($latencies.Count -eq 0) { 0.0 } else { ($latencies | Measure-Object -Maximum).Maximum }
    operation_counts = [pscustomobject]$operationCounts
}

Write-Host "Collecting post-load DB/Redis snapshots..."
$pgAfter = Get-PgSnapshot -DbHostName $DbHost -Port $DbPort -Database $DbName -User $DbUser -Password $DbPassword
$redisAfter = Get-RedisSnapshot -RedisHostName $RedisHost -Port $RedisPort -ApiBase $BaseUrl

$pgDelta = Get-DeltaObject -Before $pgBefore -After $pgAfter
$redisDelta = Get-DeltaObject -Before $redisBefore -After $redisAfter

$meta = @{
    generated_at         = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    base_url             = $BaseUrl
    seed_events          = $SeedEvents
    concurrency          = $Concurrency
    requests_per_worker  = $RequestsPerWorker
    load_profile         = $normalizedProfile
    fanout_followers     = if ($normalizedProfile -eq "fanout") { $FanoutFollowers } else { 0 }
    fanout_setup_ms      = if ($normalizedProfile -eq "fanout") { $fanoutSetupMs } else { 0 }
    weights              = "$effectiveGlobalWeight/$effectivePersonalizedWeight/$effectiveUserWeight/$effectiveWriteWeight"
}

Write-Report -Path $reportPath -Meta $meta -Summary $summary `
    -PgBefore $pgBefore -PgAfter $pgAfter -PgDelta $pgDelta `
    -RedisBefore $redisBefore -RedisAfter $redisAfter -RedisDelta $redisDelta

Remove-Item -Path $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Load baseline report created:"
Write-Host $reportPath
