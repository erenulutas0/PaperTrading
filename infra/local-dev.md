# Local Dev

Recommended local topology:

1. Postgres + Redis in Docker
2. Backend on `http://localhost:8080`
3. Frontend on `http://localhost:3005`

## Prereqs

- Docker Desktop
- Java 21
- Node.js + npm

## Start order

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
- WebSocket canary disabled

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

## Stop infra

```powershell
.\infra\stop-local-infra.ps1
```
