# Auth Legacy Usage Check

Generated at: 2026-03-04 22:24:44

## Scenario
- Base URL: http://localhost:8080
- Metric endpoint: http://localhost:8080/actuator/metrics/app.auth.requests.total
- Max legacy accepted threshold: 0
- Request timeout seconds: 15

## Result
- Status: **UNAVAILABLE**
- Legacy accepted (mode=legacy-header,result=accepted): 0
- Legacy rejected disabled (mode=legacy-header,result=rejected_disabled): 0
- Bearer accepted (mode=bearer,result=accepted): 0
- Anonymous accepted (mode=anonymous,result=accepted): 0

## Available Tags
- unavailable

## Notes
- Failed to fetch metric snapshot: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Failed to fetch legacy accepted slice: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Failed to fetch legacy rejected slice: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Failed to fetch bearer accepted slice: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
- Failed to fetch anonymous accepted slice: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (localhost:8080)
