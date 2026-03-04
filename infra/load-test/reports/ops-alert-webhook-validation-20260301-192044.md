# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:21:17

## Scenario
- Base URL: http://localhost:52936
- Startup mode: script-started-app
- Alert webhook target: http://localhost:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-192044.json
- App log: infra\load-test\reports\ops-alert-app-20260301-192044.log (stderr: infra\load-test\reports\ops-alert-app-20260301-192044.err.log)

## Notes
- Requested server port 18080 was in use; switched to 52936.
- You cannot call a method on a null-valued expression.

## Captured Payload
```json
{}
```
