# Median Load Comparison

Generated at: 2026-03-01 18:32:15

## Inputs
- Base median report: infra/load-test/reports/load-baseline-median-20260301-182607.md
- Stress median report: infra/load-test/reports/load-baseline-median-20260301-183210.md

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
| avg_latency_ms | 14.5 | 19.26 | 4.76 | 32.83 |
| p95_latency_ms | 31.74 | 49.51 | 17.77 | 55.99 |
| p99_latency_ms | 67.62 | 154.8 | 87.18 | 128.93 |

## Readout
- Median p95 trend: 17.77 ms (55.99%)
- Median p99 trend: 87.18 ms (128.93%)

