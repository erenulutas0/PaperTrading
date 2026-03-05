# Railway Staging Deployment (Monorepo)

Bu repo monorepo yapısında. Railway'de **tek service ile kökten deploy** denerseniz `Script start.sh not found`/`could not determine how to build` hatası alırsınız.

Doğru kurulum: aynı repodan 2 ayrı service.

## 1) Backend Service (Spring Boot)

- `New -> Service -> GitHub Repo`
- Repository: `erenulutas0/PaperTrading`
- Service adı: `core-api-staging` (öneri)
- `Settings -> Source`
- `Root Directory`: `services/core-api`

Deploy sonrası gerekli environment variables:

- `PORT` = Railway otomatik verir
- `SPRING_DATASOURCE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
- `SPRING_DATASOURCE_USERNAME` = `${{Postgres.PGUSER}}`
- `SPRING_DATASOURCE_PASSWORD` = `${{Postgres.PGPASSWORD}}`
- `REDIS_URL` = `${{Redis.REDIS_URL}}`
- `JWT_SECRET` = güçlü bir secret (en az 32+ karakter)
- `APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` = frontend staging domaini (ör: `https://<frontend>.up.railway.app`)
- `APP_WEBSOCKET_CANARY_BASE_URL` = backend domaini (ör: `https://<backend>.up.railway.app`)

Alternatif (istersen):

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

uygulama bunları da fallback olarak kabul eder.

Not:
- PostgreSQL ve Redis'i Railway içinde ayrı resource/service olarak ekleyin.
- `Postgres`/`PostgreSQL` service adı workspace'e göre değişebilir. Variable referansında servis adını birebir kullanın (ör. `${{PostgreSQL.PGHOST}}`).
- `services/core-api/src/main/resources/application.yml` dosyası `server.port=${PORT:8080}` olarak Railway portunu dinleyecek şekilde ayarlandı.

### Railpack sessiz crash fallback (önerilen)

Eğer runtime logunda sadece `Starting Container` görüp uygulama logu gelmiyorsa:

- Railway service backend root'u yine `services/core-api` kalsın.
- Repo içinde bulunan `services/core-api/Dockerfile` ile deploy ettirin (Railway Docker build path).

Bu Docker imajı:
- Maven ile backend jar üretir
- runtime'da `PORT` env ile ayağa kaldırır
- entrypoint'i açık olduğu için startup logları deterministik görünür

## 2) Frontend Service (Next.js)

- `New -> Service -> GitHub Repo`
- Repository: `erenulutas0/PaperTrading`
- Service adı: `web-staging` (öneri)
- `Settings -> Source`
- `Root Directory`: `apps/web`

Gerekli environment variables:

- `NEXT_PUBLIC_API_BASE_URL` = backend domaini (ör: `https://<backend>.up.railway.app`)

Opsiyonel:
- `NEXT_PUBLIC_WS_BROKER_URL` = backend domaini (boş bırakılırsa API base'den türetilir)

### Frontend `npm ci` lock mismatch fallback

Eğer Railpack frontend build'i şu hata ile durursa:

- ``npm ci can only install packages when your package.json and package-lock.json are in sync``

Railway frontend service için Docker deploy kullanın:

- Root Directory: `apps/web`
- Dockerfile Path: `Dockerfile`

Repo bu fallback için hazır dosyaları içerir:

- `apps/web/Dockerfile`
- `apps/web/.dockerignore`

## 3) Domainler

Her service için:

- `Settings -> Networking (veya Domains) -> Generate Domain`

Beklenen:

- Backend: `https://<backend>.up.railway.app`
- Frontend: `https://<frontend>.up.railway.app`

## 4) Hızlı Smoke Kontrol

Backend:

- `GET /actuator/health` -> `UP`
- `GET /api/v1/leaderboards?period=1W&page=0&size=10` -> `200`

Frontend:

- login sayfası açılıyor mu
- `/api/*` çağrıları backend'e rewrite oluyor mu
- notification websocket bağlantısı kuruluyor mu
