# Clinora 

🔗 **Live Application:** https://clinora-demo.vercel.app/

Clinora is a clinic management application for operational and clinical staff. It combines a React web application with a Java 21 / Spring Boot modular monolith, PostgreSQL, and a documented local Docker environment. It supports authenticated patient, doctor, appointment, consultation, medical-record, prescription, feedback, communication, and clinician-assistive workflows.

> Current architecture: modular monolith. AI and communication integrations are provider abstractions, not separate microservices.

## Features

- JWT authentication with refresh-token rotation and BCrypt password hashing
- Server-enforced access for `ADMIN`, `DOCTOR`, and `RECEPTIONIST`
- Patient and doctor management, including doctor-user linkage for clinical data scoping
- Appointment scheduling, updates, cancellation, reminders, and PostgreSQL-backed overlap protection
- Appointment-based consultations, completion workflow, and medical records
- Prescription creation, PDF generation, and secure expiring patient prescription links
- Completed-consultation feedback links and staff feedback review
- Communication audit records for appointment confirmations/reminders, prescription availability, consultation completion, and feedback requests
- AI-assisted consultation drafts and patient-history summaries for authorized clinical users
- REST API, local OpenAPI/Swagger, Docker Compose, Testcontainers, and GitHub Actions verification

## Architecture

```text
Browser (React + TypeScript + Vite)
              |
              v
Spring Boot modular monolith
  |- REST controllers and security filters
  |- domain services and repositories
  |- AI provider abstraction
  `- communication provider abstraction
              |
              v
PostgreSQL + Flyway migrations
```

The frontend calls the backend over HTTP. Controllers validate and authorize requests, services apply domain rules, and Spring Data JPA persists data in PostgreSQL. The backend is not split into authentication, appointment, or notification microservices.

See [architecture details](docs/architecture.md) for modules, data relationships, migrations, and scheduling semantics.

## Technology Stack

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.0.8, Spring Security 7.0.7, Spring Data JPA, Hibernate |
| API and docs | REST, Bean Validation, springdoc-openapi 3.0.3 |
| Database | PostgreSQL 18, Flyway V1-V10 |
| Frontend | React 19, TypeScript 5.7, React Router 7, Vite 8.2 |
| Testing | JUnit, Mockito, Spring Boot Test, Testcontainers, Vitest, Testing Library |
| Local tooling | Maven, npm, Docker Compose, GitHub Actions |

Redis, RabbitMQ, Kafka, Kubernetes, AWS deployment, and separate microservices are not current infrastructure.

## Frontend Experience

The React application uses protected routes and a shared authenticated layout. It provides dashboard, patient, doctor, appointment, consultation, medical-record, prescription, patient-access, and feedback views.

- The API client attaches the active Bearer token, attempts one eligible refresh after a `401`, and clears the local session when refresh cannot recover it.
- Role-aware controls improve usability, while the backend performs the authoritative authorization decision.
- List screens provide loading, empty, validation, and API-error states; clinical actions remain available only to permitted roles.
- AI drafts remain separate from editable consultation fields until an authorized clinician explicitly applies and saves them.

The local Vite server defaults to `http://localhost:5173` and calls the local backend at `http://localhost:8080`. A production frontend build requires either a same-origin `/` API base or an explicit non-local HTTPS API URL.

## Project Structure

```text
backend/           Spring Boot application, migrations, tests, and Dockerfile
frontend/          React application, API client, routes, components, and tests
docs/              Architecture, API, security, development, and deployment guides
scripts/           Release, dependency-audit, and security-smoke helpers
.github/           GitHub Actions verification workflow
docker-compose.yml Local PostgreSQL and backend environment
README.md          Project overview and quick start
```

## Quick Start

**Prerequisites:** Docker Desktop/Compose, Node.js 22+ with npm, and Java 21 with Maven for direct backend work.

1. Create a local environment file from the template. Do not commit it.

   ```powershell
   Copy-Item .env.example .env
   ```

2. Replace the local `DATABASE_PASSWORD` and `JWT_SECRET` placeholders in `.env`.

3. Start PostgreSQL and the backend.

   ```powershell
   docker compose up -d --build
   ```

4. Start the frontend in another terminal.

   ```powershell
   Set-Location frontend
   npm ci
   npm run dev
   ```

5. Open <http://localhost:5173>. Local backend endpoints include:

   - Health: <http://localhost:8080/api/v1/health>
   - Actuator health: <http://localhost:8080/actuator/health>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>

For direct backend development, start PostgreSQL first and run `mvn spring-boot:run` from `backend/` with the required local environment variables.

## Environment Configuration

[`.env.example`](.env.example) is the local configuration template. It contains placeholders only; `.env` is ignored by Git and must never be committed.

For a standard local setup, configure at least:

```dotenv
DATABASE_PASSWORD=replace_with_a_local_password
JWT_SECRET=replace_with_a_local_secret_of_at_least_32_bytes
```

Local Compose defaults use `SPRING_PROFILES_ACTIVE=local`, mock AI, and the NoOp SMS provider. Synthetic demo data is disabled by default and should only be enabled intentionally for local demonstration.

The `prod` profile requires explicit database, JWT, CORS, frontend, feedback, and clinic-time-zone configuration. It does not silently fall back to localhost; it disables demo data, Swagger, AI, and SMS by default. See the [development guide](docs/development.md) and [production deployment guide](docs/production-deployment.md).

## Live Demo

Demo: `https://demo.<DOMAIN>`

The public demo is a separate, resettable deployment backed only by synthetic data. Its dedicated account is `demo-admin@clinora.app`; the disposable public password is configured at deployment time and displayed only by the demo frontend. The demo uses the real configured Gemini-compatible provider, never the mock provider, while SMS remains disabled.

The demo database, volume, database credentials, JWT secret, admin password, and AI key must be distinct from production. See [public demo deployment](docs/demo.md) for the required environment, reverse-proxy layout, AI limits, and safe reset command.

## Authentication and Authorization

Authentication is implemented inside the modular monolith. It is not currently a separate authentication service.

- Access tokens are signed JWT Bearer tokens.
- Refresh tokens are hashed at rest, rotated on use, and revoked on logout.
- Passwords are BCrypt-hashed and validated before persistence.
- Staff registration is ADMIN-only. A one-time bootstrap administrator can be configured for a new deployment and then disabled.
- Authorization is enforced on the backend, not only hidden in the UI.

| Role | Primary access |
|---|---|
| `ADMIN` | Broad operational and clinical access; manages staff, doctors, and communications |
| `DOCTOR` | Doctor-scoped patients, appointments, consultations, medical records, prescriptions, and AI assistance |
| `RECEPTIONIST` | Operational patient, doctor-read, and appointment workflows; no clinical-record or prescription access |

Public prescription-PDF and feedback routes use separate expiring, revocable, hashed access tokens rather than patient accounts. See the [security guide](docs/security.md) for the detailed authorization model.

## Core Workflows

**Patients and doctors** are listed and managed through protected REST endpoints. Doctor records can be linked to staff users, which gives the backend the relationship required to scope doctor clinical access.

**Appointments** connect a patient and doctor. Active appointments are protected by PostgreSQL exclusion constraints so concurrent requests cannot create overlapping 30-minute patient or doctor slots. Cancelling an appointment is a domain operation rather than a physical deletion.

**Clinical workflows** begin from eligible appointments. Authorized clinical users can start, update, view, and complete consultations, create or retrieve medical records, and create/retrieve prescriptions. PDFs are generated on demand for authorized staff.

**Patient access and feedback** use purpose-specific token links. A prescription token can retrieve only its associated PDF; a feedback token can retrieve and submit feedback for only its associated completed consultation.

## AI Clinical Assistant

Clinora can generate structured consultation drafts and patient-history summaries for authorized admins and doctors. AI output is advisory: it is not automatically written to a consultation, medical record, prescription, or other clinical record. A clinician must review, apply or edit, and explicitly save any information.

- Local development and automated tests use the deterministic `mock` provider.
- An OpenAI-compatible provider abstraction is available for explicitly configured external providers.
- Provider keys remain server-side; no AI key belongs in frontend code or source control.
- A failed real-provider request returns an unavailable response; it does not fall back to fabricated mock clinical content.

**Production status:** **AI is disabled in the production profile pending successful verification of the configured external provider.**

See [AI and communications](docs/ai-and-communications.md) for endpoints, configuration, retry behavior, and clinical-safety boundaries.

## Communications and SMS

The communication domain records privacy-conscious SMS communication attempts for appointment confirmations and reminders, prescription availability, consultation completion, and feedback requests.

- Local development uses `NoOpSmsCommunicationProvider`; messages are recorded as `SIMULATED`, not vendor-delivered.
- With SMS disabled, records are `DISABLED` and no provider is called.
- Production defaults SMS to disabled. A real provider is not implemented or verified yet.
- Communication dispatch is best-effort after the related clinical transaction commits; it is not a durable queue.

## Security

Clinora includes stateless security controls appropriate to its current architecture:

- JWT validation, refresh-token rotation/revocation, BCrypt passwords, and server-side RBAC
- Doctor-scoped clinical access checks, request validation, and sanitized JSON errors
- Explicit CORS origins with no wildcard configuration or credentialed CORS
- Security headers, protected actuator endpoints, loopback-only local Compose port bindings, and a bounded in-process abuse-control filter
- Flyway schema validation, no ORM schema generation, and environment-based secret configuration

The browser stores tokens in local storage, so protecting the frontend against untrusted scripts remains important. The local Compose file is for development, not a complete production deployment. See [security details](docs/security.md).

## API

The backend exposes versioned REST endpoints under `/api/v1`, including authentication, patients, doctors, appointments, consultations, medical records, prescriptions, patient-access links, feedback, communications, and AI assistance.

OpenAPI is available locally at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. Both are disabled by the production profile. See the [API reference](docs/api.md) for endpoint groups and access requirements.

## Testing

Run backend tests from `backend/`:

```powershell
mvn test
mvn package -DskipTests
```

Run frontend tests and a production build from `frontend/`:

```powershell
npm run test
$env:VITE_API_BASE_URL = "/"
npm run build
```

Backend integration tests use Testcontainers PostgreSQL when Docker is available. The GitHub Actions workflow runs backend verification, requires the database integration test to execute, builds/tests the frontend, and runs dependency audits. It does not deploy the application.

## Docker

`docker compose up -d --build` starts the Spring Boot backend and PostgreSQL for local development. PostgreSQL data is stored in a named Docker volume; both services have health checks and the backend waits for PostgreSQL health before starting.

The Compose ports bind to loopback by default. Do not use `docker compose down -v` unless intentionally discarding local data. Production deployment requires external TLS termination, explicit production configuration, secret management, backups, and operational controls described in [production deployment](docs/production-deployment.md).

## Local Operations

Inspect the local services and backend logs with:

```powershell
docker compose ps
docker compose logs --tail 200 backend
```

To rebuild only the backend while preserving the PostgreSQL volume:

```powershell
docker compose build backend
docker compose up -d --no-deps backend
```

Use the health endpoints after a restart and let Flyway validate the existing schema. The repository's [development guide](docs/development.md) documents test, release, dependency-audit, and security-smoke commands.

## Continuous Integration

The committed [GitHub Actions workflow](.github/workflows/verify.yml) runs on pushes and pull requests. It builds and verifies the Maven project, requires the PostgreSQL integration test to run, executes release/dependency checks, installs locked frontend dependencies, runs frontend tests, builds the frontend, and runs `npm audit`.

The workflow verifies code; it does not deploy the application, provision cloud infrastructure, or validate external AI/SMS vendors.

## Production Status

| Area | Current status |
|---|---|
| Core patient-management workflows | Implemented and covered by backend/frontend tests |
| Authentication and RBAC | Implemented in the modular monolith |
| Database and migrations | PostgreSQL with Flyway V1-V10 |
| Local Docker environment | Implemented with backend/PostgreSQL health checks and persistent volume |
| CI | GitHub Actions verification workflow present; no deployment workflow |
| AI | Available locally with mock provider; disabled in production pending provider verification |
| SMS | Simulated locally with NoOp provider; disabled in production pending a real provider |
| Production deployment | Not included; deployment infrastructure, TLS, and external-provider acceptance remain operator work |

## Current Limitations

- No patient account, patient role, or general patient portal; patient-facing access is limited to purpose-specific prescription and feedback links.
- No real SMS provider or confirmed handset-delivery integration.
- No production-enabled AI provider until external provider access and clinical/privacy review are completed.
- No cloud deployment, Redis, RabbitMQ, Kafka, Kubernetes, or separate microservices.
- Communication delivery is best-effort, not durable queue processing.
- Multi-clinic and multi-time-zone scheduling are not supported.

## Documentation

- [Architecture and data model](docs/architecture.md)
- [Development and local operations](docs/development.md)
- [Public demo deployment](docs/demo.md)
- [API reference](docs/api.md)
- [Security and authorization](docs/security.md)
- [AI and communications](docs/ai-and-communications.md)
- [Production deployment and release gates](docs/production-deployment.md)
- [Remediation evidence](docs/remediation-report.md)

## License

No license file is currently included in this repository.
