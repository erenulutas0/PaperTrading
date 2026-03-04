# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:55:08

## Scenario
- Base URL: http://localhost:51044
- Startup mode: script-started-app
- Alert webhook target: http://127.0.0.1:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-195438.json
- App log: infra\load-test\reports\ops-alert-app-20260301-195438.log (stderr: infra\load-test\reports\ops-alert-app-20260301-195438.err.log)

## Notes
- Requested server port 18080 was in use; switched to 51044.
- You cannot call a method on a null-valued expression.
- At C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1:191 char:3
+   $payloadContent = (Get-Content -Raw -Path $payloadPath).Trim()
+   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
- at <ScriptBlock>, C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1: line 191
at <ScriptBlock>, <No file>: line 2

## Captured Payload
```json
{}
```
