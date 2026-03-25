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
- `run_feed_observability_rollout_checklist.ps1`
- `run_runtime_observability_review_checklist.ps1`
- `validate_ops_alert_webhook.ps1`
- `validate_ops_alert_webhook_skipapp_flow.ps1`
- `run_ops_alert_webhook_staging_checklist.ps1`
- `validate_websocket_relay_smoke.ps1`
- `run_websocket_canary_external.ps1`
- `check_auth_legacy_usage.ps1`
- `calibrate_auth_observability_thresholds.ps1`
- `assess_auth_strict_mode_readiness.ps1`
- `run_auth_attack_scenarios.ps1`
- `run_auth_spoof_regression_check.ps1`
- `run_backend_contract_smoke.ps1`
- `run_idempotency_cleanup_smoke.ps1`
- `run_idempotency_cleanup_local_runtime_check.ps1`
- `run_audit_write_capture_smoke.ps1`
- `run_audit_validation_local_runtime_check.ps1`
- `run_data_integrity_staging_checklist.ps1`
- `run_data_integrity_local_runtime_check.ps1`
- `run_railway_frontend_staging_checklist.ps1`
- `run_portfolio_pagination_warning_smoke.ps1`
- `run_strategy_bot_forward_test_scheduler_smoke.ps1`
- `run_strategy_bot_forward_test_scheduler_staging_checklist.ps1`
- `run_rate_limit_profile_smoke.ps1`
- `run_rate_limit_staging_checklist.ps1`
- `run_auth_strict_mode_smoke.ps1`
- `run_auth_strict_mode_validation_suite.ps1`
- `run_auth_strict_mode_staging_checklist.ps1`
- `run_auth_strict_pre_cutover_checklist.ps1`
- `run_auth_strict_transport_validation.ps1`
- `run_auth_strict_post_cutover_checklist.ps1`
- `run_browser_origin_staging_checklist.ps1`
- `run_websocket_canary_staging_checklist.ps1`
- `run_websocket_staging_resilience_suite.ps1`
- `run_backend_contract_local_runtime_check.ps1`

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

Run staged follower-fanout median stress in one suite report:

```powershell
./infra/load-test/run_follower_fanout_stress_suite.ps1 `
  -BaseUrl "http://localhost:8080" `
  -FanoutStages @(1000,5000,10000) `
  -SeedEvents 200 `
  -Concurrency 8 `
  -RequestsPerWorker 120 `
  -Rounds 3
```

Useful notes:
- this wrapper chains `repeat_baseline_median.ps1` once per follower stage
- it emits one markdown summary linking each generated median report
- the summary also computes `p95/p99` deltas between adjacent follower stages

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

Run a single-command feed latency recalibration checklist:

```powershell
./infra/load-test/run_feed_latency_recalibration_checklist.ps1 `
  -ReportsGlob "infra/load-test/reports/load-baseline-median-*.md"
```

Useful notes:
- this wrapper delegates to `calibrate_feed_thresholds.ps1`
- it emits one summary report with the recommended:
  - `APP_FEED_OBSERVABILITY_WARNING_P95_MS`
  - `APP_FEED_OBSERVABILITY_WARNING_P99_MS`
  - `APP_FEED_OBSERVABILITY_CRITICAL_P99_MS`
- use it after collecting a meaningful telemetry window or refreshed median reports

Run the combined feed scale validation suite:

```powershell
./infra/load-test/run_feed_scale_validation_suite.ps1 `
  -BaseUrl "http://localhost:8080" `
  -FanoutStages @(1000,5000,10000) `
  -SeedEvents 200 `
  -Concurrency 8 `
  -RequestsPerWorker 120 `
  -Rounds 3
```

Useful notes:
- this suite chains:
  - `run_follower_fanout_stress_suite.ps1`
  - `run_feed_latency_recalibration_checklist.ps1`
- it emits one parent report linking the child fanout + recalibration reports
- use `-SkipFanoutSuite` or `-SkipRecalibration` when you only want one side of the flow

Run the parent feed observability rollout checklist:

```powershell
./infra/load-test/run_feed_observability_rollout_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -FanoutStages @(1000,5000,10000)
```

Useful notes:
- this wrapper chains:
  - `run_feed_scale_staging_checklist.ps1`
  - `run_feed_latency_recalibration_checklist.ps1`
- it keeps the remaining feed observability backlog under one parent report
- use `-SkipScaleChecklist` when you only want telemetry recalibration from an existing report history
- use `-SkipTelemetryRecalibration` when you only want fresh staging ladder evidence

Run the runtime observability review checklist after a meaningful traffic window:

```powershell
./infra/load-test/run_runtime_observability_review_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- captures one report across:
  - `/actuator/feedlatency`
  - `/actuator/authsessions`
  - `/actuator/websocket`
  - `/actuator/websocketcanary?refresh=false`
  - `/actuator/idempotency`
  - `/actuator/opsalerts`
- is intended to provide one attachable snapshot before pool/cache TTL tuning or threshold changes are proposed

For a fresh local proof against current code, run:

```powershell
./infra/load-test/run_runtime_observability_review_local_runtime_check.ps1
```

Local wrapper behavior:
- boots a temporary backend on a dedicated port
- runs the runtime observability review checklist against that runtime
- emits one parent markdown report plus app stdout/stderr logs

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

Useful notes:
- the local validator now waits for the temporary webhook listener to start listening before it triggers the feed-latency breach
- it also compares `/actuator/opsalerts` before/after counts after payload capture so local runs verify both webhook delivery and actuator summary deltas

Validate a live/staging app with its real configured ops webhook by triggering a deterministic manual alert and checking actuator metrics:

```powershell
./infra/load-test/run_ops_alert_webhook_staging_checklist.ps1 `
  -BaseUrl "http://localhost:8080"
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
- `/actuator/idempotency` exposes `alertState` alongside total record counts
- `/actuator/health/idempotency` is present and healthy
- `/actuator/opsalerts` exposes live counter summary fields
- unauthorized notification contract
- invalid notification-id `400` contract
- missing notification `404` contract
- `/actuator/idempotency`
- `/api/v1/ops/auditlog`
- `/actuator/auditlog`

Useful notes:
- this smoke now expects a backend that includes the latest idempotency observability rollout
- if the target runtime does not expose both `/actuator/idempotency.alertState` and `/actuator/health/idempotency`, the report includes a rollout-diagnostic note

Run a one-off local runtime wrapper that boots a temporary backend, waits for health, and then executes the same backend contract smoke:

```powershell
./infra/load-test/run_backend_contract_local_runtime_check.ps1
```

Useful notes:
- this wrapper is for local runtime verification when you do not want to manually restart the backend first
- it writes a parent report plus the child backend contract smoke report
- the temporary backend enables health component details and fresh idempotency observability so the child smoke can validate the full current contract set
- use `-SkipAppStart` if you want to point it at an already running backend on the selected port
- use `-PreserveAppAfterRun` if you want to inspect the temporary backend after the smoke finishes

Run idempotency cleanup smoke against a local backend + local Docker Postgres:

```powershell
./infra/load-test/run_idempotency_cleanup_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The idempotency cleanup smoke checks:
- register + protected idempotent write
- replay still works before cleanup
- expired idempotency row can be seeded into local Postgres
- `/actuator/health/idempotency` starts healthy before stale seeding
- stale expired backlog flips `/actuator/health/idempotency` to `DOWN`
- `/actuator/idempotency` surfaces `alertState=WARNING` while stale backlog is present
- `POST /actuator/idempotency` purges expired rows
- `/actuator/idempotency` reflects the cleanup result
- cleanup resets `alertState` back to `NONE`
- `/actuator/health/idempotency` returns to `UP` after cleanup
- replay semantics for a live key still work after cleanup

Useful notes:
- this smoke now expects a backend that includes the latest idempotency observability rollout
- if the target runtime does not expose both `/actuator/idempotency.alertState` and `/actuator/health/idempotency`, the script now fails fast with a rollout-diagnostic note instead of cascading follow-on failures
- use the local runtime wrapper below when you want the script to boot a fresh backend automatically

Run a one-off local runtime wrapper that boots a temporary backend, waits for health, and then executes the same idempotency cleanup smoke:

```powershell
./infra/load-test/run_idempotency_cleanup_local_runtime_check.ps1
```

Useful notes:
- this wrapper is for local runtime verification when you do not want to manually restart the backend first
- it writes a parent report plus the child idempotency cleanup smoke report
- the temporary backend enables health component details so the child smoke can validate `/actuator/health/idempotency` directly
- the temporary backend also shortens idempotency cleanup observability refresh to keep alert-state transitions visible during the same run
- use `-SkipAppStart` if you want to point it at an already running backend on the selected port
- use `-PreserveAppAfterRun` if you want to inspect the temporary backend after the smoke finishes

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
- create + delete an analysis post from the actor account
- verify `/api/v1/ops/auditlog` exposes request-id-filtered rows for:
  - portfolio create
  - trade buy
  - follow
  - comment
  - analysis create
  - analysis delete
- verify `/api/v1/ops/auditlog?limit=5` returns recent rows without `internal_error`
- verify `/actuator/auditlog` returns a recent snapshot:
  - without query params
  - with optional `?limit=5` present

Run portfolio pagination warning smoke against local infra:

```powershell
./infra/load-test/run_portfolio_pagination_warning_smoke.ps1
```

The portfolio pagination warning smoke checks:
- one-off backend boot with local Postgres/Redis wiring
- market price hydration is live enough for scheduler work
- owner portfolio page hydration still works after id-slice refactor
- discover page hydration still works after id-slice refactor
- snapshot scheduler activity is observed in logs
- leaderboard scheduler activity is observed in logs
- `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
  does not appear during the observation window

Run strategy-bot forward-test scheduler smoke against a local backend:

```powershell
./infra/load-test/run_strategy_bot_forward_test_scheduler_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The strategy-bot forward-test scheduler smoke checks:
- `/actuator/strategybotforwardtests` is exposed and returns a valid baseline snapshot
- when the runtime exposes health component details, `/actuator/health/strategyBotForwardTests` converges to `UP`
- register + create linked paper portfolio
- create a `READY` strategy bot
- request + execute a `FORWARD_TEST` run
- observe scheduler tick delta on the actuator snapshot
- prove the scheduler refreshed the exact target run id
- verify the actuator snapshot keeps the scheduler alert posture clear (`alertState=NONE`) after a fresh tick
- verify the refreshed run still exposes live summary fields such as `lastEvaluatedOpenTime`

Useful notes:
- this smoke is intentionally actuator-backed instead of blind sleeping
- it relies on the scheduler snapshot delta plus `lastRefreshedRunId` to prove recurring runtime refresh actually happened
- the scheduler snapshot now also reports:
  - `staleThresholdSeconds`
  - `alertState`
  - `lastTickAgeSeconds`
- if dedicated health-component details are not exposed by the target runtime, the script falls back to the actuator snapshot's `alertState` + tick-age fields instead of failing on `404`
- when the scheduler selects a run but reload-time state drift makes it non-refreshable, the snapshot now exposes skip diagnostics (`refreshSkipCount`, `lastSkippedRunId`, `lastSkipReason`) so failures are easier to classify
- use a poll window comfortably above `app.strategy-bots.forward-test-refresh-interval` if you override that interval locally or in staging

Run a one-off local runtime wrapper that boots a temporary backend, waits for health, and then executes the same scheduler smoke:

```powershell
./infra/load-test/run_strategy_bot_forward_test_scheduler_local_runtime_check.ps1
```

Useful notes:
- this wrapper is for local runtime verification when you do not want to manually restart the backend first
- it writes a parent report plus the child scheduler smoke report
- the temporary backend enables deterministic synthetic crypto candles, so the local check does not depend on external Binance REST access
- the temporary backend also shortens the forward-test refresh interval to keep the scheduler tick observable inside the smoke window
- the temporary backend enables health component details so the child smoke can validate `/actuator/health/strategyBotForwardTests` directly
- when the wrapper starts its own backend, the requested `-ServerPort` must be free; it now fails fast on port conflicts instead of silently hopping ports because shared ShedLock state can make parallel local runtimes false-fail the scheduler proof
- use `-SkipAppStart` if you want to point it at an already running backend on the selected port
- use `-PreserveAppAfterRun` if you want to inspect the temporary backend after the smoke finishes

Run the staging-oriented checklist wrapper against an already running backend:

```powershell
./infra/load-test/run_strategy_bot_forward_test_scheduler_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Useful notes:
- this wrapper first verifies `/actuator/health`
- then it delegates to `run_strategy_bot_forward_test_scheduler_smoke.ps1` with staging-friendly polling defaults
- if you provide `-RestartCommand`, the wrapper runs that command, waits for health recovery, and only then launches the scheduler smoke
- use this when you want a single report for "healthy target + optional fresh restart + scheduler recurring proof"

Run a combined audit validation suite against a local or staging backend:

```powershell
./infra/load-test/run_audit_validation_suite.ps1 `
  -BaseUrl "http://localhost:8080"
```

The audit validation suite orchestrates:
- `run_backend_contract_smoke.ps1`
- `run_audit_write_capture_smoke.ps1`

Useful notes:
- `-SkipContractSmoke` is useful when you only want write-capture validation.
- `-SkipWriteCapture` is useful when you only want contract/endpoint health.
- the suite writes a summary markdown report that links the child reports.

Run the staging-oriented audit checklist wrapper:

```powershell
./infra/load-test/run_audit_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Useful notes:
- this wrapper delegates to `run_audit_validation_suite.ps1` with staging-friendly defaults.
- use `-SkipWriteCapture` if you only want endpoint health/inspection validation.
- use `-SkipContractSmoke` if you only want append-only write capture validation.

Run a one-off local runtime wrapper that boots a temporary backend, seeds local market prices, and then executes the same audit validation suite:

```powershell
./infra/load-test/run_audit_validation_local_runtime_check.ps1
```

Useful notes:
- this wrapper is for local runtime verification when you do not want to manually restart the backend first
- the temporary backend enables `app.market.manual-seed-enabled` and seeds `BTCUSDT` through `/actuator/marketprices` so trade + analysis audit writes do not depend on external Binance connectivity
- it writes a parent report plus the child audit validation suite report

Run endpoint-aware rate-limit profile smoke against a local backend:

```powershell
./infra/load-test/run_rate_limit_profile_smoke.ps1 `
  -BaseUrl "http://localhost:8080"
```

The rate-limit profile smoke checks:
- uses a synthetic `X-Forwarded-For` identity so localhost traffic does not bypass the rate limiter
- drives burst traffic until `429` appears for:
  - portfolio comment writes
  - comment reply writes
  - user follow writes
  - auth refresh writes
- verifies normal read probes still return `200` after each write burst, proving profile-specific write throttling does not starve the default read bucket

Run the staging-focused rate-limit checklist wrapper:

```powershell
./infra/load-test/run_rate_limit_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Useful notes:
- this wrapper delegates to `run_rate_limit_profile_smoke.ps1` with a staging-oriented burst preset
- setup traffic uses separate non-synthetic headers so the read-isolation result is not polluted by test bootstrap requests
- keep the default synthetic IP unless staging sits behind a proxy that rewrites `X-Forwarded-For`

Run auth strict-mode smoke against a backend started with `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false`:

```powershell
./infra/load-test/run_auth_strict_mode_smoke.ps1 `
  -BaseUrl "http://localhost:18080"
```

The auth strict-mode smoke checks:
- register two users
- legacy `X-User-Id` only request is rejected with correlated `401 unauthorized`
- Bearer-only request is still accepted
- Bearer + matching `X-User-Id` remains accepted
- Bearer + mismatched `X-User-Id` is rejected
- authenticated follow still works without legacy-header auth

Run a focused spoof-regression check after strict-mode cutover:

```powershell
./infra/load-test/run_auth_spoof_regression_check.ps1 `
  -BaseUrl "http://localhost:8080"
```

The spoof-regression check:
- reuses `run_auth_attack_scenarios.ps1`
- drives only the Bearer + mismatched `X-User-Id` flood
- keeps a small canary probe tail so the auth path is checked under the same live runtime
- emits a focused markdown report that proves the header-mismatch scenario still returns only `401`

Run a strict-mode validation suite that chains the main rollout checks:

```powershell
./infra/load-test/run_auth_strict_mode_validation_suite.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -StrictSmokeBaseUrl "http://strict-local-backend:18081" `
  -SkipRelay
```

The validation suite can orchestrate:
- legacy usage check
- strict-mode auth smoke
- auth attack scenarios
- rate-limit profile smoke
- lightweight baseline
- websocket relay smoke

Useful notes:
- `-StrictSmokeBaseUrl` lets you point the strict smoke at a different runtime than the main staging base URL.
- `-SkipRelay` is useful when the target runtime is not relay-enabled.
- `-SkipBaseline` is useful when you only want auth- and websocket-focused validation.

Run the staging checklist wrapper with stricter rollout defaults:

```powershell
./infra/load-test/run_auth_strict_mode_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -StrictSmokeBaseUrl "http://strict-preview-core-api:8080" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Checklist behavior:
- wraps `run_auth_strict_mode_validation_suite.ps1`
- includes rate-limit profile smoke unless explicitly skipped
- uses a slightly heavier baseline preset intended for staging validation
- skips strict-smoke by default unless `-StrictSmokeBaseUrl` is provided

Run the post-cutover strict auth checklist once legacy header acceptance is disabled:

```powershell
./infra/load-test/run_auth_strict_post_cutover_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Checklist behavior:
- runs Bearer-only transport validation (`lightweight_baseline.ps1` + `validate_websocket_relay_smoke.ps1`)
- runs focused spoof-regression validation
- runs endpoint-aware rate-limit isolation validation
- use `-SkipRelay` when no relay-enabled target exists yet

Run the browser-origin staging checklist for CORS + WebSocket origin verification:

```powershell
./infra/load-test/run_browser_origin_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -FrontendOrigin "https://frontend.up.railway.app" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Checklist behavior:
- verifies REST preflight returns `Access-Control-Allow-Origin` for the frontend origin
- verifies register/login/protected-read requests succeed with the same browser origin
- reuses `validate_websocket_relay_smoke.ps1` with an explicit WebSocket `Origin` header
- use `-SkipRelay` when only HTTP CORS verification is needed

Run the leaderboard preference persistence checklist:

```powershell
./infra/load-test/run_leaderboard_preferences_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- registers a fresh user
- verifies default leaderboard preference payload
- persists dashboard leaderboard lens (`period`, `sortBy`, `direction`)
- persists public leaderboard lens (`sortBy`, `direction`) without clobbering dashboard values
- re-reads `/api/v1/users/me/preferences` to prove continuity
- probes both portfolio and account leaderboard endpoints with the persisted lens values

Run the leaderboard period validation checklist:

```powershell
./infra/load-test/run_leaderboard_period_validation_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- probes portfolio leaderboard periods `1D`, `1W`, `1M`, `ALL`
- captures both `RETURN_PERCENTAGE` and `PROFIT_LOSS` top slices
- writes one attachable markdown report for the remaining product/staging decision on ranking semantics
- treats empty-but-healthy slices as `CONDITIONAL_READY` so the report distinguishes data absence from transport/contract failure

The parent staging readiness suite also includes the data-integrity, error-contract, and leaderboard period validation steps unless `-SkipDataIntegrity`, `-SkipErrorContracts`, or `-SkipLeaderboardPeriods` is provided.

Run the feed-scale staging checklist:

```powershell
./infra/load-test/run_feed_scale_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- uses staging-oriented defaults for follower fanout stages: `1000 -> 5000 -> 10000`
- delegates to `run_feed_scale_validation_suite.ps1`
- keeps the per-stage median reports plus recalibration output linked behind one attachable wrapper report

Run the error-contract staging checklist:

```powershell
./infra/load-test/run_error_contract_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- verifies representative unified error payloads across portfolio/trade/tournament/watchlist surfaces
- verifies social follow/participation edge contracts on bearer-authenticated flows
- checks both JSON `requestId` continuity and `X-Request-Id` echo behavior

Run the Flyway/data staging checklist:

```powershell
./infra/load-test/run_flyway_data_staging_checklist.ps1 `
  -DbHost "staging-db-host" `
  -DbPort 5432 `
  -DbName "finance_db" `
  -DbUser "postgres"
```

Checklist behavior:
- verifies successful Flyway history rows for `V3`, `V9`, `V10`, and `V27`
- verifies the live `interactions_target_type_check` constraint includes `COMMENT`
- verifies BUY trade rows no longer carry `NULL` `realized_pnl` after the V9 data fix
- verifies no legacy `quantity = 0` crypto rows remain in `portfolio_items` / `trade_activities` under the `USDT` symbol heuristic after the V27 precision widening
- emits recent sample rows when zeroed crypto history is present so manual cleanup decisions can be made from the report itself

Run the parent data-integrity staging checklist:

```powershell
./infra/load-test/run_data_integrity_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -DbHost "staging-db-host" `
  -DbPort 5432 `
  -DbName "finance_db" `
  -DbUser "postgres"
```

Parent checklist behavior:
- runs the market-price false-loss checklist
- runs the Flyway/data checklist
- emits one parent markdown report linking both child reports
- keeps the remaining data-integrity rollout evidence in one attachable artifact instead of two separate commands

Run a one-off local runtime wrapper for the same error-contract checklist:

```powershell
./infra/load-test/run_error_contract_local_runtime_check.ps1
```

Wrapper behavior:
- boots a temporary backend against local Postgres/Redis
- waits for health
- executes `run_error_contract_staging_checklist.ps1` against the fresh runtime
- writes a parent report plus the child checklist report

Run Bearer-only transport validation for the two scripts that matter most after strict auth cutover:

```powershell
./infra/load-test/run_auth_strict_transport_validation.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Transport wrapper behavior:
- runs `lightweight_baseline.ps1`
- runs `validate_websocket_relay_smoke.ps1`
- writes one summary report linking both child reports
- keeps the focus on Bearer-authenticated browser/relay transport paths instead of the broader auth cutover suite

Run a single-command end-to-end `-SkipAppStart` flow (script starts core-api, runs skip validation, then stops app):

```powershell
./infra/load-test/validate_ops_alert_webhook_skipapp_flow.ps1
```

Useful notes:
- this wrapper is the safest local regression check for ops-alert webhook changes because it avoids stale shared runtime state
- the child validation now re-checks `/actuator/opsalerts` after the webhook payload arrives, so the resulting report proves log/webhook sent counters advanced together

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

Useful notes:
- the runner now reads `GET /actuator/websocketcanary?refresh=false` before and after the probe loop so the report shows:
  - initial latest snapshot state
  - final latest snapshot state
  - whether the latest canary view stayed `not-run-yet` or moved into an evaluated snapshot
- it no longer collides with PowerShell's built-in `$Error` variable while recording probe failures

Run the staging checklist that proves latest-snapshot transition plus external canary health:

```powershell
./infra/load-test/run_websocket_canary_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -Iterations 12 `
  -IntervalSec 20
```

Checklist behavior:
- reuses `run_websocket_canary_external.ps1`
- verifies the initial latest snapshot is `not-run-yet` unless `-AllowAlreadyProbed` is supplied
- verifies the post-run latest snapshot is no longer `not-run-yet`
- verifies the post-run latest snapshot is successful and not `CRITICAL`

Run the higher-level websocket staging resilience suite:

```powershell
./infra/load-test/run_websocket_staging_resilience_suite.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -FrontendOrigin "https://frontend.up.railway.app" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq" `
  -CanaryIterations 12 `
  -CanaryIntervalSec 20
```

Suite behavior:
- runs browser-origin validation
- runs websocket relay continuity / restart validation
- runs websocket canary transition validation
- runs direct notification SSE fallback delivery validation
- writes one summary report linking child reports
- note: the suite validates transport-level SSE fallback delivery, not full browser toast/render dedupe semantics

Run the standalone notification SSE fallback checklist:

```powershell
./infra/load-test/run_notification_sse_fallback_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- issues a short-lived bearer-authenticated stream token
- opens the SSE stream directly with that token
- triggers a follow notification and verifies one SSE event arrives
- verifies unread-count and inbox continuity after fallback delivery
- checks a short duplicate window so the SSE path does not replay the same notification twice

Run the fresh-local-runtime wrapper for the same SSE fallback checklist:

```powershell
./infra/load-test/run_notification_sse_fallback_local_runtime_check.ps1
```

Run the market-price false-loss checklist:

```powershell
./infra/load-test/run_market_price_false_loss_staging_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- creates a fresh public portfolio and opens one small BTCUSDT long
- verifies portfolio detail total equity does not collapse into false-loss territory
- verifies leaderboard profit/loss and total equity for the same portfolio stay above a configurable floor
- is intended to catch the old `0.00` quantity / sparse-price false-loss drift with one attachable report

Run the fresh-local-runtime restart wrapper for the same false-loss check:

```powershell
./infra/load-test/run_market_price_false_loss_local_runtime_check.ps1
```

Local wrapper behavior:
- boots one temporary backend with manual market seeding enabled
- opens the micro-position through the checklist
- restarts into a second fresh backend process
- re-reads `/actuator/marketprices`, portfolio detail, and leaderboard so restart-time false-loss regressions are visible

Run the parent local runtime wrapper for the same combined data-integrity proof:

```powershell
./infra/load-test/run_data_integrity_local_runtime_check.ps1
```

Local parent wrapper behavior:
- runs the market-price false-loss local runtime check
- runs the Flyway/data staging checklist against the same local database
- emits one parent report linking both child reports

Run the Railway frontend staging checklist after `apps/web` is deployed:

```powershell
./infra/load-test/run_railway_frontend_staging_checklist.ps1 `
  -FrontendBaseUrl "https://your-frontend.up.railway.app" `
  -BackendBaseUrl "https://your-backend.up.railway.app" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Checklist behavior:
- verifies the frontend domain serves `/auth/login`
- verifies the frontend proxy path serves `/api/v1/leaderboards`
- verifies proxied register/login and a protected unread-count read through the frontend domain
- delegates browser-origin and optional relay validation to `run_browser_origin_staging_checklist.ps1` using the real frontend origin

Run one parent staging readiness suite that links the main audit/auth/websocket/ops checklist reports:

```powershell
./infra/load-test/run_staging_readiness_suite.ps1 `
  -BaseUrl "http://staging-core-api:8080" `
  -FrontendOrigin "https://frontend.up.railway.app" `
  -RelayBrokerRestartCommand "docker restart finance-rabbitmq"
```

Suite behavior:
- runs audit staging checklist
- runs data-integrity staging checklist
- runs auth strict pre-cutover checklist
- runs websocket staging resilience suite
- runs ops webhook staging checklist
- writes one parent markdown report that links the child checklist reports
- use `-Skip*` switches to narrow the suite for partial rollout phases

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

Run the pre-cutover strict auth readiness checklist before disabling legacy-header acceptance:

```powershell
./infra/load-test/run_auth_strict_pre_cutover_checklist.ps1 `
  -BaseUrl "http://staging-core-api:8080"
```

Checklist behavior:
- reuses `assess_auth_strict_mode_readiness.ps1`
- proves:
  - legacy header acceptance is below threshold
  - auth refresh churn calibration is available
  - readiness is `READY` or `CONDITIONAL_READY`
- emits a single markdown summary that points at the underlying readiness report

For a fresh local proof with legacy-header acceptance already forced off, run:

```powershell
./infra/load-test/run_auth_strict_post_cutover_local_runtime_check.ps1 `
  -SkipRelay
```

Local wrapper behavior:
- boots a temporary backend with `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false`
- runs the post-cutover checklist against that current runtime
- emits one parent markdown report plus app stdout/stderr logs

## Output

Report file:

- `infra/load-test/reports/load-baseline-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/load-baseline-median-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/load-median-comparison-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/feed-threshold-calibration-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/ops-alert-webhook-validation-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/ops-alert-webhook-skipapp-flow-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/ops-alert-webhook-staging-checklist-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-relay-smoke-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-canary-external-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-canary-staging-checklist-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/websocket-staging-resilience-suite-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-legacy-usage-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-observability-calibration-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-strict-readiness-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-strict-pre-cutover-checklist-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-strict-post-cutover-local-runtime-check-YYYYMMDD-HHMMSS.md`
- `infra/load-test/reports/auth-attack-scenarios-YYYYMMDD-HHMMSS.md`

Contains:

- HTTP latency summary (avg/p50/p95/p99/max)
- success/failure counts
- PostgreSQL before/after/delta table
- Redis before/after/delta table
- Per-round median summary (for repeated scenario runs)
