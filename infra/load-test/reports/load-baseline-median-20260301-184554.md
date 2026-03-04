# Baseline Median Summary

Generated at: 2026-03-01 18:45:54

## Scenario
- Base URL: http://localhost:8080
- Seed events: 300
- Concurrency: 10
- Requests per worker: 120
- Load profile: mixed
- Weights (global/personalized/user/write): 60/25/15/0
- Rounds: 3

## Per-Round Results
| round | success_rate | avg_latency_ms | p50_latency_ms | p95_latency_ms | p99_latency_ms | max_latency_ms | report |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 86.5 | 4.8 | 3.62 | 7.32 | 16.76 | 150.92 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-184532.md |
| 2 | 84.42 | 4.43 | 3.61 | 5.89 | 16.28 | 137.32 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-184540.md |
| 3 | 85.75 | 8.56 | 5.69 | 20.46 | 40.82 | 207.85 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-184546.md |

## Median Readout
| metric | median |
|---|---:|
| success_rate | 85.75 |
| avg_latency_ms | 4.8 |
| p95_latency_ms | 7.32 |
| p99_latency_ms | 16.76 |

