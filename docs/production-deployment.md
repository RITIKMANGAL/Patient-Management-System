# Production deployment and release gates

This is a modular monolith. No deployment or production-readiness claim follows merely from passing tests.
Use Java 21, Maven, PostgreSQL 18, and the committed frontend lockfile.
Spring Boot 4.0.8 replaces the unsupported 3.3 line. Tomcat 11.0.26 addresses advisories found in
the managed 11.0.24 dependency. Nimbus is 10.10; springdoc is 3.0.3.
The official Jackson 2 compatibility module preserves existing AI/security serialization while MVC uses
Jackson 3 with compatibility defaults. The deprecated bridge should be retired before a future Boot upgrade.

## Staff provisioning

Anonymous staff registration is forbidden. POST /api/v1/auth/register and /register require ADMIN.
An ADMIN chooses one supported role; DOCTOR additionally requires an existing, unlinked doctorId.
Passwords must be at least 8 characters and at most 72 UTF-8 bytes; hashes never leave the backend.
Set BOOTSTRAP_ADMIN_ENABLED=true only for the first deployment and supply BOOTSTRAP_ADMIN_USERNAME,
BOOTSTRAP_ADMIN_PASSWORD, BOOTSTRAP_ADMIN_FIRST_NAME and BOOTSTRAP_ADMIN_LAST_NAME from a secret store.
A PostgreSQL transaction advisory lock serializes bootstrap. An existing admin is never overwritten;
an existing non-admin username is never promoted. Disable bootstrap and remove its credentials afterward.
Do not enable demo seeding in production.

## Mandatory production configuration

Set SPRING_PROFILES_ACTIVE=prod. Supply DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD and a strong,
random JWT_SECRET. Supply explicit HTTPS CORS_ALLOWED_ORIGINS, FRONTEND_API_URL and FEEDBACK_PUBLIC_BASE_URL.
Set CLINIC_TIME_ZONE to the intended IANA clinic zone. Never bake .env or credentials into an image.
The prod profile disables Swagger, demo data, mock AI and SMS by default. Enabled SMS is rejected until a
real vendor is implemented and verified. Enabled AI requires provider=openai, HTTPS and a real key;
successful real-provider verification remains a deployment gate, not an automatic consequence of configuration.
Use AI_ENABLED=false until that gate and the clinical data/privacy review are satisfied.

Production frontend builds require VITE_API_BASE_URL=https://your-api-origin or / for same-origin
reverse-proxy routing. The build rejects missing configuration, localhost and plaintext HTTP.
Development remains npm run dev on localhost:5173 with its existing localhost:8080 fallback.
For local production-bundle testing, use a same-origin /api reverse proxy; do not weaken the production guard.

Compose is a local-development configuration. Its defaults do NOT constitute production configuration.
When using it with prod, explicitly set AI_ENABLED=false, SMS_ENABLED=false, DEMO_DATA_ENABLED=false,
APPOINTMENT_REMINDERS_ENABLED=false and all URLs/clinic zone; do not rely on Compose's development defaults.
Terminate TLS at a trusted reverse proxy. Expose neither PostgreSQL nor port 8080 publicly.
Preserve Authorization headers. The application does not trust X-Forwarded-For for rate-limit identity.
Enforce additional client-IP limits at the trusted edge; behind a proxy app login limits are shared by
that proxy's source IP. TLS/HSTS/CSP for the frontend must be configured at the serving proxy.

## Scheduling and transactions

Appointment values remain offset-free ISO LocalDateTime and PostgreSQL TIMESTAMP, interpreted as
clinic wall time, not the browser/device zone. Existing values are not converted. UTC preserves the
previous Docker default; operators must review existing meaning before selecting another clinic zone.
Scheduling, reminders and clinical AI date comparisons use the same configured clock.
Nonexistent or ambiguous DST appointment times are rejected instead of guessed.
Frontend datetime-local input sends the wall time unchanged. Multi-clinic/multi-timezone scheduling is not supported.

V1-V8 are unchanged. V9 permits SIMULATED and DISABLED communication states.
V10 installs btree_gist and exclusion constraints for 30-minute active doctor AND patient slots.
SCHEDULED/CONFIRMED occupy [start,end); back-to-back bookings are valid.
Migration requires extension-install privileges and zero pre-existing active overlap; it fails rather than deleting data.
Take a verified backup before migration. ddl-auto remains validate.
Constraint and deadlock conflicts return sanitized 409 responses; ordinary preflight overlap remains 400.
Re-read availability before retrying a concurrent write.

Appointment/prescription/consultation notifications now execute after the clinical commit in separate
transactions. A rollback sends nothing; delivery failure cannot roll back committed clinical data.
This is best-effort, not a durable queue: a process crash between commit and callback can lose a notification.
NoOp means SIMULATED, disabled means DISABLED, unavailable provider means FAILED. SENT means provider
acceptance, not handset delivery; DELIVERED requires a future vendor confirmation implementation.
Historical SENT rows from the old NoOp implementation are retained but are not delivery evidence.

## Abuse controls and privacy

Single-process, bounded fixed windows: login 20/minute/source IP, refresh 60/minute/source IP,
staff provisioning 10/minute/user, AI 10/minute/user. Rejections use JSON 429 and Retry-After.
Limits reset on process restart and multiply across replicas. Keep edge controls for distributed deployments.
JWT remains Bearer-based; refresh tokens are hashed and rotated. Tokens in browser localStorage retain
an XSS exposure risk. Do not load untrusted scripts; no HTML rendering of AI output is authorized.
Use synthetic data for provider verification, and approve healthcare-data processing/retention separately.
AI is advisory, not an autonomous writer of clinical records.

## Release contents and verification

docs/release-files.txt is the exact required source/test/config/documentation inclusion list.
scripts/check-release.ps1 checks presence; -RequireTracked also fails if clean checkout would omit required files.
No staging or committing is performed by either script.
Required production source includes backend/src/main and frontend/src; test support includes backend/src/test,
frontend tests and test helpers. Required configuration includes all migrations, Maven/Vite/TypeScript manifests,
lockfiles, Docker files, example environments and the CI workflow.
Local-only: real .env files and IDE settings. Generated: target, node_modules, dist and test reports.
Must not ship: credentials, private tokens, database dumps, patient data, generated debug logs.
No significant unclassified source item was found in this review.

CI runs tests/package, requires database integration not to skip, builds the frontend, and audits both ecosystems.
It does not deploy. Live AI is explicitly opt-in and excluded from ordinary CI.
Run mvn clean verify, npm test, VITE_API_BASE_URL=/ npm run build, npm audit,
Maven dependency:tree then scripts/dependency-audit.ps1, secret scanning, and the security smoke script.
Rebuild/recreate only the backend with docker compose build backend and docker compose up -d --no-deps backend.
Never run down -v or delete the PostgreSQL volume. Verify Flyway, health, auth/RBAC and persistence afterward.
Backup/restore rehearsal, production TLS/edge configuration, operational monitoring and real-vendor acceptance
remain operator release gates, even when local regression passes.
