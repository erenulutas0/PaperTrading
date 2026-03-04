# Ops Alert Webhook Validation

Generated at: 2026-03-01 21:00:43

## Scenario
- Base URL: http://localhost:64919
- Startup mode: reuse-existing-app
- Alert webhook target: http://127.0.0.1:64920/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **PASSED**
- App process id: n/a
- App lifecycle: n/a
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-210042.json
- App log: n/a (app not started by script)

## Notes
- none

## Captured Payload
```json
{"service":"core-api","component":"feed-latency","severity":"CRITICAL","alertKey":"critical-breach","message":"Feed latency critical threshold breached","timestamp":"2026-03-01T18:00:42.881201300Z","details":{"warningP99Ms":0.0,"warningBreaches":1,"maxObservedP95Ms":184.418304,"warningP95Ms":0.0,"criticalP99Ms":0.0,"criticalBreaches":1,"maxObservedP99Ms":184.418304}}
```
