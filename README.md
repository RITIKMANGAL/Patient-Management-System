# Patient Management System

Patient Management System is being built incrementally as a production-oriented healthcare platform. The repository currently contains the backend foundation, the first core patient-management domains, and monolith-local authentication/authorization.

## Current Architecture Status

The current application is a single Maven-based Spring Boot backend under `backend/`.

Implemented now:

- Spring Boot backend foundation
- Domain-oriented package structure
- Patient domain APIs and persistence
- Doctor domain APIs and persistence
- Medical record APIs and persistence
- Prescription and prescription item APIs and persistence
- Environment-driven PostgreSQL configuration
- Flyway V1 core schema migration
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
- Unit tests, controller tests, and Testcontainers PostgreSQL integration coverage

Not implemented yet:

- Appointment service
- Authentication service
- Notification service
- API gateway
- Frontend
- Redis
- RabbitMQ
- AWS deployment

## Technology Stack

Current backend:

- Java 21
- Spring Boot 3.x
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

Planned future platform stack:

- React, TypeScript, Vite, Tailwind CSS, shadcn/ui
- Redis, RabbitMQ
- GitHub Actions
- AWS

## Development Prerequisites

- Docker
- Docker Compose

Optional, for running the backend or tests outside containers:

- Java 21
- Maven 3.9+

Manual PostgreSQL installation is not required for the Docker Compose workflow.

## Environment Variables

The backend reads database and security settings from environment variables. Use `.env.example` for Docker Compose overrides and `backend/.env.example` when running the backend directly from your shell.

Do not commit a real `.env` file.

Docker Compose provides safe local defaults so a new checkout can start with:

```bash
docker compose up --build
```

For local Docker development, copy `.env.example` to `.env` only when you want to override defaults:

```text
POSTGRES_DB=patient_management
DATABASE_URL=jdbc:postgresql://postgres:5432/patient_management
DATABASE_USERNAME=patient_management
DATABASE_PASSWORD=replace_with_local_development_password
JWT_SECRET=replace_with_local_development_jwt_secret_at_least_32_bytes
JWT_ACCESS_TOKEN_EXPIRATION=900
JWT_REFRESH_TOKEN_EXPIRATION=604800
CORS_ALLOWED_ORIGINS=http://localhost:5173
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
```

Optional:

```text
SERVER_PORT=8080
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

## Run Backend Directly

From the backend directory:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile expects PostgreSQL to be running and the database environment variables to be configured. Flyway creates the current core and auth schemas.

## Run Tests

From the backend directory:

```bash
mvn test
```

The service and controller tests do not require a manually installed PostgreSQL instance. The PostgreSQL integration test uses Testcontainers and is skipped when Docker is not available.

With Docker available, the Testcontainers-backed integration tests start PostgreSQL automatically and verify the Flyway-managed schema.

## Docker Image

`backend/Dockerfile` uses a multi-stage build:

- Builder: `maven:3.9.9-eclipse-temurin-21-alpine`
- Runtime: `eclipse-temurin:21-jre-alpine`

The runtime image contains only the built Spring Boot JAR and runs as a non-root `app` user. Maven is not present in the runtime stage.

## Docker Security Notes

The Docker setup is for reproducible local development, not a complete production deployment architecture. The backend image runs as a non-root user, exposes only port `8080`, and receives database/JWT/CORS configuration through environment variables. PostgreSQL exposes `5432` to the host to make local development and inspection straightforward.

Do not use the sample local passwords or JWT secret in shared environments. Override them through a local `.env` file or your shell.

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
GET  /api/v1/patients/{patientId}/prescriptions
```

OpenAPI and Swagger:

```http
GET /v3/api-docs
GET /swagger-ui.html
```

Business APIs require `Authorization: Bearer <accessToken>`. Health endpoints and auth register/login/refresh are public. Swagger/OpenAPI endpoints are accessible for local development.

## Authentication

Authentication currently lives inside the modular monolith under `com.patientmanagement.auth`. It may be extracted into a dedicated Authentication Service in a future architecture phase, but that extraction is not implemented now.

Public registration creates a `RECEPTIONIST` user by default. Unauthenticated callers cannot choose `ADMIN`; administrative user creation will be introduced later through a controlled workflow.

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

Access tokens are stateless JWTs signed with `JWT_SECRET`. Claims are limited to:

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
| Prescriptions | `GET /api/v1/patients/{patientId}/prescriptions` | `ADMIN`, `DOCTOR` |

Unauthenticated or invalid authentication returns `401 Unauthorized`. Authenticated users with insufficient roles receive `403 Forbidden`.

## Security Notes

The API uses stateless Bearer tokens, so HTTP sessions are disabled and CSRF protection is disabled for this API model. Spring Security's default security headers remain enabled.

CORS is configured through `CORS_ALLOWED_ORIGINS`; wildcard origins with credentials are not used.

Rate limiting, brute-force controls backed by Redis, OAuth/SSO, and MFA are future work and are not implemented in this repository yet.

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

## Planned Architecture

The system will evolve incrementally:

- Core Patient Management application as a modular monolith
- Appointment Service as a microservice when appointment boundaries justify separation
- Authentication Service as a microservice only if authentication boundaries justify separation
- Notification Service as a microservice when notification workflows are introduced
- Spring Cloud API Gateway after there are multiple backend services to route

The project will avoid unnecessary technologies until there is a clear requirement.
