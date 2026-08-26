# Patient Management System

Patient Management System is being built incrementally as a production-oriented healthcare platform. The repository currently contains the backend foundation and the first core patient-management domains.

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
- Unit tests, controller tests, and Testcontainers PostgreSQL integration coverage

Not implemented yet:

- Authentication and authorization
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
- Flyway
- springdoc-openapi
- JUnit
- Mockito
- Spring Boot Test
- Testcontainers

Planned future platform stack:

- React, TypeScript, Vite, Tailwind CSS, shadcn/ui
- Spring Security
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

## Database Structure

Flyway migration `V1__create_core_schema.sql` creates:

- `patients`
- `doctors`
- `medical_records`
- `prescriptions`
- `prescription_items`

The schema uses UUID primary keys, foreign keys between records/prescriptions and patients/doctors, a cascading prescription-to-items relationship, unique doctor license numbers, and auditing timestamps on aggregate tables.

## Planned Architecture

The system will evolve incrementally:

- Core Patient Management application as a modular monolith
- Appointment Service as a microservice when appointment boundaries justify separation
- Authentication Service as a microservice when authentication requirements are introduced
- Notification Service as a microservice when notification workflows are introduced
- Spring Cloud API Gateway after there are multiple backend services to route

The project will avoid unnecessary technologies until there is a clear requirement.
