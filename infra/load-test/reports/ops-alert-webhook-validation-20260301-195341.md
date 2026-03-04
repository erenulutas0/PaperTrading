# Ops Alert Webhook Validation

Generated at: 2026-03-01 19:54:10

## Scenario
- Base URL: http://localhost:56543
- Startup mode: script-started-app
- Alert webhook target: http://127.0.0.1:19099/ops
- Forced feed thresholds:
  - APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
  - APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
  - APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
  - APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0

## Result
- Validation: **FAILED**
- Payload file: infra\load-test\reports\ops-alert-webhook-payload-20260301-195341.json
- App log: infra\load-test\reports\ops-alert-app-20260301-195341.log (stderr: infra\load-test\reports\ops-alert-app-20260301-195341.err.log)

## Notes
- Requested server port 18080 was in use; switched to 56543.
- You cannot call a method on a null-valued expression.
- At C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1:189 char:3
+   $payloadContent = (Get-Content -Raw -Path $payloadPath).Trim()
+   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
- at <ScriptBlock>, C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\validate_ops_alert_webhook.ps1: line 189
at <ScriptBlock>, <No file>: line 2

## Captured Payload
```json
{}
```
