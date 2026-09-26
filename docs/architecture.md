# Architecture

## Overview

Clinora is a modular monolith. A React frontend communicates with one Spring Boot application, which owns authentication, business workflows, data access, and provider abstractions. PostgreSQL is the system of record.

```text
React frontend
  -> versioned REST API
  -> Spring Security and controllers
  -> domain services
  -> Spring Data JPA repositories
  -> PostgreSQL
```

The AI and communication boundaries are code-level provider abstractions, not deployed microservices.

## Backend Modules

| Module | Responsibility |
|---|---|
| `auth` | Staff users, roles, JWTs, refresh tokens, bootstrap administration, and security filters |
| `patient` / `doctor` | Core directory data and doctor-user linkage |
| `appointment` | Scheduling, availability rules, status transitions, and cancellation |
| `consultation` | Appointment-based clinical encounters and completion workflow |
| `medicalrecord` | Patient medical-record creation and retrieval |
| `prescription` | Prescription/item creation, retrieval, and PDF generation |
| `prescription.access` | Expiring, hashed, revocable patient PDF access tokens |
| `feedback` | Expiring, hashed feedback tokens and completed-consultation feedback |
| `communication` | Communication audit records, SMS provider abstraction, and reminders |
| `ai` | Advisory consultation drafts and patient-history summaries |
| `common` / `config` | Error responses, validation, time, OpenAPI, and production safeguards |

## Frontend Modules

The React application uses React Router and protected routes. `AuthContext` owns browser session state; the API client attaches Bearer tokens, attempts a single refresh after an eligible `401`, and clears the session when refresh fails.

Pages cover login, staff registration, dashboard, patients, doctors, appointments/consultations, medical records, prescriptions, public prescription access, and public feedback. The authenticated layout supplies navigation and role-aware action visibility, while the backend remains authoritative.

## Data Model

```text
AuthUser --< roles
AuthUser --0..1 Doctor

Patient --< Appointment >-- Doctor
Appointment --0..1 Consultation
Patient --< MedicalRecord >-- Doctor
Patient --< Prescription --< PrescriptionItem
Patient --< Communication
Appointment --< Communication
Prescription --< PrescriptionAccessToken
Consultation --< FeedbackAccessToken
Consultation --0..1 Feedback
```

Doctor-role access is constrained through the linked doctor record and the relevant appointment/patient relationships.

## Database Migrations

Flyway owns schema evolution. Hibernate runs with `ddl-auto=validate` rather than generating schema changes.

| Migration | Scope |
|---|---|
| V1 | Patients, doctors, medical records, prescriptions, and prescription items |
| V2 | Staff users, roles, and refresh tokens |
| V3 | Appointments |
| V4 | Communications |
| V5 | Consultations |
| V6 | Prescription access tokens |
| V7 | Feedback and feedback access tokens |
| V8 | Doctor-to-user linkage |
| V9 | Explicit simulated/disabled communication statuses |
| V10 | PostgreSQL exclusion constraints for active appointment overlap prevention |

V10 uses `btree_gist` and prevents active 30-minute overlaps for both doctors and patients. Back-to-back bookings remain valid. It intentionally fails on invalid pre-existing overlap data rather than deleting or silently changing it.

## Time and Scheduling

Appointments use offset-free `LocalDateTime` values and PostgreSQL `TIMESTAMP`, interpreted as clinic wall time through `CLINIC_TIME_ZONE`. Invalid or ambiguous daylight-saving times are rejected. Clinora is designed for one configured clinic time zone, not multi-clinic/time-zone scheduling.

## Local Runtime

Docker Compose starts PostgreSQL 18 and the backend. PostgreSQL data persists in the `postgres_data` named volume. The backend waits for PostgreSQL health and applies/validates Flyway migrations at startup. The frontend runs separately with Vite during development.

See [development](development.md) and [production deployment](production-deployment.md) for operational details.
