# Public Demo Deployment

## Purpose and Boundary

The public demo is the same Clinora modular monolith running with a disposable synthetic dataset. It is not connected to production and must never share production credentials, database, volume, JWT secret, demo password, or AI key.

```text
https://demo.<DOMAIN>
        |
  TLS reverse proxy
        |
  static demo frontend + Clinora demo backend
        |
  clinora_demo PostgreSQL
        |
  configured Gemini-compatible provider
```

The reverse proxy terminates HTTPS and keeps PostgreSQL private. The frontend must use an explicit `VITE_API_BASE_URL` for the demo origin; it never calls Gemini directly.

## Demo Services

`docker-compose.demo.yml` creates only these resources:

- PostgreSQL database: `clinora_demo`
- PostgreSQL user: `clinora_demo`
- Named volume: `clinora_demo_postgres_data`
- Container names: `clinora-demo-postgres` and `clinora-demo-backend`
- Backend loopback binding: `127.0.0.1:18080` by default

PostgreSQL has no host port. Do not use the normal local Compose database or production database for this deployment.

## Configuration

Copy `.env.demo.example` to an ignored `.env.demo` file and set unique demo-only values:

- `DEMO_DATABASE_PASSWORD`
- `DEMO_JWT_SECRET`
- `DEMO_ADMIN_PASSWORD`
- `DEMO_AI_API_KEY`
- `DEMO_FRONTEND_URL` with the HTTPS public demo origin

The seed creates `demo-admin@clinora.app` with the configured demo password and the existing `ADMIN` role. The password is intentionally disposable/public for the demo but must never be used outside it.

The `demo` Spring profile enforces:

- `DEMO_MODE=true`
- seeded synthetic data enabled
- `clinora_demo` database and user only
- real OpenAI-compatible AI provider with a non-empty dedicated key
- demo AI protection enabled
- SMS disabled

Any violation fails startup. The seed is idempotent and creates only fictional data.

## Real Gemini AI

The demo uses the existing `AiProvider` and `OpenAiProvider` implementation:

```dotenv
AI_ENABLED=true
AI_PROVIDER=openai
AI_MODEL=gemini-3.8-flash
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
DEMO_AI_API_KEY=replace_with_a_dedicated_demo_key
```

The provider key remains server-side. The browser calls Clinora; Clinora calls the configured provider. AI requests are limited to the existing consultation-draft and patient-history-summary endpoints. AI output is advisory and is not saved automatically.

Demo protection defaults are intentionally conservative: three requests per demo administrator per minute, one concurrent request, 1,800 input characters, and 1,600 output characters. The regular application validation still rejects oversized request fields. The provider uses bounded exponential-backoff retries for `503` and clearly transient `429` responses; quota-exhaustion `429`, authentication, malformed, and other client errors are not retried or replaced with mock output.

## Build and Run

Build the public frontend with a separately supplied environment file:

```powershell
Set-Location frontend
Copy-Item .env.demo.example .env.demo
# Set VITE_DEMO_PASSWORD to the same disposable public password.
npm ci
npm run build -- --mode demo
```

Start the demo backend and database:

```powershell
docker compose --env-file .env.demo -f docker-compose.demo.yml -p clinora-demo up -d --build
docker compose --env-file .env.demo -f docker-compose.demo.yml -p clinora-demo ps
```

Serve the static frontend through a TLS reverse proxy at `https://demo.<DOMAIN>`. Route the demo API to the loopback-bound demo backend, preserve the `Authorization` header, and do not expose PostgreSQL or port 8080 publicly.

## Reset Procedure

An operator, not a public endpoint, resets the demo. The scripts validate `DEMO_MODE`, the exact demo database name/user, and the dedicated Compose project before removing only the demo volume.

```powershell
./scripts/reset-demo.ps1 -EnvFile .env.demo
```

```sh
./scripts/reset-demo.sh .env.demo
```

The reset recreates only the `clinora-demo` Compose project and `clinora_demo_postgres_data`, starts the backend, runs Flyway through normal startup, reseeds synthetic data, and waits for actuator health. It must never be pointed at production.

## Verification Checklist

Use the seeded demo administrator to verify normal JWT login, dashboard, patient/doctor/appointment workflows, consultation, medical records, prescriptions/PDF, prescription access, feedback, communications, AI draft, AI summary, and logout. A real provider response must be verified using synthetic input before claiming live AI success. If the provider returns `429`, `503`, timeout, or authentication failure, keep the demo available for non-AI workflows and report the external provider limitation without mock fallback.

SMS stays disabled in this deployment. `SIMULATED`, `DISABLED`, and `FAILED` communication states must never be presented as real SMS delivery.
