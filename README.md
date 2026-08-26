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
- Unit tests, controller tests, and Testcontainers PostgreSQL integration coverage

Not implemented yet:

- Appointment service
- Authentication service
- Notification service
- API gateway
- Frontend
- Redis
- RabbitMQ
- Docker or Docker Compose
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
- Nimbus JOSE/JWT through Spring Security JOSE
- JUnit
- Mockito
- Spring Boot Test
- Testcontainers

Planned future platform stack:

- React, TypeScript, Vite, Tailwind CSS, shadcn/ui
- PostgreSQL, Redis, RabbitMQ
- Docker and Docker Compose
- GitHub Actions
- AWS

## Development Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15+ for local runtime
- Docker only when running Testcontainers-backed integration tests

## Environment Variables

The backend reads database settings from environment variables. Use `backend/.env.example` as documentation for the required names.

Required for local runtime:

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

Do not commit a real `.env` file.

## Run Backend Locally

From the backend directory:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile expects PostgreSQL to be running and the database environment variables to be configured. Flyway creates the current core schema.

## Run Tests

From the backend directory:

```bash
mvn test
```

The service and controller tests do not require a manually installed PostgreSQL instance. The PostgreSQL integration test uses Testcontainers and is skipped when Docker is not available.

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
