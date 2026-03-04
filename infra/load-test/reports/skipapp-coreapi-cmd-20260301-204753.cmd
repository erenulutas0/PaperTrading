@echo off
set SERVER_PORT=58080
set APP_ALERTING_ENABLED=true
set APP_ALERTING_COOLDOWN=PT1S
set APP_ALERTING_WEBHOOK_URL=http://127.0.0.1:19099/ops
set APP_FEED_OBSERVABILITY_MIN_SAMPLES=1
set APP_FEED_OBSERVABILITY_WARNING_P95_MS=0
set APP_FEED_OBSERVABILITY_WARNING_P99_MS=0
set APP_FEED_OBSERVABILITY_CRITICAL_P99_MS=0
set APP_MARKET_WS_ENABLED=false
set APP_SCHEDULING_ENABLED=false
set MAVEN_OPTS=-Dmaven.repo.local=C:/Users/pc/OneDrive/Masa?st?/finance-app/services/core-api/.m2repo
call C:\Users\pc\OneDrive\Masa?st?\finance-app\services\core-api\mvnw.cmd -q spring-boot:run > "C:/Users/pc/OneDrive/Masa?st?/finance-app/infra/load-test/reports/skipapp-coreapi-cmd-20260301-204753.log" 2>&1
