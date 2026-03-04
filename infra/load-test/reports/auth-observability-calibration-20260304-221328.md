# Auth Observability Threshold Calibration

Generated at: 2026-03-04 22:13:37

## Scenario
- Base URL: http://localhost:8080
- Endpoint: http://localhost:8080/actuator/authsessions
- Iterations: 2
- Interval seconds: 1
- Request timeout seconds: 15
- Warning count multiplier: 1.25
- Critical count multiplier: 1.75
- Warning ratio multiplier: 1.2
- Critical ratio multiplier: 1.6
- Min samples floor: 20

## Result
- Status: **UNAVAILABLE**
- Successful samples: 0 / 2
- WARNING state samples: 0
- CRITICAL state samples: 0

## Current Runtime Thresholds
- unavailable

## P95 Baseline (Sampled Window Values)
- unavailable

## Recommended Env Overrides
- unavailable

## Run Details
| Run | CheckedAt | Status | AlertState | WindowSec | RefreshSuccess | InvalidRefresh | TotalAttempts | InvalidRatio | Error |
|-----|-----------|--------|------------|-----------|----------------|----------------|---------------|--------------|-------|
| 1 | 2026-03-04T22:13:28 | error | UNKNOWN | 0 | 0 | 0 | 0 | 0 | Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080) |
| 2 | 2026-03-04T22:13:33 | error | UNKNOWN | 0 | 0 | 0 | 0 | 0 | Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080) |

## Notes
- Iteration 1 failed: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Iteration 2 failed: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
