# Lightweight Load Baseline

This folder contains a lightweight, repeatable load scenario for the core API feed path with DB/Redis baseline snapshots.

## What it does

1. Creates two users (`owner`, `actor`) via `/api/v1/auth/register`.
2. Creates one public portfolio.
3. Seeds activity events by posting comments to the portfolio.
4. Warms the feed cache with `GET /api/v1/feed/global?page=0&size=20`.
   Authenticated personalized/write paths in the load scripts now reuse returned Bearer tokens from registration responses.
5. Runs concurrent workers calling the same feed endpoint.
   Optional mixed profile can drive:
   - global feed read
   - personalized feed read
   - user activity read
   - interaction write (comment) for invalidation pressure
   Optional fanout profile builds many followers on a single actor and runs write-heavy load to stress feed cache invalidation fanout.
6. Captures PostgreSQL and Redis counters before/after load.
   Redis order of preference: `redis-cli` -> Actuator (`/actuator/metrics`) fallback.
7. Writes a Markdown report under `infra/load-test/reports/`.

## Script

- `lightweight_baseline.ps1`
- `compare_baselines.ps1`
- `repeat_baseline_median.ps1`
- `compare_median_reports.ps1`
- `calibrate_feed_thresholds.ps1`
- `validate_ops_alert_webhook.ps1`
- `validate_ops_alert_webhook_skipapp_flow.ps1`
- `validate_websocket_relay_smoke.ps1`
- `run_websocket_canary_external.ps1`
- `check_auth_legacy_usage.ps1`
- `calibrate_auth_observability_thresholds.ps1`
- `assess_auth_strict_mode_readiness.ps1`
- `run_auth_attack_scenarios.ps1`
- `run_backend_contract_smoke.ps1`
- `run_idempotency_cleanup_smoke.ps1`
- `run_audit_write_capture_smoke.ps1`

## Prerequisites

- Core API running on `http://localhost:8080` (or pass `-BaseUrl`).
- PostgreSQL reachable (defaults: `localhost:5433`, `finance_db`).
- Redis reachable (defaults: `localhost:6379`).
- `psql` on PATH for DB metrics (optional but recommended).
- `redis-cli` on PATH for Redis metrics (optional but recommended).
- If `redis-cli` is missing, API should expose Actuator metrics endpoint (`/actuator/metrics`).

If `psql` is missing, HTTP load still runs and report marks DB metrics as unavailable.
If both `redis-cli` and Actuator metrics are unavailable, report marks Redis metrics as unavailable.

## Example usage

```powershell
./infra/load-test/lightweight_baseline.ps1
```

Custom run:

```powershell
./infra/load-test/lightweight_baseline.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SeedEvents 300 `
  -Concurrency 12 `
  -RequestsPerWorker 150
```

Production-like mixed feed profile:

```powershell
./infra/load-test/lightweight_baseline.ps1 `
  -BaseUrl "http://localhost:8080" `
  -LoadProfile "mixed" `
  -GlobalReadWeight 55 `
  -PersonalizedReadWeight 25 `
  -UserReadWeight 10 `
  -WriteWeight 10 `
  -SeedEvents 400 `
  -Concurrency 12 `
  -RequestsPerWorker 150
```

Follower fanout invalidation stress profile:

```powershell
./infra/load-test/lightweight_baseline.ps1 `
  -BaseUrl "http://localhost:8080" `
  -LoadProfile "fanout" `
  -FanoutFollowers 1000 `
  -SeedEvents 200 `
  -Concurrency 8 `
  -RequestsPerWorker 120
```

Higher-stress profile (for trend comparison):

```powershell
./infra/load-test/lightweight_baseline.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SeedEvents 120 `
  -Concurrency 10 `
  -RequestsPerWorker 120
```

Compare two generated reports:

```powershell
./infra/load-test/compare_baselines.ps1 `
  -BaseReport "infra/load-test/reports/load-baseline-<base>.md" `
  -StressReport "infra/load-test/reports/load-baseline-<stress>.md"
```

Run the same scenario for 3 rounds and produce a median p95/p99 summary:

```powershell
./infra/load-test/repeat_baseline_median.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SeedEvents 200 `
  -Concurrency 8 `
  -RequestsPerWorker 100 `
  -Rounds 3
```

Repeat in mixed profile:

```powershell
./infra/load-test/repeat_baseline_median.ps1 `
  -BaseUrl "http://localhost:8080" `
  -LoadProfile "mixed" `
  -GlobalReadWeight 55 `
  -PersonalizedReadWeight 25 `
  -UserReadWeight 10 `
  -WriteWeight 10 `
  -SeedEvents 400 `
  -Concurrency 12 `
  -RequestsPerWorker 150 `
  -Rounds 3
```

Repeat in follower fanout profile:

```powershell
./infra/load-test/repeat_baseline_median.ps1 `
  -BaseUrl "http://localhost:8080" `
  -LoadProfile "fanout" `
  -FanoutFollowers 1000 `
  -SeedEvents 200 `
  -Concurrency 8 `
  -RequestsPerWorker 120 `
  -Rounds 3
```

Compare two median summary reports:

```powershell
./infra/load-test/compare_median_reports.ps1 `
  -BaseMedianReport "infra/load-test/reports/load-baseline-median-<base>.md" `
  -StressMedianReport "infra/load-test/reports/load-baseline-median-<stress>.md"
```

Calibrate feed alert thresholds from historical median reports:

```powershell
./infra/load-test/calibrate_feed_thresholds.ps1 `
  -ReportsGlob "infra/load-test/reports/load-baseline-median-*.md" `
  -WarningMultiplier 1.25 `
  -CriticalMultiplier 1.60 `
  -Percentile 0.90
```

Validate end-to-end ops webhook routing (starts core-api on port `18080` by default):

```powershell
./infra/load-test/validate_ops_alert_webhook.ps1
```

Validate against an already running app:

```powershell
./infra/load-test/validate_ops_alert_webhook.ps1 `
  -SkipAppStart `
  -ServerPort 8080 `
  -WebhookPort 19099
```

Run backend contract smoke against a local backend:

```powershell
./infra/load-test/run_backend_contract_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The backend contract smoke currently checks:
- health
- register + authenticated write
- idempotent replay + request-id echo
- unauthorized notification contract
- invalid notification-id `400` contract
- missing notification `404` contract
- `/actuator/idempotency`
- `/api/v1/ops/auditlog`
- `/actuator/auditlog`

Run idempotency cleanup smoke against a local backend + local Docker Postgres:

```powershell
./infra/load-test/run_idempotency_cleanup_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The idempotency cleanup smoke checks:
- register + protected idempotent write
- replay still works before cleanup
- expired idempotency row can be seeded into local Postgres
- `POST /actuator/idempotency` purges expired rows
- `/actuator/idempotency` reflects the cleanup result
- replay semantics for a live key still work after cleanup

Run audit write-capture smoke against a local backend:

```powershell
./infra/load-test/run_audit_write_capture_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The audit write-capture smoke checks:
- register owner + actor
- create portfolio with correlated request id
- execute a trade against that portfolio
- follow the owner from the actor account
- comment on the portfolio from the actor account
- verify `/api/v1/ops/auditlog` exposes request-id-filtered rows for:
  - portfolio create
  - trade buy
  - follow
  - comment
- verify `/actuator/auditlog` still exposes a filtered recent snapshot

Run a single-command end-to-end `-SkipAppStart` flow (script starts core-api, runs skip validation, then stops app):

```powershell
./infra/load-test/validate_ops_alert_webhook_skipapp_flow.ps1
```

Validate websocket relay smoke scenario against an already running relay-enabled app:

```powershell
./infra/load-test/validate_websocket_relay_smoke.ps1 `
  -SkipAppStart `
  -BaseUrl "http://localhost:8080"
```

Validate websocket relay smoke scenario by script-starting core-api in relay mode:

```powershell
./infra/load-test/validate_websocket_relay_smoke.ps1 `
  -ServerPort 18082 `
  -RelayHost "localhost" `
  -RelayPort 61613
```

Validate relay failover with controlled broker restart command:

```powershell
./infra/load-test/validate_websocket_relay_smoke.ps1 `
  -SkipAppStart `
  -BaseUrl "http://localhost:8080" `
  -BrokerRestartCommand "docker restart finance-rabbitmq" `
  -BrokerRecoveryWaitSec 25
```

Run websocket canary externally (recommended from a separate node/network path):

```powershell
./infra/load-test/run_websocket_canary_external.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -Iterations 12 `
  -IntervalSec 20 `
  -IncludeWebSocketSnapshot `
  -FailOnAnyFailure
```

Check auth legacy-header usage readiness before disabling legacy mode:

```powershell
./infra/load-test/check_auth_legacy_usage.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -MaxLegacyAccepted 0
```

Sample auth refresh churn telemetry and generate recommended `APP_AUTH_OBSERVABILITY_*` overrides:

```powershell
./infra/load-test/calibrate_auth_observability_thresholds.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -Iterations 12 `
  -IntervalSec 20
```

Run calibration in report-only mode and do not fail pipeline even if endpoint is unavailable:

```powershell
./infra/load-test/calibrate_auth_observability_thresholds.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -NoFail
```

Run the same check in report-only mode (do not fail process on threshold breach):

```powershell
./infra/load-test/check_auth_legacy_usage.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -MaxLegacyAccepted 0 `
  -NoFail
```

Run a single-command strict-mode readiness assessment (legacy usage + churn calibration + readiness decision):

```powershell
./infra/load-test/assess_auth_strict_mode_readiness.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -MaxLegacyAccepted 0 `
  -CalibrationIterations 12 `
  -CalibrationIntervalSec 20
```

Fail pipeline when readiness remains blocked:

```powershell
./infra/load-test/assess_auth_strict_mode_readiness.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -FailOnNotReady
```

Run auth-focused attack scenarios (invalid JWT flood, header/token mismatch flood, invalid refresh flood + websocket canary probe stress):

```powershell
./infra/load-test/run_auth_attack_scenarios.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -InvalidJwtAttempts 120 `
  -HeaderMismatchAttempts 80 `
  -InvalidRefreshAttempts 120 `
  -CanaryProbeAttempts 20 `
  -FailOnAnyFailure
```

Note:

- `run_auth_attack_scenarios.ps1` intentionally keeps one `X-User-Id` spoof case to verify `Authorization` + legacy-header mismatch rejection during strict-mode rollout.
- `lightweight_baseline.ps1` and `validate_websocket_relay_smoke.ps1` are now Bearer-token based and are expected to pass with `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false`.

## Output

Report file:

- `infra/load-test/reports/load-baseline-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/load-baseline-median-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/load-median-comparison-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/feed-threshold-calibration-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/ops-alert-webhook-validation-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/ops-alert-webhook-skipapp-flow-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-relay-smoke-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-canary-external-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-legacy-usage-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-observability-calibration-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-strict-readiness-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-attack-scenarios-YYYYMMDD-HHMMSS.md`

Contains:

- HTTP latency summary (avg/p50/p95/p99/max)
- success/failure counts
- PostgreSQL before/after/delta table
- Redis before/after/delta table
- Per-round median summary (for repeated scenario runs)
