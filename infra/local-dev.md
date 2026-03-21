# Local Dev

Recommended local topology:

1. Postgres + Redis in Docker
2. Backend on `http://localhost:8080`
3. Frontend on `http://localhost:3005`

## Prereqs

- Docker Desktop
- Java 21
- Node.js + npm
- `apps/web/node_modules` installed (`npm install` in `apps/web`)

## Start order

### 0. Guided bootstrap

```powershell
.\infra\start-local-stack.ps1
```

This now:

- starts Docker infra
- opens a backend PowerShell window
- opens a frontend PowerShell window

### 1. Infra

```powershell
.\infra\start-local-infra.ps1
```

### 2. Backend

```powershell
.\infra\start-local-backend.ps1
```

Local backend defaults used by the script:

- Postgres: `jdbc:postgresql://localhost:5433/finance_db`
- Redis: `redis://localhost:6379`
- Port: `8080`
- CORS / WS origins: `http://localhost:3005`
- Alerting disabled
- Auth observability enabled
- WebSocket canary disabled

Strict-mode backend variant:

```powershell
.\infra\start-local-backend-strict.ps1
```

This keeps the same local infra contract, but starts backend with:

- `APP_AUTH_ALLOW_LEGACY_USER_ID_HEADER=false`
- `APP_AUTH_ENFORCE_HEADER_TOKEN_MATCH=true`
- default strict-mode port: `18080`

### 3. Frontend

```powershell
.\infra\start-local-frontend.ps1
```

Local frontend defaults used by the script:

- `API_BASE_URL=http://localhost:8080`
- `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
- `NEXT_PUBLIC_WS_BROKER_URL=ws://localhost:8080`
- `NEXT_PUBLIC_WS_HTTP_URL=http://localhost:8080`

Open:

- Frontend: `http://localhost:3005`
- Backend: `http://localhost:8080`

## Quick health check

```powershell
.\infra\check-local-stack.ps1
```

## Backend contract smoke

After the stack is up, run:

```powershell
.\infra\load-test\run_backend_contract_smoke.ps1 -BaseUrl "http://localhost:8080"
```

This validates a small but useful local slice:

- request correlation echo
- idempotent replay behavior
- `/actuator/idempotency`
- `/api/v1/ops/auditlog`
- `/actuator/auditlog`
- unauthorized API error contract

## Auth strict-mode smoke

Against a strict-mode local backend:

```powershell
.\infra\load-test\run_auth_strict_mode_smoke.ps1 -BaseUrl "http://localhost:18080"
```

This validates:

- legacy `X-User-Id` only requests are rejected
- Bearer-only requests still work
- Bearer + matching legacy header is tolerated
- Bearer + mismatched legacy header is rejected
- authenticated follow flow still works in strict mode

## Stop infra

```powershell
.\infra\stop-local-infra.ps1
```
