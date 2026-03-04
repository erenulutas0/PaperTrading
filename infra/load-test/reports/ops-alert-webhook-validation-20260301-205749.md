# Ops Alert Webhook Validation

Generated at: 2026-03-01 20:58:22

## Scenario
- Base URL: http://localhost:62585
- Startup mode: script-started-app
- Alert webhook target: http://127.0.0.1:62586/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **PASSED**
- App process id: 5824
- App lifecycle: preserved
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-205749.json
- App log: infra\load-test\reports\ops-alert-app-20260301-205749.log (stderr: infra\load-test\reports\ops-alert-app-20260301-205749.err.log)

## Notes
- none

## Captured Payload
```json
{"service":"core-api","component":"feed-latency","severity":"CRITICAL","alertKey":"critical-breach","message":"Feed latency critical threshold breached","timestamp":"2026-03-01T17:58:21.993877100Z","details":{"warningBreaches":1,"maxObservedP95Ms":167.641088,"warningP95Ms":0.0,"criticalP99Ms":0.0,"criticalBreaches":1,"maxObservedP99Ms":167.641088,"warningP99Ms":0.0}}
```
