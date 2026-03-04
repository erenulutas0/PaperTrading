# Median Load Comparison

Generated at: 2026-03-01 18:26:19

## Inputs
- Base median report: infra/load-test/reports/load-baseline-median-20260301-182103.md
- Stress median report: infra/load-test/reports/load-baseline-median-20260301-182607.md

## Scenario Delta
| field | base | stress |
|---|---:|---:|
| Seed events | 200 | 400 |
| Concurrency | 8 | 12 |
| Requests per worker | 100 | 150 |
| Rounds | 3 | 3 |

## Median Trend
| metric | base_median | stress_median | delta | delta_% |
|---|---:|---:|---:|---:|
| success_rate | 100 | 100 | 0 | 0 |
| avg_latency_ms | 7.13 | 14.5 | 7.37 | 103.37 |
| p95_latency_ms | 10.23 | 31.74 | 21.51 | 210.26 |
| p99_latency_ms | 29.36 | 67.62 | 38.26 | 130.31 |

## Readout
- Median p95 trend: 21.51 ms (210.26%)
- Median p99 trend: 38.26 ms (130.31%)

