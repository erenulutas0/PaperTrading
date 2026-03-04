# Ops Alert Webhook Validation

Generated at: 2026-03-01 20:57:36

## Scenario
- Base URL: http://localhost:1
- Startup mode: reuse-existing-app
- Alert webhook target: http://127.0.0.1:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- App process id: n/a
- App lifecycle: n/a
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-205712.json
- App log: n/a (app not started by script)

## Notes
- Core API is not reachable at http://localhost:1/actuator/health. Start app or remove -SkipAppStart.
- At C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1:197 char:7
+       throw "Core API is not reachable at $healthUrl. Start app or re …
+       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
- at <ScriptBlock>, C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1: line 197
at <ScriptBlock>, <No file>: line 2

## Captured Payload
```json
{}
```
