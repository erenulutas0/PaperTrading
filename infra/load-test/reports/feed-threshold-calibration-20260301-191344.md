# Feed Latency Threshold Calibration

Generated at: 2026-03-01 19:13:44

## Inputs
- Reports glob: infra/load-test/reports/load-baseline-median-*.md
- Reports parsed: 10
- Percentile used: 0.9
- Warning multiplier: 1.25
- Critical multiplier: 1.6

## Parsed Median Inputs
| p95_latency_ms | p99_latency_ms | report |
|---:|---:|---|
| 25.47 | 47.01 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-181859.md |
| 10.23 | 29.36 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-182103.md |
| 31.74 | 67.62 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-182607.md |
| 49.51 | 154.8 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-183210.md |
| 24.96 | 55.01 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-183454.md |
| 18.4 | 45.25 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-183731.md |
| 27.11 | 132.46 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-184435.md |
| 7.32 | 16.76 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-184554.md |
| 13.34 | 116.14 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-184718.md |
| 38.86 | 68.97 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-median-20260301-185359.md |

## Calibration Result
- Baseline p95 (p90): 38.86 ms
- Baseline p99 (p90): 132.46 ms

Recommended config:
- APP_FEED_OBSERVABILITY_WARNING_P95_MS=48.58
- APP_FEED_OBSERVABILITY_WARNING_P99_MS=165.58
- APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=211.94

## Notes
- This script calibrates from synthetic load-test medians.
- Final production defaults should be re-calibrated after one sprint of real traffic telemetry.
