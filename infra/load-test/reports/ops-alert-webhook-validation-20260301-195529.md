# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:55:58

## Scenario
- Base URL: http://localhost:54224
- Startup mode: script-started-app
- Alert webhook target: http://127.0.0.1:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **PASSED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-195529.json
- App log: infra\load-test\reports\ops-alert-app-20260301-195529.log (stderr: infra\load-test\reports\ops-alert-app-20260301-195529.err.log)

## Notes
- Requested server port 18080 was in use; switched to 54224.

## Captured Payload
```json
{"service":"core-api","component":"feed-latency","severity":"CRITICAL","alertKey":"critical-breach","message":"Feed latency critical threshold breached","timestamp":"2026-03-01T16:55:58.132398400Z","details":{"warningBreaches":1,"maxObservedP95Ms":150.863872,"warningP95Ms":0.0,"criticalP99Ms":0.0,"criticalBreaches":1,"maxObservedP99Ms":150.863872,"warningP99Ms":0.0}}
```
