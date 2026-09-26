# AI and Communications

## AI Clinical Assistant

The AI clinical assistant is limited to authorized ADMIN and DOCTOR users and offers two advisory operations:

- `POST /api/v1/consultations/{consultationId}/ai/draft` generates a structured consultation draft from rough notes and explicitly supplied current fields.
- `GET /api/v1/patients/{patientId}/ai/summary` generates a patient-history summary from authorized patient data.

AI output is not persisted automatically. The clinician must review it, optionally apply it to the form, edit it as appropriate, and explicitly save through the existing clinical workflow. Clinora does not use AI for autonomous diagnoses, prescriptions, triage, or clinical-record writes.

### Providers and Configuration

`AiProvider` supports the local deterministic `mock` provider and an `openai` provider for an explicitly configured OpenAI-compatible API. Real-provider configuration requires `AI_ENABLED=true`, `AI_PROVIDER=openai`, an HTTPS base URL, model, timeout, and server-side API key.

The real provider retries a `503` at most twice with bounded backoff. It does not blindly retry `429` responses and does not substitute mock-generated clinical content after a real-provider failure. Failures return the application's AI-unavailable response so the clinician can continue manually.

**Production status:** AI is disabled by default in the `prod` profile. A real provider must pass a synthetic-data acceptance test and an appropriate clinical-data/privacy review before being enabled.

## Communications

`CommunicationService` records a privacy-conscious communication event and dispatches through `CommunicationProvider`. The local provider is `NoOpSmsCommunicationProvider`; no real vendor adapter is implemented.

Supported communication types are:

- appointment confirmation after appointment creation
- appointment reminder for due scheduled/confirmed appointments
- prescription availability after prescription creation
- consultation completion after a consultation is completed
- feedback-request support for completed consultations

The reminder scheduler uses the configured look-ahead window and avoids duplicate active reminder records for an appointment.

### Status Semantics

| Status | Meaning |
|---|---|
| `SIMULATED` | Local NoOp provider accepted a simulated send; no external SMS was delivered |
| `DISABLED` | SMS is disabled; no provider call was made |
| `FAILED` | Dispatch could not be completed, for example due to missing recipient/provider |
| `SENT` | Reserved for future real-provider acceptance, not handset delivery |
| `DELIVERED` | Reserved for future vendor delivery confirmation |

Communication notifications run after the related clinical transaction commits. A notification failure does not roll back the committed clinical change. This is best-effort behavior, not a durable outbox or queue; a process crash in the post-commit window can lose a notification.

### Privacy and Production Status

SMS messages avoid diagnosis, symptoms, medical-record text, medication details, AI output, tokens, passwords, and internal identifiers. The production profile disables SMS by default and rejects enabled SMS until a real provider is implemented and verified. NoOp simulation must never be interpreted as real vendor or handset delivery.
