# Security and Authorization

## Authentication

Clinora uses stateless JWT Bearer authentication inside the Spring Boot modular monolith.

- Passwords are BCrypt-hashed; plaintext passwords are not stored or returned.
- Access tokens have a configured short lifetime.
- Refresh tokens are hashed in the database, rotated on refresh, and revoked on logout.
- Invalid, expired, or malformed tokens are rejected with sanitized JSON responses.
- HTTP sessions are not used for authentication.

Staff registration is not public. `POST /api/v1/auth/register` is ADMIN-only. The optional bootstrap administrator is designed for first deployment only and uses a database advisory lock to avoid concurrent bootstrap races. Disable it after use.

## Roles

| Capability | ADMIN | DOCTOR | RECEPTIONIST |
|---|---:|---:|---:|
| Read patients/doctors/appointments | Yes | Yes, scoped where clinical | Yes |
| Create/update patients | Yes | No | Yes |
| Delete patients | Yes | No | No |
| Create/update/delete doctors | Yes | No | No |
| Create appointments | Yes | No | Yes |
| Update/cancel appointments | Yes | Yes, scoped | Yes |
| Consultations, medical records, prescriptions | Yes | Yes, scoped | No |
| AI clinical assistance | Yes | Yes, scoped | No |
| Manage staff registration | Yes | No | No |
| View communication audit records | Yes | No | No |

The frontend can hide unavailable actions for usability, but backend method security is authoritative.

## Doctor Data Scoping

Doctor users are linked to a doctor record. For a non-admin doctor, clinical reads/writes are checked against that doctor's appointment and patient relationships. This protects consultations, medical records, prescriptions, access-token management, feedback staff views, and AI clinical endpoints from cross-doctor access.

## Public Token Routes

Clinora does not create patient accounts or a patient role. Instead, two constrained public workflows use opaque tokens:

- Prescription PDF access tokens retrieve only the associated prescription PDF.
- Feedback access tokens retrieve/submit feedback only for the associated completed consultation.

The database stores token hashes, checks expiry and revocation, and does not expose the token value in records or logs.

## Request and Deployment Protections

- CORS requires explicit origins; wildcard origins are rejected and credentials are disabled for CORS.
- CSRF is disabled because the API is stateless Bearer-token based.
- Security headers include content-type protection, frame denial, referrer policy, permissions policy, and HSTS for HTTPS connections.
- Health endpoints are public; sensitive actuator endpoints remain protected.
- A bounded, in-process abuse-control filter limits login, refresh, registration, and AI requests. It is not a distributed rate limiter and should be supplemented at the trusted edge for multi-replica deployment.
- Production requires explicit HTTPS origins/URLs, a strong JWT secret, and external TLS termination. See [production deployment](production-deployment.md).

## Operational Notes

Tokens are stored in browser local storage, which means XSS prevention matters. Do not add untrusted scripts or render untrusted HTML. Keep AI keys server-side, use environment/secret management for production credentials, and do not commit `.env` files.
