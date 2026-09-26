# Development Guide

## Prerequisites

- Docker Desktop with Docker Compose
- Node.js 22+ and npm for the frontend
- Java 21 and Maven for direct backend development

## Local Configuration

Create a local `.env` from the root template:

```powershell
Copy-Item .env.example .env
```

Set unique local values for `DATABASE_PASSWORD` and `JWT_SECRET`. The template documents the remaining optional local settings. Never commit `.env`, copied credentials, database dumps, or generated build output.

The `local` profile permits localhost development defaults. The `prod` profile requires explicit production values and should not be used with the Compose defaults without following the [production deployment guide](production-deployment.md).

## Start the Local Stack

```powershell
docker compose up -d --build
docker compose ps
```

The backend is available at `http://localhost:8080`; PostgreSQL is loopback-bound at `localhost:5432`; the frontend development server uses `http://localhost:5173`.

Start the frontend separately:

```powershell
Set-Location frontend
npm ci
npm run dev
```

For direct backend execution, start PostgreSQL and then run:

```powershell
Set-Location backend
mvn spring-boot:run
```

## Health and API Documentation

- `GET /api/v1/health`
- `GET /actuator/health`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`

OpenAPI and Swagger are local-development tools. The production profile disables them.

## Demo Data

Demo data is development-only, synthetic, and disabled by default. Set `DEMO_DATA_ENABLED=true` and a non-empty `DEMO_DATA_PASSWORD` only when intentionally preparing a local demonstration database. Disable it after seeding; do not use demo seeding in production.

## Tests and Quality Gates

Backend:

```powershell
Set-Location backend
mvn test
mvn package -DskipTests
```

Frontend:

```powershell
Set-Location frontend
npm run test
$env:VITE_API_BASE_URL = "/"
npm run build
npm audit
```

Database integration tests use Testcontainers PostgreSQL when Docker is available. The repository also includes:

```powershell
./scripts/check-release.ps1 -RequireTracked
./scripts/dependency-audit.ps1
./scripts/security-smoke-test.ps1
```

The security smoke script is read-only against the running stack and can use optional `SMOKE_TEST_EMAIL` and `SMOKE_TEST_PASSWORD` values for an authenticated protected-read check. It does not print credentials or tokens.

## Local Docker Data Safety

The PostgreSQL named volume preserves local data across backend/container recreation. Rebuild only the backend when needed:

```powershell
docker compose build backend
docker compose up -d --no-deps backend
```

Do not run `docker compose down -v` unless intentionally discarding the local database.
