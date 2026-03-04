# Auth Observability Threshold Calibration

Generated at: 2026-03-04 22:11:50

## Scenario
- Base URL: http://localhost:8080
- Endpoint: http://localhost:8080/actuator/authsessions
- Iterations: 3
- Interval seconds: 1
- Request timeout seconds: 15
- Warning count multiplier: 1.25
- Critical count multiplier: 1.75
- Warning ratio multiplier: 1.2
- Critical ratio multiplier: 1.6
- Min samples floor: 20

## Result
- Status: **UNAVAILABLE**
- Successful samples: 0 / 3
- WARNING state samples: 0
- CRITICAL state samples: 0

## Current Runtime Thresholds
- unavailable

## P95 Baseline (Sampled Window Values)
- p95 refreshSuccessCount: 0
- p95 invalidRefreshCount: 0
- p95 totalRefreshAttempts: 0
- p95 invalidRefreshRatio: 0

## Recommended Env Overrides
- APP_AUTH_OBSERVABILITY_MIN_SAMPLES=20
- APP_AUTH_OBSERVABILITY_WARNING_REFRESH_COUNT=1
- APP_AUTH_OBSERVABILITY_CRITICAL_REFRESH_COUNT=1
- APP_AUTH_OBSERVABILITY_WARNING_INVALID_COUNT=1
- APP_AUTH_OBSERVABILITY_CRITICAL_INVALID_COUNT=1
- APP_AUTH_OBSERVABILITY_WARNING_INVALID_RATIO=0
- APP_AUTH_OBSERVABILITY_CRITICAL_INVALID_RATIO=0

## Run Details
| Run | CheckedAt | Status | AlertState | WindowSec | RefreshSuccess | InvalidRefresh | TotalAttempts | InvalidRatio | Error |
|-----|-----------|--------|------------|-----------|----------------|----------------|---------------|--------------|-------|
| 1 | 2026-03-04T22:11:36 | error | UNKNOWN | 0 | 0 | 0 | 0 | 0 | Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080) |
| 2 | 2026-03-04T22:11:41 | error | UNKNOWN | 0 | 0 | 0 | 0 | 0 | Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080) |
| 3 | 2026-03-04T22:11:46 | error | UNKNOWN | 0 | 0 | 0 | 0 | 0 | Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080) |

## Notes
- Iteration 1 failed: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Iteration 2 failed: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Iteration 3 failed: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
