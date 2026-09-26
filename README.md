# Clinora

Clinora is a Modern Clinic Management Platform being built incrementally as a production-oriented healthcare application. The repository currently contains the modular-monolith backend, role-aware React frontend, clinical workflows, secure patient access, communication foundations, and clinician-assistive AI integration.

## Current Architecture Status

The current application consists of a Maven-based Spring Boot backend under `backend/` and a React/Vite frontend under `frontend/`.

Implemented now:

- Spring Boot backend foundation
- Domain-oriented package structure
- Patient domain APIs and persistence
- Doctor domain APIs and persistence
- Medical record APIs and persistence
- Prescription and prescription item APIs and persistence
- Appointment APIs and persistence
- Patient communication foundation with persistent communication audit records
- Environment-driven PostgreSQL configuration
- Flyway V1-V8 domain/security schema, V9 explicit SMS simulation states, and V10 appointment exclusion constraints
- JPA auditing for created and updated timestamps
- Global exception handling with validation, 404, and 409 responses
- Spring Boot Actuator health/info exposure
- Minimal application health API
- OpenAPI/Swagger documentation
- Spring Security authentication and role-based authorization
- JWT access tokens
- PostgreSQL-backed refresh tokens stored as hashes
- BCrypt password hashing
- Configurable CORS for future frontend development
- Docker-based local development environment
- Docker Compose orchestration for backend and PostgreSQL
- Global pagination defaults and maximum page size
- Sanitized API error responses for common client and security errors
- Unit tests, controller tests, and Testcontainers PostgreSQL integration coverage
- React, TypeScript, and Vite frontend foundation
- Frontend login, registration, protected dashboard, logout, route protection, role-aware navigation, patient/doctor/appointment CRUD workflows, medical record workflow, and prescription workflow
- Frontend audit and modern UI/UX polish for the existing authenticated application shell and implemented pages
- Consultation management workflow for appointment-based clinical encounters
- Step 19 patient communication foundation for future SMS, prescription delivery, reminders, and feedback workflows
- Step 20 Docker runtime activation of the communication foundation with Flyway V4 applied against the preserved local PostgreSQL volume
- Step 21 appointment confirmation SMS workflow using the existing no-op communication provider
- Step 22 appointment reminder communication workflow using the existing no-op communication provider
- Step 23 prescription available SMS workflow using the existing no-op communication provider
- Merged Step 25-26 AI-assisted clinical documentation and patient history summarization using a local mock AI provider
- Step 28 on-demand prescription PDF generation for authorized clinical users
- Step 29 secure prescription-specific patient access links backed by hashed, expiring, revocable tokens
- Step 30 patient feedback/rating workflow for completed consultations using hashed, expiring, revocable feedback tokens
- Step 31 real AI/LLM provider integration behind the existing AI provider abstraction
- Step 32 real SMS provider preparation while keeping the no-op SMS provider as the only active provider
- Step 33 backend-enforced doctor data scoping for clinical resources through doctor-user linkage and appointment relationships

Not implemented yet:

- Authentication service
- Notification service
- Real SMS provider integration
- Patient login/portal
- API gateway
- Redis
- RabbitMQ
- AWS deployment

## Technology Stack

Current backend:

- Java 21
- Spring Boot 4.0.8 (Java 21), Spring Security 7.0.7, Tomcat 11.0.26
- Maven
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL driver
- Bean Validation
- Spring Boot Actuator
- Spring Security
- Flyway
- springdoc-openapi
- Nimbus JOSE/JWT
- Docker
- Docker Compose
- JUnit
- Mockito
- Spring Boot Test
- Testcontainers

Current frontend:

- React
- TypeScript
- Vite
- React Router
- Fetch API
- Vitest
- Testing Library

Planned future platform stack:

- Tailwind CSS, shadcn/ui
- Redis, RabbitMQ
- GitHub Actions
- AWS

## Development Prerequisites

- Docker
- Docker Compose

Optional, for running the backend or tests outside containers:

- Java 21
- Maven 3.9+
- Node.js
- npm

Manual PostgreSQL installation is not required for the Docker Compose workflow.

## Environment Variables

The backend reads database and security settings from environment variables. Use `.env.example` for Docker Compose overrides and `backend/.env.example` when running the backend directly from your shell.

The frontend reads its backend base URL from `VITE_API_BASE_URL`. Use `frontend/.env.example` for local frontend configuration.

Do not commit a real `.env` file.

Docker Compose requires a local `.env` file containing a unique database password and a JWT signing secret. This prevents a copied Compose configuration from starting with known credentials or a predictable signing key.

```bash
cp .env.example .env
# Set DATABASE_PASSWORD and JWT_SECRET to unique local values in .env.
docker compose up --build
```

For local Docker development, copy `.env.example` to `.env` and replace these placeholders before starting the stack:

```text
POSTGRES_DB=patient_management
DATABASE_URL=jdbc:postgresql://postgres:5432/patient_management
DATABASE_USERNAME=patient_management
DATABASE_PASSWORD=replace_with_local_development_password
JWT_SECRET=replace_with_local_development_jwt_secret_at_least_32_bytes
JWT_ACCESS_TOKEN_EXPIRATION=900
JWT_REFRESH_TOKEN_EXPIRATION=604800
CORS_ALLOWED_ORIGINS=http://localhost:5173
PAGEABLE_DEFAULT_SIZE=20
PAGEABLE_MAX_SIZE=100
APPOINTMENT_REMINDERS_ENABLED=true
APPOINTMENT_REMINDER_LOOK_AHEAD_HOURS=24
APPOINTMENT_REMINDER_FIXED_DELAY_MS=900000
APPOINTMENT_REMINDER_INITIAL_DELAY_MS=60000
SMS_ENABLED=true
SMS_PROVIDER=noop
SMS_SENDER=PMCLINIC
SMS_TIMEOUT_SECONDS=15
SMS_TEMPLATE_APPOINTMENT_CONFIRMATION=
SMS_TEMPLATE_APPOINTMENT_REMINDER=
SMS_TEMPLATE_CONSULTATION_COMPLETED=
SMS_TEMPLATE_PRESCRIPTION_AVAILABLE=
SMS_TEMPLATE_FEEDBACK_REQUEST=
AI_ENABLED=true
AI_PROVIDER=mock
AI_MODEL=mock-clinical-assistant-v1
AI_TIMEOUT_SECONDS=15
AI_API_KEY=
AI_BASE_URL=https://api.openai.com/v1/chat/completions
PRESCRIPTION_ACCESS_TOKEN_EXPIRATION_HOURS=168
FEEDBACK_ACCESS_TOKEN_EXPIRATION_HOURS=168
FEEDBACK_PUBLIC_BASE_URL=http://localhost:5173
DEMO_DATA_ENABLED=false
DEMO_DATA_PASSWORD=
```

For direct JVM runtime outside Docker, use the host PostgreSQL address:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/patient_management
DATABASE_USERNAME=patient_management
DATABASE_PASSWORD=replace_with_local_password
JWT_SECRET=replace_with_strong_local_development_secret_at_least_32_bytes
JWT_ACCESS_TOKEN_EXPIRATION=900
JWT_REFRESH_TOKEN_EXPIRATION=604800
CORS_ALLOWED_ORIGINS=http://localhost:5173
PAGEABLE_DEFAULT_SIZE=20
PAGEABLE_MAX_SIZE=100
APPOINTMENT_REMINDERS_ENABLED=true
APPOINTMENT_REMINDER_LOOK_AHEAD_HOURS=24
APPOINTMENT_REMINDER_FIXED_DELAY_MS=900000
APPOINTMENT_REMINDER_INITIAL_DELAY_MS=60000
SMS_ENABLED=true
SMS_PROVIDER=noop
SMS_SENDER=PMCLINIC
SMS_TIMEOUT_SECONDS=15
SMS_TEMPLATE_APPOINTMENT_CONFIRMATION=
SMS_TEMPLATE_APPOINTMENT_REMINDER=
SMS_TEMPLATE_CONSULTATION_COMPLETED=
SMS_TEMPLATE_PRESCRIPTION_AVAILABLE=
SMS_TEMPLATE_FEEDBACK_REQUEST=
AI_ENABLED=true
AI_PROVIDER=mock
AI_MODEL=mock-clinical-assistant-v1
AI_TIMEOUT_SECONDS=15
AI_API_KEY=
AI_BASE_URL=https://api.openai.com/v1/chat/completions
PRESCRIPTION_ACCESS_TOKEN_EXPIRATION_HOURS=168
FEEDBACK_ACCESS_TOKEN_EXPIRATION_HOURS=168
FEEDBACK_PUBLIC_BASE_URL=http://localhost:5173
DEMO_DATA_ENABLED=false
DEMO_DATA_PASSWORD=
```

Optional:

```text
SERVER_PORT=8080
```

Frontend:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Inside Docker, the backend must connect to PostgreSQL through the Compose service name `postgres`, not `localhost`.

## Local Development With Docker

Start the complete local environment from the repository root:

```bash
docker compose up --build
```

This starts:

- `postgres`: PostgreSQL 18 Alpine with a persistent named volume
- `backend`: Spring Boot API built from `backend/Dockerfile`

Application URLs:

```text
API base: http://localhost:8080
Swagger:  http://localhost:8080/swagger-ui/index.html
Health:   http://localhost:8080/actuator/health
Liveness: http://localhost:8080/actuator/health/liveness
Readiness: http://localhost:8080/actuator/health/readiness
App health: http://localhost:8080/api/v1/health
```

Stop containers while keeping the database volume:

```bash
docker compose down
```

Stop containers and delete the local database volume:

```bash
docker compose down -v
```

The named volume is `postgres_data`. It survives `docker compose down` and is removed by `docker compose down -v`.

View logs:

```bash
docker compose logs -f backend
docker compose logs -f postgres
```

Rebuild without cache:

```bash
docker compose build --no-cache
```

Validate Compose configuration:

```bash
docker compose config
```

## Database Access

PostgreSQL is exposed to the host on `localhost:5432` for local inspection. The application container still uses `postgres:5432` internally.

Connect with the PostgreSQL client inside the container:

```bash
docker compose exec postgres psql -U patient_management -d patient_management
```

List tables:

```sql
\dt
```

Inspect Flyway history:

```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

## Development Demo Data

Demo data is development-only and disabled by default. It provides an idempotent fictional clinic dataset for local workflow inspection: six role-based users, four doctors, ten patients, eighteen appointments, six consultations, six medical records, four prescriptions, communication history, and four feedback records.

Enable it only for a local run. Set a unique local-only value for `DEMO_DATA_PASSWORD`; the seed will refuse to run without it.

```bash
DEMO_DATA_ENABLED=true DEMO_DATA_PASSWORD="$DEMO_DATA_PASSWORD" docker compose up -d --build backend
```

On Windows PowerShell:

```powershell
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_DATA_PASSWORD = "<set-a-unique-local-password>"
docker compose up -d --build backend
```

After the backend starts and reports the seed completion in the logs, disable it again before future runs:

```powershell
Remove-Item Env:\DEMO_DATA_ENABLED
Remove-Item Env:\DEMO_DATA_PASSWORD
docker compose up -d backend
```

The seed is idempotent. Re-running it updates only records identified by the seed's fixed synthetic emails, legacy synthetic license IDs, or internal ownership markers, and normalizes demo-account credentials to the explicitly configured local-only password. It does not select unrelated records for updates. Do not enable the seed outside an isolated local environment.

Seeded demo records include:

- Patients across pediatric, adult, and older-adult age groups
- Doctors in internal medicine, family medicine, cardiology, and pediatrics
- Past, current-day, and upcoming appointments with varied statuses
- In-progress and completed consultations
- Medical records, prescriptions with multiple medicines, communication history, and patient feedback

Manual demo workflow:

1. Log in as `admin.demo@example.com` and verify patients, doctors, appointments, prescriptions, medical records, and communication history.
2. Log in as `receptionist.demo@example.com` and verify patient and appointment workflows; clinical consultation and AI actions should remain unavailable.
3. Log in as `doctor.demo@example.com`, open Appointments, and use Asha Rao's active consultation appointment.
4. Open the consultation panel, enter current synthetic rough notes, generate an AI draft, review it, apply it, and explicitly save.
5. Open Patients, use Asha Rao, and generate the AI patient history summary from the existing appointment, consultation, medical record, and prescription history.

Do not enable demo seeding in shared or production environments. The demo accounts and records are synthetic local development data only.

## Run Backend Directly

From the backend directory:

```bash
cd backend
mvn spring-boot:run
```

The Maven Spring Boot run configuration activates the `local` profile automatically. That profile uses local-only defaults matching Docker Compose, so PostgreSQL should be reachable at `localhost:5432` with the local development database/user unless you override them through environment variables. Flyway creates the current core, auth, appointment, and communication schemas.

## Run Frontend Locally

From the frontend directory:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on:

```text
http://localhost:5173
```

The backend CORS configuration already allows `http://localhost:5173` by default.

## Run Tests

From the backend directory:

```bash
mvn test
```

The service and controller tests do not require a manually installed PostgreSQL instance. The PostgreSQL integration test uses Testcontainers and is skipped when Docker is not available.

With Docker available, the Testcontainers-backed integration tests start PostgreSQL automatically and verify the Flyway-managed schema.

From the frontend directory:

```bash
npm run test
npm run build
```

## Docker Image

`backend/Dockerfile` uses a multi-stage build:

- Builder: `maven:3.9.9-eclipse-temurin-21-alpine`
- Runtime: `eclipse-temurin:21-jre-alpine`

The runtime image contains only the built Spring Boot JAR and runs as a non-root `app` user. Maven is not present in the runtime stage.

## Docker Security Notes

The Docker setup is for reproducible local development, not a complete production deployment architecture. The backend image runs as a non-root user, exposes only port `8080`, and receives database/JWT/CORS configuration through environment variables. PostgreSQL exposes `5432` to the host to make local development and inspection straightforward.

Do not use the sample local passwords or JWT secret in shared environments. Override them through a local `.env` file or your shell.

## Security Smoke Test

Run the local, read-only security/runtime smoke test from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\security-smoke-test.ps1
```

The tool does not modify containers, database data, or test data, and it never prints credentials or tokens. It can use optional `SMOKE_TEST_EMAIL` and `SMOKE_TEST_PASSWORD` environment variables for an authenticated read-only check; without them, that check is skipped. `PASS WITH WARNINGS` is normal when optional local services or safe test credentials are unavailable.

## Troubleshooting

If the backend is unhealthy, check logs:

```bash
docker compose logs -f backend
```

If PostgreSQL is unhealthy, check logs:

```bash
docker compose logs -f postgres
```

If Flyway or JPA validation fails after schema changes, reset the local development database:

```bash
docker compose down -v
docker compose up --build
```

## Current API

Health:

```http
GET /api/v1/health
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/info
```

Authentication:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

Patients:

```http
POST   /api/v1/patients
GET    /api/v1/patients
GET    /api/v1/patients/{id}
PUT    /api/v1/patients/{id}
DELETE /api/v1/patients/{id}
```

Doctors:

```http
POST   /api/v1/doctors
GET    /api/v1/doctors
GET    /api/v1/doctors/{id}
PUT    /api/v1/doctors/{id}
DELETE /api/v1/doctors/{id}
```

Medical records:

```http
POST /api/v1/medical-records
GET  /api/v1/medical-records/{id}
GET  /api/v1/patients/{patientId}/medical-records
```

Prescriptions:

```http
POST /api/v1/prescriptions
GET  /api/v1/prescriptions/{id}
GET  /api/v1/prescriptions/{id}/pdf
POST /api/v1/prescriptions/{id}/access
DELETE /api/v1/prescriptions/{id}/access
GET  /api/v1/patients/{patientId}/prescriptions
```

Patient prescription access:

```http
GET /api/v1/prescription-access/{token}/pdf
```

Feedback:

```http
POST   /api/v1/consultations/{consultationId}/feedback-access
DELETE /api/v1/consultations/{consultationId}/feedback-access
GET    /api/v1/feedback-access/{token}
POST   /api/v1/feedback-access/{token}
GET    /api/v1/feedback
```

Consultations:

```http
POST /api/v1/appointments/{appointmentId}/consultation
GET  /api/v1/appointments/{appointmentId}/consultation
GET  /api/v1/consultations/{id}
PUT  /api/v1/consultations/{id}
POST /api/v1/consultations/{id}/complete
GET  /api/v1/patients/{patientId}/consultations
```

AI clinical assistant:

```http
POST /api/v1/consultations/{consultationId}/ai/draft
GET  /api/v1/patients/{patientId}/ai/summary
```

Appointments:

```http
POST   /api/v1/appointments
GET    /api/v1/appointments
GET    /api/v1/appointments/{id}
PUT    /api/v1/appointments/{id}
DELETE /api/v1/appointments/{id}
```

Communications:

```http
GET /api/v1/communications
GET /api/v1/communications/{id}
```

List endpoints use a default page size of 20 and cap requested page sizes at 100 unless overridden through environment configuration. Unsupported sort fields return `400 Bad Request`.

Supported sort fields:

| Area | Sort fields |
| --- | --- |
| Patients | `createdAt`, `updatedAt`, `firstName`, `lastName`, `dateOfBirth`, `email` |
| Doctors | `createdAt`, `updatedAt`, `firstName`, `lastName`, `specialization`, `licenseNumber`, `department` |
| Medical records | `recordDate`, `createdAt`, `updatedAt`, `diagnosis` |
| Prescriptions | `prescriptionDate`, `createdAt`, `updatedAt` |
| Consultations | `startedAt`, `completedAt`, `createdAt`, `updatedAt`, `status` |
| Appointments | `appointmentDateTime`, `status`, `createdAt`, `updatedAt` |
| Communications | `createdAt`, `updatedAt`, `type`, `channel`, `status`, `sentAt`, `deliveredAt` |

Appointment listing supports optional filters for `patientId`, `doctorId`, `status`, `from`, and `to`.
`DELETE /api/v1/appointments/{id}` cancels the appointment instead of physically deleting it.

OpenAPI and Swagger:

```http
GET /v3/api-docs
GET /swagger-ui.html
```

Business APIs require `Authorization: Bearer <accessToken>`. Health endpoints and auth login/refresh are public. Staff registration requires ADMIN. Swagger/OpenAPI is accessible locally and disabled in the production profile.

Communication history APIs are admin-only. There is no public or frontend API for sending arbitrary SMS messages.

## Current Frontend

The frontend currently provides:

- `GET /login` client route for authentication
- `GET /register` ADMIN-only staff provisioning route
- `GET /dashboard` protected client route
- `GET /patients` protected client route with patient list/create/update/delete workflow
- `GET /doctors` protected client route with doctor list/create/update/delete workflow
- `GET /appointments` protected client route with appointment list/create/update/cancel workflow
- `GET /medical-records` protected client route with medical record list-by-patient/create/detail workflow
- `GET /prescriptions` protected client route with prescription list-by-patient/create/detail/PDF and patient-access-link workflow
- `GET /prescription-access/:token` purpose-specific public client route for retrieving a prescription PDF by secure access token
- Inline consultation workflow from eligible appointments for admin and doctor users
- AI consultation note draft generation inside the consultation workflow for admin and doctor users
- AI patient history summary generation from the patients screen for admin and doctor users
- Authenticated application layout
- Logout action
- Role-aware navigation for the implemented backend domains
- Shared visual language for forms, tables, state messages, buttons, and destructive confirmations
- Centralized API client using `VITE_API_BASE_URL`
- Centralized authentication state and token handling

The frontend implements patient, doctor, and appointment CRUD using the existing backend APIs. Appointment deletion uses the backend cancellation endpoint, so appointments are cancelled rather than physically removed. Admin and doctor users can start, save, view, and complete consultations from the appointments screen when an appointment is eligible. Admin and doctor users can generate an AI consultation draft from rough notes, review the draft, apply it to the editable form, and then explicitly save. Admin and doctor users can also generate an AI patient history summary from the patients screen. The medical record screen uses the existing backend APIs to list records by patient, create records, and retrieve details. The prescription screen uses the existing backend APIs to list prescriptions by patient, create prescriptions with medicine items, retrieve details, open/download generated prescription PDFs, create a patient access link, copy that link, and revoke active patient access. Medical record and prescription edit/delete screens are not implemented because the backend does not currently expose those operations.

Step 19 does not add a communication frontend. Communication records are backend-only foundation data for future patient communication workflows.

The backend login response contains access and refresh tokens. Because the current backend uses JSON token responses rather than HTTP-only cookies, the frontend stores the current access and refresh tokens in browser `localStorage` through a single token storage module. Tokens are not logged or displayed. Backend RBAC remains the authoritative security boundary; frontend role checks are only for user experience.

## Authentication

Authentication currently lives inside the modular monolith under `com.patientmanagement.auth`. It may be extracted into a dedicated Authentication Service in a future architecture phase, but that extraction is not implemented now.

Public staff registration is disabled. ADMIN can provision ADMIN, RECEPTIONIST, or DOCTOR accounts; DOCTOR requires an existing, unlinked doctor record. First-admin provisioning uses the opt-in environment-driven bootstrap described in [Production Deployment](docs/production-deployment.md).

The production profile requires explicit non-local HTTPS `CORS_ALLOWED_ORIGINS`, `FRONTEND_API_URL`, and
`FEEDBACK_PUBLIC_BASE_URL`; it cannot fall back to the local development defaults.

Passwords are accepted only in request DTOs, hashed with BCrypt, and stored as `password_hash`. Password hashes are never returned by API responses.

Login returns:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Login failures use a generic authentication failure response so callers cannot distinguish unknown accounts from incorrect passwords.

## JWT and Refresh Tokens

Access tokens are stateless JWTs signed with HS256 using `JWT_SECRET`. Tokens with malformed content, tampered signatures, expired timestamps, or unexpected signing algorithms are rejected. Claims are limited to:

- `sub`
- `username`
- `roles`
- `iat`
- `exp`

JWTs do not contain patient data, medical records, prescriptions, passwords, or password hashes.

Refresh tokens are opaque random values. The database stores only a SHA-256 `token_hash`, expiration timestamp, revoked flag, and user relationship. Refresh uses rotation: the old refresh token is revoked and a replacement token is created in the same transaction.

Logout revokes the provided refresh token. Existing JWT access tokens are not instantly revoked because they are stateless; access tokens should remain short-lived.

## Roles and Authorization

Initial roles:

- `ADMIN`
- `DOCTOR`
- `RECEPTIONIST`

Authorization matrix:

| Area | Endpoint | Roles |
| --- | --- | --- |
| Patients | `GET /api/v1/patients` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Patients | `GET /api/v1/patients/{id}` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Patients | `POST /api/v1/patients` | `ADMIN`, `RECEPTIONIST` |
| Patients | `PUT /api/v1/patients/{id}` | `ADMIN`, `RECEPTIONIST` |
| Patients | `DELETE /api/v1/patients/{id}` | `ADMIN` |
| Doctors | `GET /api/v1/doctors` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Doctors | `GET /api/v1/doctors/{id}` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Doctors | `POST /api/v1/doctors` | `ADMIN` |
| Doctors | `PUT /api/v1/doctors/{id}` | `ADMIN` |
| Doctors | `DELETE /api/v1/doctors/{id}` | `ADMIN` |
| Medical records | `POST /api/v1/medical-records` | `ADMIN`, `DOCTOR` |
| Medical records | `GET /api/v1/medical-records/{id}` | `ADMIN`, `DOCTOR` |
| Medical records | `GET /api/v1/patients/{patientId}/medical-records` | `ADMIN`, `DOCTOR` |
| Prescriptions | `POST /api/v1/prescriptions` | `ADMIN`, `DOCTOR` |
| Prescriptions | `GET /api/v1/prescriptions/{id}` | `ADMIN`, `DOCTOR` |
| Prescriptions | `GET /api/v1/prescriptions/{id}/pdf` | `ADMIN`, `DOCTOR` |
| Prescriptions | `POST /api/v1/prescriptions/{id}/access` | `ADMIN`, `DOCTOR` |
| Prescriptions | `DELETE /api/v1/prescriptions/{id}/access` | `ADMIN`, `DOCTOR` |
| Patient prescription access | `GET /api/v1/prescription-access/{token}/pdf` | Public secure-token access |
| Prescriptions | `GET /api/v1/patients/{patientId}/prescriptions` | `ADMIN`, `DOCTOR` |
| Consultations | `POST /api/v1/appointments/{appointmentId}/consultation` | `ADMIN`, `DOCTOR` |
| Consultations | `GET /api/v1/appointments/{appointmentId}/consultation` | `ADMIN`, `DOCTOR` |
| Consultations | `GET /api/v1/consultations/{id}` | `ADMIN`, `DOCTOR` |
| Consultations | `PUT /api/v1/consultations/{id}` | `ADMIN`, `DOCTOR` |
| Consultations | `POST /api/v1/consultations/{id}/complete` | `ADMIN`, `DOCTOR` |
| Consultations | `GET /api/v1/patients/{patientId}/consultations` | `ADMIN`, `DOCTOR` |
| AI clinical assistant | `POST /api/v1/consultations/{consultationId}/ai/draft` | `ADMIN`, `DOCTOR` |
| AI clinical assistant | `GET /api/v1/patients/{patientId}/ai/summary` | `ADMIN`, `DOCTOR` |
| Appointments | `GET /api/v1/appointments` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Appointments | `GET /api/v1/appointments/{id}` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Appointments | `POST /api/v1/appointments` | `ADMIN`, `RECEPTIONIST` |
| Appointments | `PUT /api/v1/appointments/{id}` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Appointments | `DELETE /api/v1/appointments/{id}` | `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| Communications | `GET /api/v1/communications` | `ADMIN` |
| Communications | `GET /api/v1/communications/{id}` | `ADMIN` |

Unauthenticated or invalid authentication returns `401 Unauthorized`. Authenticated users with insufficient roles receive `403 Forbidden`.

Backend authorization is role-based and data-scoped. `ADMIN` keeps broad access. `RECEPTIONIST` keeps the existing operational patient and appointment permissions. A `DOCTOR` user must be linked to a doctor record through `doctors.user_id`; doctor-role access to appointments, patients, consultations, medical records, prescriptions, staff prescription-access-token operations, feedback staff views, and AI clinical endpoints is scoped through that doctor's appointment/patient relationships.

Public secure prescription PDF access and public feedback access remain token-based and do not require a JWT.

## Security Notes

The API uses stateless Bearer tokens, so HTTP sessions are disabled and CSRF protection is disabled for this API model. Responses include `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a restrictive permissions policy, cache-control headers, and HTTPS-only HSTS when served over TLS.

CORS is configured through `CORS_ALLOWED_ORIGINS`; wildcard origins are rejected and browser credentials are disabled because authentication uses explicit Bearer headers rather than cookies. Docker Compose binds PostgreSQL and the backend to `127.0.0.1` so local services are not exposed on the network by default.

API errors use the common JSON error structure and avoid stack traces, SQL details, JWT internals, and Java exception class names in client responses.

AI assistance is restricted to `ADMIN` and `DOCTOR` users. The default mock provider makes no external calls; `AI_PROVIDER=openai` uses the configured server-side OpenAI-compatible endpoint. AI input/output is treated as clinical information: prompts are not logged, provider secrets are not exposed, and generated content is not persisted automatically.

Rate limiting, brute-force controls backed by Redis, OAuth/SSO, and MFA are future work and are not implemented in this repository yet.

Communication records can contain patient contact/audit information, so communication history is restricted to `ADMIN` only in this foundation step.

Doctor data scoping intentionally avoids making patients globally doctor-owned. A doctor can access a patient only when an appointment relationship links that patient to the authenticated doctor's linked doctor record.

## Database Structure

Flyway migration `V1__create_core_schema.sql` creates:

- `patients`
- `doctors`
- `medical_records`
- `prescriptions`
- `prescription_items`

The schema uses UUID primary keys, foreign keys between records/prescriptions and patients/doctors, a cascading prescription-to-items relationship, unique doctor license numbers, and auditing timestamps on aggregate tables.

Flyway migration `V2__create_auth_schema.sql` creates:

- `users`
- `roles`
- `user_roles`
- `refresh_tokens`

The auth schema uses UUID primary keys, unique usernames and role names, a many-to-many user-role relationship, seeded roles, refresh-token hashes, expiration timestamps, and revocation tracking.

Flyway migration `V3__create_appointment_schema.sql` creates:

- `appointments`

Appointments use UUID primary keys, foreign keys to `patients` and `doctors`, a constrained string status, appointment date/time, reason, optional notes, and auditing timestamps. Useful indexes support patient, doctor, status, and date/time filtering.

Flyway migration `V4__create_communication_schema.sql` creates:

- `communications`

Communication records use UUID primary keys, a required patient relationship, an optional appointment relationship, type/channel/status constraints, recipient delivery target, provider message id, failure reason, attempt count, sent/delivered timestamps, and auditing timestamps. Indexes support patient, appointment, status, type, and created-at history queries.

Flyway migration `V5__create_consultation_schema.sql` creates:

- `consultations`

Consultations use UUID primary keys, a required unique appointment relationship, status constraints, clinical text fields, started/completed timestamps, and auditing timestamps. Patient and doctor are derived from the appointment relationship instead of being duplicated on the consultation row.

Flyway migration `V6__create_prescription_access_token_schema.sql` creates:

- `prescription_access_tokens`

Prescription access tokens use UUID primary keys, a required prescription relationship, unique SHA-256 token hashes, expiration timestamps, revocation timestamps, and a partial unique index that allows only one unrevoked access token per prescription. The raw patient access token is never stored.

Flyway migration `V7__create_feedback_schema.sql` creates:

- `feedback`
- `feedback_access_tokens`

Feedback records use UUID primary keys, a required unique consultation relationship, patient and doctor relationships derived from the consultation's appointment context, a rating constrained to 1 through 5, an optional bounded comment, and a created-at timestamp.

Feedback access tokens use UUID primary keys, a required consultation relationship, unique SHA-256 token hashes, expiration timestamps, revocation timestamps, and a partial unique index that allows only one unrevoked feedback token per consultation. The raw feedback access token is never stored.

Flyway migration `V8__link_doctors_to_users.sql` adds:

- `doctors.user_id`

The column is nullable for backward compatibility with existing doctor rows, has a unique constraint, and references `users(id)` with `ON DELETE SET NULL`. This is the doctor-ownership link used for backend-enforced data scoping.

## Consultation Management

Step 24 introduces first-class consultation records for appointment-based clinical encounters.

The appointment remains the scheduling record. A consultation represents the actual clinical encounter and stores:

- chief complaint
- symptoms
- examination or observations
- assessment or diagnosis
- treatment or advice
- follow-up instructions

Consultations can be started for `SCHEDULED` or `CONFIRMED` appointments. A single appointment can have only one consultation because `consultations.appointment_id` is unique. Admin and doctor users can start, update, read, and complete consultations. Receptionists can continue managing appointments but cannot view or modify clinical consultation records.

Completing a consultation requires at least a chief complaint and assessment. Completion marks the consultation `COMPLETED`, records `completed_at`, and marks the related appointment `COMPLETED`. Completed consultations are read-only through the current API.

Step 24 does not add:

- automatic medical-record generation from consultations
- prescription generation from consultations
- consultation-completed SMS
- real SMS provider integration
- patient portal functionality
- Redis, RabbitMQ, or external background workers

## AI Clinical Assistant

The merged Step 25-26 introduced AI-assisted clinical documentation and patient history summarization under `com.patientmanagement.ai`. Step 31 adds a real OpenAI-compatible provider behind the same `AiProvider` abstraction while preserving the mock provider for local development and automated tests.

The backend uses configuration-driven provider selection:

- `AI_ENABLED`
- `AI_PROVIDER`
- `AI_MODEL`
- `AI_TIMEOUT_SECONDS`
- `AI_API_KEY`
- `AI_BASE_URL`

Local development defaults to:

```text
AI_ENABLED=true
AI_PROVIDER=mock
AI_MODEL=mock-clinical-assistant-v1
AI_TIMEOUT_SECONDS=15
AI_API_KEY=
AI_BASE_URL=https://api.openai.com/v1/chat/completions
```

The mock provider makes no external network calls and does not require an API key. To use the real provider from the backend, set:

```text
AI_ENABLED=true
AI_PROVIDER=openai
AI_MODEL=gemini-3.8-flash
AI_TIMEOUT_SECONDS=30
AI_API_KEY=your-provider-api-key
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
```

`AI_API_KEY` must remain server-side. Do not place real API keys in frontend code, source control, README examples, or committed environment files. If `AI_PROVIDER=openai` is configured without an API key, the backend fails startup instead of silently falling back to mock AI.

The real provider retries HTTP 503 responses at most twice with short exponential backoff and jitter, within `AI_TIMEOUT_SECONDS`. Other HTTP failures, including HTTP 429, are not retried. Persistent provider failures return the existing AI-unavailable response; the application does not substitute mock-generated clinical content for a failed real request.

AI consultation note assistance is available through:

```http
POST /api/v1/consultations/{consultationId}/ai/draft
```

The request accepts rough clinical notes plus optional current consultation fields. The endpoint returns a structured draft with chief complaint, symptoms, examination/observations, assessment, treatment/advice, and follow-up instructions. The endpoint does not modify the consultation. The doctor or admin must review the AI-generated draft, apply or edit it in the form, and explicitly save through the existing consultation update endpoint.

AI patient history summaries are available through:

```http
GET /api/v1/patients/{patientId}/ai/summary
```

The summary is generated from bounded recent data already stored in the system: appointments, consultations, medical records, and prescriptions for the selected patient. It is not persisted. If little or no history exists, the mock provider reports insufficient documented clinical history rather than inventing information.

AI output is assistive and must be reviewed by an authorized clinical user before being used as part of the patient's clinical record. The backend does not automatically save AI-generated content into consultations, medical records, or prescriptions. The system does not provide AI diagnosis, treatment recommendations, autonomous prescribing, medical decision support, triage, predictive analytics, RAG over medical guidelines, or ambient/voice scribing.

## Communication Foundation

Step 19 introduces a provider-independent backend communication domain under `com.patientmanagement.communication`.

Supported communication types:

- `APPOINTMENT_CONFIRMATION`
- `APPOINTMENT_REMINDER`
- `CONSULTATION_COMPLETED`
- `PRESCRIPTION_AVAILABLE`
- `FEEDBACK_REQUEST`

Supported channel in this step:

- `SMS`

Supported statuses:

- `PENDING`
- `SIMULATED`
- `DISABLED`
- `SENT`
- `DELIVERED`
- `FAILED`

The current provider abstraction is `CommunicationProvider`. A development-safe `NoOpSmsCommunicationProvider` records a send attempt without contacting a real SMS vendor. Step 32 makes SMS provider selection configuration-driven, but `noop` is still the only implemented provider. Real providers such as Twilio, MSG91, or AWS SNS are intentionally not integrated yet.

No Step 19 workflow automatically sends patient messages from appointments, prescriptions, medical records, or consultation completion. The communication service exists so later steps can call a consistent backend foundation.

Not implemented in Step 19:

- Actual SMS delivery provider
- Prescription PDF generation
- Secure prescription links
- Appointment reminder scheduling
- Consultation-completion SMS automation
- Patient rating or feedback links
- Patient portal/login
- Background queues, Redis, or RabbitMQ

## Communication Runtime Activation

Step 20 verified the communication foundation in the Docker Compose runtime without resetting or deleting the PostgreSQL volume.

Verified runtime behavior:

- The backend Docker image was rebuilt from the current source.
- The backend container was recreated while the existing PostgreSQL container and named volume were preserved.
- Flyway validated migrations V1 through V4 and applied `V4__create_communication_schema.sql`.
- The `communications` table, constraints, foreign keys, and indexes exist in PostgreSQL.
- `/api/v1/health`, `/actuator/health`, and Swagger/OpenAPI start successfully in the Docker runtime.
- OpenAPI exposes only the communication history endpoints: `GET /api/v1/communications` and `GET /api/v1/communications/{id}`.
- Existing authenticated patient, doctor, and appointment list APIs continue to return `200 OK` for an existing receptionist user.
- Unauthenticated protected API requests return `401 Unauthorized`.
- Communication history remains restricted; an existing receptionist user receives `403 Forbidden`.

The preserved local database currently has no `ADMIN` user, so a live `200 OK` response for the admin-only communication history endpoints was not verified without creating or modifying user data. Backend tests cover the admin authorization path.

## Appointment Confirmation SMS Workflow

Step 21 connects appointment creation to the existing communication foundation.

When `POST /api/v1/appointments` successfully creates a new appointment, the backend automatically creates an `APPOINTMENT_CONFIRMATION` communication record for the appointment's patient using the `SMS` channel and the patient's stored phone number.

In local development, `NoOpSmsCommunicationProvider` records `SIMULATED`, never `SENT`. Disabled SMS records `DISABLED`; a missing provider records `FAILED`. Neither simulation nor disabling sets a sent timestamp. Historical pre-remediation `SENT` records are not proof of vendor delivery and are preserved unchanged.

This step does not add:

- Real SMS provider integration
- Prescription delivery
- Feedback/rating links
- Patient portal functionality
- Communication frontend screens
- Redis, RabbitMQ, scheduled jobs, or background workers

## Appointment Reminder Communication Workflow

Step 22 connects upcoming appointment detection to the existing communication foundation.

The backend periodically checks for `SCHEDULED` and `CONFIRMED` appointments from the current time through the configured reminder look-ahead window. For each due appointment that does not already have an active `APPOINTMENT_REMINDER` communication, it creates an SMS reminder communication using the patient's stored phone number.

Local development continues to use `NoOpSmsCommunicationProvider`, so reminders are recorded as simulated sends and no real SMS vendor is contacted.

Reminder configuration:

- `APPOINTMENT_REMINDERS_ENABLED`, default `true`
- `APPOINTMENT_REMINDER_LOOK_AHEAD_HOURS`, default `24`
- `APPOINTMENT_REMINDER_FIXED_DELAY_MS`, default `900000`
- `APPOINTMENT_REMINDER_INITIAL_DELAY_MS`, default `60000`

This step does not add:

- Real SMS provider integration
- Custom reminder templates or message bodies
- Prescription delivery
- Feedback/rating links
- Patient portal functionality
- Communication frontend screens
- Redis, RabbitMQ, or external background workers

## Prescription Available SMS Workflow

Step 23 connects prescription creation to the existing communication foundation.

When `POST /api/v1/prescriptions` successfully creates a prescription, the backend automatically creates a `PRESCRIPTION_AVAILABLE` communication record for the prescription's patient using the `SMS` channel and the patient's stored phone number.

Local development continues to use `NoOpSmsCommunicationProvider`, so prescription-available notifications are recorded as simulated sends and no real SMS vendor is contacted. The communication dispatch does not include medication names, dosage, diagnosis, medical-record text, prescription contents, tokens, passwords, or secure prescription links.

The current communication schema does not include a prescription foreign key, so the audit record is associated with the patient but not with a specific prescription row. Prescription-specific duplicate prevention is therefore not implemented in this step.

This step does not add:

- Real SMS provider integration
- Prescription PDF generation
- Secure prescription links
- Prescription update or delete endpoints
- Patient portal functionality
- Communication frontend screens
- Redis, RabbitMQ, or external background workers

## Consultation Completed SMS Workflow

Step 27 connects successful consultation completion to the existing communication foundation.

When `POST /api/v1/consultations/{id}/complete` successfully transitions an in-progress consultation to `COMPLETED`, the backend automatically creates a `CONSULTATION_COMPLETED` communication record for the appointment's patient using the `SMS` channel and the patient's stored phone number.

The SMS dispatch uses a generic privacy-safe message and does not include diagnosis, symptoms, medications, prescription details, clinical notes, AI-generated text, tokens, passwords, or other sensitive clinical content.

If the patient's phone number is missing or blank, the consultation still completes and a failed communication audit record is stored with `Communication recipient is required`. The SMS provider is not called with a blank recipient.

Local development continues to use `NoOpSmsCommunicationProvider`, so consultation-completed notifications are recorded as simulated sends and no real SMS vendor is contacted.

This step does not add:

- Real SMS provider integration
- New public communication-send APIs
- Communication frontend screens
- Patient portal functionality
- Real AI/LLM provider integration
- Redis, RabbitMQ, or external background workers

## Prescription PDF Generation

Step 28 adds on-demand prescription PDF generation.

Authorized `ADMIN` and `DOCTOR` users can request:

```http
GET /api/v1/prescriptions/{id}/pdf
```

The backend loads the existing prescription, patient, doctor, and prescription-item data, generates a PDF in memory, and returns it as `application/pdf` with an attachment filename. The PDF is not persisted.

The document includes only fields already available in the current domain model: prescription date, patient name/date of birth/gender/blood group/contact details, doctor name/specialization/department/license/contact details, prescription items, dosage, frequency, duration, instructions, notes, and a simple footer.

The PDF does not include JWTs, refresh tokens, passwords, AI prompts/responses, internal database IDs, secure links, or unsupported clinical fields.

The frontend prescriptions screen exposes a `PDF` action for authorized users. It uses the existing authenticated API client path and opens the generated PDF in a new tab, falling back to a download if the browser blocks the new tab.

This step does not add:

- Secure public prescription links
- Patient portal functionality
- Stored PDF files
- S3/cloud storage
- Email delivery
- E-signatures or QR verification
- Real SMS provider integration
- Redis, RabbitMQ, or external background workers

## Secure Patient Prescription Access

Step 29 adds secure prescription-specific patient access without creating patient accounts or a patient role.

Authorized `ADMIN` and `DOCTOR` users can create a one-time-display access token for a prescription:

```http
POST /api/v1/prescriptions/{id}/access
```

The response contains only the raw token and expiration timestamp:

```json
{
  "token": "...",
  "expiresAt": "2026-09-13T00:00:00Z"
}
```

The backend generates the token from 48 cryptographically secure random bytes and encodes it with URL-safe Base64. The token does not contain the prescription ID, patient ID, timestamps, or predictable data. Only the SHA-256 token hash is stored in `prescription_access_tokens`.

The current MVP policy allows one active patient access token per prescription. Creating a new token revokes any previous unrevoked token for that prescription. Staff can also revoke the active token explicitly:

```http
DELETE /api/v1/prescriptions/{id}/access
```

The patient-facing PDF endpoint is:

```http
GET /api/v1/prescription-access/{token}/pdf
```

This endpoint does not require JWT authentication. It authenticates the request by hashing the supplied token, finding the stored hash, verifying that the token is not expired or revoked, and then reusing the existing Step 28 PDF generator for the associated prescription. The URL does not include a prescription ID, and callers cannot use one token to access another prescription.

Invalid, expired, or revoked tokens return the same generic error and do not reveal whether a prescription, patient, or token record exists.

The frontend prescriptions screen exposes `Create Patient Access`, `Copy link`, and `Revoke access` actions for authorized users. The generated token/link is displayed for immediate copying and is not stored in `localStorage` or `sessionStorage`. The public `/prescription-access/:token` route is purpose-specific and has no patient dashboard, login, portal navigation, or broader clinical-data access.

This step does not add:

- Patient accounts or patient login
- A `PATIENT` role
- OTP authentication
- Email delivery
- Real SMS delivery
- Stored prescription PDFs
- S3/cloud storage
- Public access to patients, doctors, appointments, medical records, communications, AI, or unrelated prescriptions

## Patient Feedback / Rating

Step 30 adds a completed-consultation feedback workflow without adding patient accounts or a patient role.

Authorized `ADMIN` and `DOCTOR` users can create a one-time-display feedback access link for a completed consultation:

```http
POST /api/v1/consultations/{consultationId}/feedback-access
```

The response contains the raw token, public feedback URL, and expiration timestamp:

```json
{
  "token": "...",
  "feedbackUrl": "http://localhost:5173/feedback/...",
  "expiresAt": "2026-09-13T00:00:00Z"
}
```

The backend generates the token from 48 cryptographically secure random bytes and encodes it with URL-safe Base64. The token does not contain the consultation ID, patient ID, doctor ID, timestamps, or predictable data. Only the SHA-256 token hash is stored in `feedback_access_tokens`.

The current MVP policy allows one active feedback access token per consultation. Creating a new token revokes any previous unrevoked token for that consultation. Staff can also revoke the active token explicitly:

```http
DELETE /api/v1/consultations/{consultationId}/feedback-access
```

The patient-facing feedback endpoints are:

```http
GET  /api/v1/feedback-access/{token}
POST /api/v1/feedback-access/{token}
```

These endpoints do not require JWT authentication. They authenticate the request by hashing the supplied token, finding the stored hash, verifying that the token is not expired or revoked, and then resolving the completed consultation from the token. The URL does not include consultation, patient, or doctor IDs.

Patients can submit one rating from 1 to 5 and an optional comment up to 1000 characters. Successful submission creates one feedback record for the consultation and immediately revokes the token. A consultation can have only one feedback record.

After successful consultation completion, the backend also attempts to create a separate `FEEDBACK_REQUEST` communication using the existing `CommunicationService` and development-safe `NoOpSmsCommunicationProvider`. This is best-effort: a feedback token or communication failure does not roll back the completed consultation. The persisted communication record stores delivery audit fields only, not the message body or raw feedback token.

The frontend appointments screen exposes `Create Feedback Link` for completed consultations to authorized admin/doctor users. The generated link is displayed for immediate copying and is not stored in `localStorage` or `sessionStorage`. The public `/feedback/:token` route has no patient dashboard, login, portal navigation, or broader clinical-data access.

Staff feedback history is available to `ADMIN` and `DOCTOR` users through:

```http
GET /api/v1/feedback
```

This step does not add:

- Patient accounts or patient login
- A `PATIENT` role
- OTP authentication
- Google Reviews or public reviews
- Advanced analytics
- Real SMS delivery
- Real AI/LLM provider integration
- Redis, RabbitMQ, microservices, or cloud storage

## Real SMS Provider Preparation

Step 32 prepares the communication subsystem for a future real SMS adapter without integrating any external SMS provider.

Current SMS provider behavior:

- `NoOpSmsCommunicationProvider` remains the active/default provider.
- `SMS_PROVIDER=noop` is the only supported provider value.
- `SMS_ENABLED=false` records `DISABLED` and does not call a provider.
- Unsupported provider names fail startup instead of silently falling back or attempting an external call.
- Automated tests do not send real SMS and do not require provider credentials.

SMS configuration:

```text
SMS_ENABLED=true
SMS_PROVIDER=noop
SMS_SENDER=PMCLINIC
SMS_TIMEOUT_SECONDS=15
SMS_TEMPLATE_APPOINTMENT_CONFIRMATION=
SMS_TEMPLATE_APPOINTMENT_REMINDER=
SMS_TEMPLATE_CONSULTATION_COMPLETED=
SMS_TEMPLATE_PRESCRIPTION_AVAILABLE=
SMS_TEMPLATE_FEEDBACK_REQUEST=
```

The template values are optional placeholders for future provider-side template identifiers. They are not secrets, and no real provider credentials are included in source control, `.env.example`, or tests.

All existing SMS-triggering workflows continue to go through `CommunicationService` and `CommunicationProvider`:

- Appointment confirmation
- Appointment reminder
- Consultation completed
- Prescription available
- Feedback request

Messages remain privacy-safe. They do not include diagnosis, symptoms, clinical notes, AI-generated clinical content, medication details, prescription contents, passwords, JWTs, refresh tokens, provider credentials, or other internal secrets. Secure feedback tokens are not persisted in communication records or exposed through logs.

Communication dispatch is best-effort for the main business workflows. Appointment creation, consultation completion, prescription creation, and feedback-request generation should not roll back merely because SMS dispatch cannot be completed.

## Planned Architecture

The system will evolve incrementally:

- Core Patient Management application as a modular monolith
- Appointment Service as a microservice when appointment boundaries justify separation
- Authentication Service as a microservice only if authentication boundaries justify separation
- Notification Service as a microservice when notification workflows are introduced
- Spring Cloud API Gateway after there are multiple backend services to route

The project will avoid unnecessary technologies until there is a clear requirement.
