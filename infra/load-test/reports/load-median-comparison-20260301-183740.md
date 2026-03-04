# Median Load Comparison

Generated at: 2026-03-01 18:37:40

## Inputs
- Base median report: infra/load-test/reports/load-baseline-median-20260301-182607.md
- Stress median report: infra/load-test/reports/load-baseline-median-20260301-183731.md

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
| avg_latency_ms | 14.5 | 8.64 | -5.86 | -40.41 |
| p95_latency_ms | 31.74 | 18.4 | -13.34 | -42.03 |
| p99_latency_ms | 67.62 | 45.25 | -22.37 | -33.08 |

## Readout
- Median p95 trend: -13.34 ms (-42.03%)
- Median p99 trend: -22.37 ms (-33.08%)

