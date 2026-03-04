# Median Load Comparison

Generated at: 2026-03-01 18:35:06

## Inputs
- Base median report: infra/load-test/reports/load-baseline-median-20260301-182607.md
- Stress median report: infra/load-test/reports/load-baseline-median-20260301-183454.md

## Scenario Delta
| field | base | stress |
|---|---:|---:|
| Seed events | 400 | 400 |
| Concurrency | 12 | 12 |
| Requests per worker | 150 | 150 |
| Rounds | 3 | 3 |

## Median Trend
| metric | base_median | stress_median | delta | delta_% |
|---|---:|---:|---:|---:|
| success_rate | 100 | 100 | 0 | 0 |
| avg_latency_ms | 14.5 | 10.37 | -4.13 | -28.48 |
| p95_latency_ms | 31.74 | 24.96 | -6.78 | -21.36 |
| p99_latency_ms | 67.62 | 55.01 | -12.61 | -18.65 |

## Readout
- Median p95 trend: -6.78 ms (-21.36%)
- Median p99 trend: -12.61 ms (-18.65%)

