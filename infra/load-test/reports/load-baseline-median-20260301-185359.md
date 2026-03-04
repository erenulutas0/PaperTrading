# Baseline Median Summary

Generated at: 2026-03-01 18:53:59

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
| 1 | 100 | 16.13 | 10.28 | 43.86 | 111.54 | 207.89 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-185332.md |
| 2 | 100 | 16.42 | 11.58 | 38.86 | 68.97 | 268.57 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-185343.md |
| 3 | 100 | 9.19 | 6.18 | 19.7 | 35.52 | 223.68 | C:\Users\pc\OneDrive\Masaüstü\finance-app\infra\load-test\reports\load-baseline-20260301-185351.md |

## Median Readout
| metric | median |
|---|---:|
| success_rate | 100 |
| avg_latency_ms | 16.13 |
| p95_latency_ms | 38.86 |
| p99_latency_ms | 68.97 |

