# API Reference

All staff APIs are versioned under `/api/v1` and return JSON unless an endpoint returns a PDF. Requests use Bearer authentication unless explicitly marked public. OpenAPI is available locally at `/v3/api-docs` and `/swagger-ui.html`.

## Endpoint Groups

| Area | Endpoints | Access |
|---|---|---|
| Health | `GET /api/v1/health` | Public |
| Authentication | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/register` | Login/refresh public; logout authenticated; staff registration ADMIN-only |
| Patients | CRUD `/patients` | Read: all staff; create/update: ADMIN/RECEPTIONIST; delete: ADMIN |
| Doctors | CRUD `/doctors` | Read: all staff; create/update/delete: ADMIN |
| Appointments | CRUD-style `/appointments` | Read/update/cancel: staff; create: ADMIN/RECEPTIONIST; doctor access is scoped |
| Consultations | Appointment consultation and consultation update/complete routes | ADMIN/DOCTOR; doctor access is scoped |
| Medical records | Create, retrieve, and list-by-patient | ADMIN/DOCTOR; doctor access is scoped |
| Prescriptions | Create, retrieve, PDF, and list-by-patient | ADMIN/DOCTOR; doctor access is scoped |
| Prescription access | Staff token management and public token PDF route | Staff management: ADMIN/DOCTOR; public PDF: token-authenticated |
| Feedback | Staff feedback token management/review and public token routes | Staff: ADMIN/DOCTOR; public context/submit: token-authenticated |
| Communications | List and retrieve communication records | ADMIN |
| AI | Consultation draft and patient-history summary | ADMIN/DOCTOR; doctor access is scoped |

## Selected Routes

```text
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/register

GET    /api/v1/patients
POST   /api/v1/patients
GET    /api/v1/patients/{id}
PUT    /api/v1/patients/{id}
DELETE /api/v1/patients/{id}

GET    /api/v1/doctors
POST   /api/v1/doctors
GET    /api/v1/doctors/{id}
PUT    /api/v1/doctors/{id}
DELETE /api/v1/doctors/{id}

GET    /api/v1/appointments
POST   /api/v1/appointments
GET    /api/v1/appointments/{id}
PUT    /api/v1/appointments/{id}
DELETE /api/v1/appointments/{id}

POST   /api/v1/appointments/{appointmentId}/consultation
GET    /api/v1/appointments/{appointmentId}/consultation
GET    /api/v1/consultations/{id}
PUT    /api/v1/consultations/{id}
POST   /api/v1/consultations/{id}/complete

POST   /api/v1/medical-records
GET    /api/v1/medical-records/{id}
GET    /api/v1/patients/{patientId}/medical-records

POST   /api/v1/prescriptions
GET    /api/v1/prescriptions/{id}
GET    /api/v1/prescriptions/{id}/pdf
GET    /api/v1/patients/{patientId}/prescriptions

POST   /api/v1/prescriptions/{prescriptionId}/access
GET    /api/v1/prescriptions/{prescriptionId}/access
DELETE /api/v1/prescriptions/{prescriptionId}/access
GET    /api/v1/prescription-access/{token}/pdf

POST   /api/v1/consultations/{consultationId}/feedback-access
DELETE /api/v1/consultations/{consultationId}/feedback-access
GET    /api/v1/feedback-access/{token}
POST   /api/v1/feedback-access/{token}
GET    /api/v1/feedback

GET    /api/v1/communications
GET    /api/v1/communications/{id}

POST   /api/v1/consultations/{consultationId}/ai/draft
GET    /api/v1/patients/{patientId}/ai/summary
```

## Conventions

- Collection endpoints use Spring pageable parameters. Default page size is 20; maximum is 100.
- Validation, authorization, missing-resource, conflict, and unexpected failures use the project's JSON error response shape.
- Appointment `DELETE` is cancellation, not a physical database deletion.
- The public prescription and feedback endpoints are narrowly scoped by opaque, hashed, expiring, revocable tokens. They do not grant general patient API access.

For exact request/response schemas, validation rules, and current OpenAPI output, run the local backend and use Swagger rather than duplicating generated schema here.
