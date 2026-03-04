# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:18:20

## Scenario
- Base URL: http://localhost:18080
- Startup mode: script-started-app
- Alert webhook target: http://localhost:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-191819.json
- App log: infra\load-test\reports\ops-alert-app-20260301-191819.log (stderr: infra\load-test\reports\ops-alert-app-20260301-191819.err.log)

## Notes
- Webhook payload file was not created.

## Captured Payload
```json
{}
```
