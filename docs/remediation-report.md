# Pre-production remediation report

Date: 2026-09-26. Scope: current working tree, not merely HEAD. No staging, commit, push,
database reset, volume replacement, or demo reseeding was performed.

## 1. Executive summary

**NO-GO for production today.** The confirmed source defects were remediated and regression-tested,
but release inclusion, production deployment configuration, and real AI acceptance remain open.

Outstanding: P0 **0**, P1 **2** (F03, F06), P2 **1** (F09 deployment verification),
P3 **3** (documented operational limitations below).
F01, F02, F04, F05, F07 and F08 are fixed. F03 now has an exact release manifest and CI;
F09 has code safeguards but no configured/verified production deployment.
F06 is an external provider blocker: the fresh synthetic Gemini request returned HTTP 429.
AI can instead be deliberately disabled for initial production, with corresponding product acceptance.

## 2. Before versus after

| ID | Before | After | Evidence |
|---|---|---|---|
| F01 | Anonymous registration created enabled staff | ADMIN-only provisioning; guarded first-admin bootstrap; doctor-user linking | Security role matrix, bootstrap tests, real HTTP registration, live 401/403 |
| F02 | Boot 3.3.5 / Security 6.3.4 / Framework 6.1.14 | Boot 4.0.8 / Security 7.0.7 / Framework 7.0.9, Nimbus 10.10, springdoc 3.0.3, Tomcat 11.0.26 | Full tests, packaged build, 164-package OSV scan |
| F03 | 52 tracked modifications, 188 untracked files, no staged files; required frontend/migrations absent from HEAD | Exact 307-file release list, presence/tracking checker, minimal GitHub Actions workflow; still deliberately unstaged | release-files.txt; clean checkout remains incomplete |
| F04 | NoOp recorded SENT; disabled provider left PENDING | NoOp SIMULATED, disabled DISABLED, unavailable FAILED; no sent timestamp for simulation | Provider/service tests and persisted real-HTTP workflow records |
| F05 | Read-then-write overlap race | PostgreSQL doctor/patient exclusion constraints for active 30-minute slots | Real simultaneous transactions: exactly one commit, loser exclusion/deadlock; adjacent slot succeeds |
| F06 | Real provider not successfully verified | Fresh openai/gemini-3.8-flash attempt reached provider and received 429 | Separate opt-in smoke test: 1 test, 1 error; no mock replacement |
| F07 | No application abuse control | Bounded per-process login/refresh/provisioning/AI windows with JSON 429 | Filter regression; edge/multi-instance limitations documented |
| F08 | Server-default timezone | Configured clinic clock, unchanged wall-time storage, DST gap/overlap rejection | Clock injection, DST test, live UTC configuration |
| F09 | Production bundle could silently use localhost | Build requires explicit HTTPS or same-origin base; prod profile rejects unsafe providers/URLs | Positive build and negative missing-config build, production safety unit test; actual deployment pending |

Supporting compatibility sources: [Boot 3.5 OSS support end](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/),
[Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide),
[springdoc compatibility](https://springdoc.org/).
The OSV scan identified Tomcat CVE-2026-65905, CVE-2026-65182 and CVE-2026-68525 in 11.0.24;
11.0.26 removes those reported findings. The application's Bearer model does not use Tomcat DIGEST/FORM
authentication, but the vulnerable dependency was still patched rather than accepted on reachability assumptions.

## 3. Actual test results

| Gate | Result |
|---|---|
| Backend full regression | 373 tests: 0 failures, 0 errors, 1 skipped; 372 executed successfully |
| PostgreSQL integration subset | 7 tests: 0 failures/errors/skips, disposable PostgreSQL 18 |
| Frontend | 19 files, 144 tests passed |
| Backend package | PASS |
| Frontend TypeScript/production build | PASS with VITE_API_BASE_URL=/ |
| Missing production frontend config | Correctly rejected before bundling |
| npm audit | 0 vulnerabilities |
| Maven dependency audit | 164 distinct resolved packages, 0 affected packages after remediation |
| Gitleaks 8.30.1 | Source and all 7 Git commits inspected; 2 test-password fixture findings in each scan, triaged; no confirmed real secret |
| Flyway | V1-V10 successful; live startup validates all 10; only V9/V10 added |
| Docker | Multi-stage build PASS; backend and PostgreSQL healthy; runtime user app |
| Auth/RBAC | Real HTTP integration and live three-role checks pass |
| Isolation | Two-doctor real HTTP workflow checks patient/appointment/consultation/record/prescription IDs; live foreign-patient request 404 |
| E2E | Java HTTP client -> embedded Tomcat -> actual services -> disposable PostgreSQL, no mocked service/repository |
| Real AI | NOT VERIFIED: fresh real request returned provider 429 |
| Browser | NOT VERIFIED: tooling returned no browsers/apps |
| Security smoke script | 0 critical failures, 0 warnings, 5 skips; script labels result PASS WITH WARNINGS because of skips |
| Release | Required working files present; tracking gate intentionally fails until owner includes required files |

The sole normal-suite skip is the environment-gated RealAiProviderSmokeTests; database tests are not skipped.
The real-AI failure was a separate opt-in execution, not concealed in the regression totals.
The smoke skips are cross-doctor live checking, valid public-token flow, and its three duplicate automated gates.
Cross-doctor isolation and those automated suites were independently executed as described above;
a valid public-link live workflow was not newly exercised.

Commands executed (PowerShell uses mvn.cmd/npm.cmd):

- mvn -q clean test; final mvn -q test after credential-redaction regression additions.
- mvn -q package -DskipTests.
- npm run test; VITE_API_BASE_URL=/ npm run build; npm audit --json.
- mvn dependency:tree -DoutputType=json -DoutputFile=target/dependency-tree.json; scripts/dependency-audit.ps1.
- AI_LIVE_TEST=true mvn -q -Dtest=RealAiProviderSmokeTests test (synthetic notes only).
- docker compose build backend; docker compose up -d --no-deps backend; docker compose restart backend.
- docker compose ps; read-only psql aggregate/checksum/schema queries; HTTP checks.
- scripts/security-smoke-test.ps1 -SkipAutomatedTests with existing synthetic admin credentials.
- Gitleaks dir over a nonignored-source snapshot and Gitleaks git, both --redact.
- git diff --check; git status; release presence/tracking checks.

Initial upgrade tests exposed two previously skipped integration-test defects: an assertion expected an
in-progress consultation after explicitly completing it, and another compared detached entity identity.
Tests now assert the persisted completed state and exact patient UUID, preserving their intended behavior.
The smoke script also used email where LoginRequest requires username; that contract mismatch is corrected.

## 4. Security result

- Staff self-provisioning is closed at HTTP and method boundaries. Non-admin role manipulation is denied.
- BCrypt input is limited to 72 UTF-8 bytes, including multibyte regression coverage. Passwords remain hashed.
- JWT signature/algorithm/expiry/disabled-user checks and DB authority loading are preserved.
- Refresh hashing, pessimistic locking, rotation, logout and replay rejection remain intact.
- JWT filtering no longer catches downstream application exceptions and mislabels them as token errors.
- Security failures keep the existing sanitized JSON contract. Credentials in auth/config record diagnostics are redacted.
- CORS remains explicit without credentialed wildcards; prod URLs require HTTPS. Public capability links remain purpose-specific.
- CSRF remains disabled for the existing stateless Authorization Bearer model, not cookie authentication.
- Query parameters/Specifications remain parameterized. No injection exploit was identified; this is not a penetration-test certification.
- React escaping is preserved. localStorage tokens retain XSS exposure if a future injection occurs; no demonstrated XSS was found.
- Application rate limits are process-local and reset on restart; trusted-edge controls are still necessary.
- Secret scan covered nonignored release source and Git history, not an assertion that private .env contains no credentials.
- Local/development credentials are not production credentials; provision fresh production secrets and rotate any credential previously exposed elsewhere.

## 5. Database result

V1-V8 hashes were preserved; V9/V10 are new. Existing foreign keys, UUIDs, JPA auditing and ddl-auto=validate remain.
The Specification and PatientRepository EXISTS fixes are untouched. New GiST indexes exist specifically to enforce
doctor/patient slot exclusion. Concurrent conflict can be SQLSTATE 23P01 or PostgreSQL deadlock victim 40P01;
both roll back the losing write, and application conflict handling returns sanitized 409.

Clinical notifications run after successful commit in independent transactions. Rollback dispatch is tested;
real HTTP consultation completion persists simulated communications and feedback capability records.
A process crash before the callback can still lose a best-effort notification; no durable outbox is claimed.

Counts AND whole-row aggregate checksums match before/after backend rebuild/restart:
11 patients, 4 doctors, 18 appointments, 10 consultations, 6 medical records, 4 prescriptions, 5 prescription items.
The same named PostgreSQL volume remains mounted at /var/lib/postgresql.
Only migrations and expected auth-token lifecycle writes occurred in the live database; no clinical test data was created there.

Backup/restore rehearsal is NOT VERIFIED. Production migration requires btree_gist installation privileges
and a prior backup. Existing active overlaps were checked read-only: zero.
Production load/EXPLAIN benchmarking is NOT VERIFIED. Appointment EntityGraphs and bounded pages remain;
prescription-item loading can still issue additional per-prescription queries, and reminder scanning returns
all due rows in its window. These are documented sizing/performance limitations, not a claim of measured production capacity.

## 6. AI result

1. Real Gemini generation verified? **No**. Provider=openai, model=gemini-3.8-flash; actual call returned 429.
2. Synthetic data? **Yes**, a synthetic headache-note fixture, no live patient data.
3. 429 observed? **Yes**, explicit rate-limit exception; no response fabricated.
4. 503 retries verified? **Yes in automated local HTTP provider tests**, not a forced live vendor outage.
5. Failed calls safe? Sanitized errors and manual clinical workflow remain; tests cover these paths.
6. Mock clinical fallback? No automatic fallback from failed real provider calls; mock remains explicit development configuration.
7. API key protected? Environment-driven, not printed or bundled; diagnostic configuration strings redacted.
8. AI output persisted? Generation does not itself persist clinical records; clinician review/apply/save can persist reviewed notes.
9. Autonomous clinical updates? None added or authorized.

## 7. SMS result

1. Provider remains NoOp locally; real vendor integration is absent.
2. New NoOp records cannot claim SENT; they are SIMULATED with no sent timestamp.
3. Disabled SMS records DISABLED and makes no provider call; prod rejects enabled SMS until real integration exists.
4. Real delivery is NOT VERIFIED and not claimed. Historical SENT rows remain unmodified and are not reliable delivery evidence.

## 8. Release result

[Exact release inclusion list](release-files.txt): 307 files, including frontend, lockfile, all ten migrations,
source/tests, Docker/config examples, documentation, scripts and CI.
[Exact remediation changes](remediation-files.txt): 57 modified existing working-tree files and 23 additions.
This excludes earlier dirty work that was merely preserved.

A checkout of current HEAD still cannot reproduce this working application: required source remains untracked.
No staging was performed. Run scripts/check-release.ps1 -RequireTracked after the owner intentionally includes
the release list. CI is created locally but has NOT executed on GitHub.
Real .env/IDE settings are local-only; target/node_modules/dist are generated; secrets, dumps and patient data must not ship.
No significant unclassified nonignored source item remains. Do not archive the entire working directory as a release.

## 9. Findings table

| ID | Severity | Area | Finding / evidence | Status | Required action |
|---|---|---|---|---|---|
| F01 | P0 | Provisioning | Admin checks plus bootstrap/HTTP regression | FIXED | Configure one-time bootstrap only when needed |
| F02 | P1 | Dependencies | Supported line; zero OSV package findings | FIXED | Continue scheduled scanning and patching |
| F03 | P1 | Release | Required frontend/migrations still untracked | NOT VERIFIED | Owner review/inclusion, then clean-checkout CI |
| F04 | P1 | SMS | Explicit simulated/disabled persisted states | FIXED | Keep production SMS disabled |
| F05 | P1 | Booking | PostgreSQL exclusions and concurrent test | FIXED | Monitor/retry controlled conflicts |
| F06 | P1 | AI | Fresh provider HTTP 429 | EXTERNAL BLOCKER | Resolve quota/availability and verify, or disable AI |
| F07 | P2 | Abuse | Bounded filter + regression | FIXED | Deploy edge limits for real client IPs/replicas |
| F08 | P2 | Time | Clinic clock and DST validation | FIXED | Confirm intended zone before deployment |
| F09 | P2 | Deployment | Code guards exist; no actual production deployment | NOT VERIFIED | Supply prod secrets/URLs/zone; verify TLS and startup |
| L01 | P3 | Browser tokens | localStorage retains XSS impact | DEFERRED | Keep script/CSP controls; evaluate separately |
| L02 | P3 | Notifications | Post-commit best-effort crash window | DEFERRED | Accept optional SMS limitations or plan durable delivery |
| L03 | P3 | Capacity | No representative load/query benchmark | NOT VERIFIED | Measure realistic volume before sizing |

## 10. Deployment blockers

- Required files are not yet part of a reproducible reviewed checkout; GitHub CI has not run.
- AI must either pass a fresh real-provider acceptance check or be intentionally disabled for release.
- Production secrets, HTTPS origins/proxy, clinic zone, disabled demo/SMS configuration and backup/restore procedure
  need deployment-specific verification. Local Docker success does not establish those controls.
- Browser workflow/visual acceptance remains unverified because no browser surface was available.

## 11. Final checklist

- [x] Secure staff provisioning
- [x] Supported dependency line
- [ ] Required release files included in a clean checkout
- [x] SMS behavior safe
- [x] Appointment concurrency safe
- [x] Patient isolation verified
- [ ] Production configuration complete and deployed
- [x] Secret scan reviewed: no confirmed real source/history secrets
- [x] Dependency scan acceptable
- [x] Backend tests pass (live-provider probe reported separately)
- [x] Frontend tests pass
- [x] Builds pass
- [x] Flyway validated
- [x] Docker verified
- [x] Core real-HTTP E2E verified
- [ ] AI verified or intentionally disabled in production
- [x] Documentation updated
- [x] Release tree reviewed
- [ ] Browser visual acceptance
- [ ] Backup/restore rehearsal

## 12. Final decision

**NO-GO.** The code remediation and local integration gates passed, but a production release cannot be
declared complete while the clean checkout omits required files, real AI fails unless explicitly disabled,
and production/browser/restore acceptance remains unverified. No additional architecture is required to
close those gates. See production-deployment.md for the exact operator configuration and release workflow.

## 13. Final closure update (2026-09-26)

This section supersedes the release, restore, configuration, and decision statements above where they
conflict. It records the final read/write verification work without claiming an external deployment.

- **F03 release content:** The reviewed release manifest was staged without committing it. A clean export
  of the Git index contained all 307 manifest files (zero missing files) and completed backend packaging
  plus a fresh frontend dependency install and production build. The release content is reproducible from
  the staged index; the owner must still review and commit it before GitHub CI can validate the actual
  commit.
- **F09 production configuration:** `FRONTEND_API_URL` is now explicitly required by the production
  profile and is validated as HTTPS along with the existing production URLs. Root and backend examples,
  Compose forwarding, and deployment instructions document it. An isolated production-profile backend
  started successfully only with explicit non-local, HTTPS configuration.
- **Backup and restore:** A custom-format backup of the existing local PostgreSQL database was restored
  into a separately named temporary PostgreSQL container and volume. Flyway validated all V1--V10
  migrations as current. Counts and aggregate checksums for patients, doctors, appointments,
  consultations, medical records, prescriptions, and prescription items matched the source. The live
  database, its volume, and clinical data were never reset or modified by this rehearsal.
- **Swagger/resource handling:** The isolated production rehearsal found that a missing static resource
  such as `/v3/api-docs` could be converted to a generic `500`. It now produces the project's sanitized
  `404` response, with a regression test. This does not expose a stack trace or resource internals.
- **AI status:** The optional real-provider capability remains **AI SAFELY DISABLED — PROVIDER PENDING**
  in production defaults. One synthetic-data Gemini attempt was rate-limited (`429`); no fresh retry is
  claimed. Core patient-management workflows do not require AI to operate.
- **SMS status:** Development remains NoOp/simulated. Production defaults disable SMS and reject enabling
  it without a real provider. No delivery claim is made.
- **Browser acceptance:** **NOT AVAILABLE**. No browser automation surface was available, so visual and
  interactive browser acceptance is not claimed.

### Updated finding status

| ID | Updated status | Closure evidence / remaining condition |
|---|---|---|
| F03 | VERIFIED (staged release candidate) | Clean index export built successfully; requires owner commit and normal CI execution. |
| F06 | SAFE DISABLED | AI is optional and disabled by the production profile pending vendor quota/provider acceptance. |
| F09 | VERIFIED LOCALLY | Production profile and isolated startup verified; operator must provide real HTTPS DNS/TLS and secrets. |
| Backup/restore | VERIFIED LOCALLY | Isolated restore plus Flyway/history and clinical-data comparison passed. |
| Browser | NOT AVAILABLE | Requires later browser-based acceptance. |

### Updated decision

**GO WITH CONDITIONS.** The reviewed staged release candidate passes local build, test, database-restore,
and isolated production-startup gates. Before a real deployment, the owner must commit the reviewed staged
content, let CI run, provide production secrets and real HTTPS/TLS infrastructure, keep demo data and SMS
disabled, and retain AI disabled until a successful synthetic provider acceptance test is available.
