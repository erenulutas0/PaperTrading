# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:22:08

## Scenario
- Base URL: http://localhost:57889
- Startup mode: script-started-app
- Alert webhook target: http://127.0.0.1:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-192137.json
- App log: infra\load-test\reports\ops-alert-app-20260301-192137.log (stderr: infra\load-test\reports\ops-alert-app-20260301-192137.err.log)

## Notes
- Requested server port 18080 was in use; switched to 57889.
- You cannot call a method on a null-valued expression.

## Captured Payload
```json
{}
```
