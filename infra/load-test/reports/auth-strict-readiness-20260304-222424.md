# Auth Strict-Mode Readiness Assessment

Generated at: 2026-03-04 22:24:53

## Scenario
- Base URL: http://localhost:8080
- Max legacy accepted threshold: 0
- Calibration iterations: 2
- Calibration interval seconds: 1
- Request timeout seconds: 15

## Inputs
- Legacy report: C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\auth-legacy-usage-20260304-222424.md
- Calibration report: C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\auth-observability-calibration-20260304-222444.md

## Decision
- Readiness status: **NOT_READY**
- Reason: Legacy header usage is not below threshold.

## Observed Signals
- Legacy status: UNAVAILABLE
- Legacy accepted count: 0
- Calibration status: UNAVAILABLE
- Calibration warning samples: 0
- Calibration critical samples: 0

## Recommended Auth Observability Overrides
- unavailable

## Next Actions
- Resolve blocking status from legacy usage and/or auth churn telemetry.
- Re-run check_auth_legacy_usage.ps1 and calibrate_auth_observability_thresholds.ps1 after fixes.

## Notes
- Legacy readiness metric endpoint unavailable.
- Auth observability calibration endpoint unavailable.
