import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import * as aiApi from "../api/aiApi";
import * as patientApi from "../api/patientApi";
import { useAuth } from "../auth/AuthContext";
import { ConfirmDialog } from "../components/ConfirmDialog";
import type { FormEvent } from "react";
import type { AiPatientHistorySummaryResponse } from "../types/ai";
import type { PageResponse } from "../types/api";
import type { BloodGroup, Patient, PatientGender, PatientRequest } from "../types/patient";
import { formatDate, formatEnum } from "../utils/formatters";
import { optionalEmailError, personNameError, phoneError, type FieldErrors, validationResult } from "../utils/validation";

type LoadState =
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<Patient>; error: null }
  | { status: "error"; page: null; error: string };

type FormMode = "create" | "edit";

type PatientSummaryState =
  | { status: "idle"; patient: null; response: null; error: null }
  | { status: "loading"; patient: Patient; response: null; error: null }
  | { status: "loaded"; patient: Patient; response: AiPatientHistorySummaryResponse; error: null }
  | { status: "error"; patient: Patient; response: null; error: string };

interface FormState {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: PatientGender | "";
  bloodGroup: BloodGroup | "";
  phone: string;
  email: string;
  address: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
}

const emptyForm: FormState = {
  firstName: "",
  lastName: "",
  dateOfBirth: "",
  gender: "",
  bloodGroup: "",
  phone: "",
  email: "",
  address: "",
  emergencyContactName: "",
  emergencyContactPhone: ""
};

const genderOptions: PatientGender[] = ["FEMALE", "MALE", "OTHER", "UNKNOWN"];
const bloodGroupOptions: BloodGroup[] = [
  "A_POSITIVE",
  "A_NEGATIVE",
  "B_POSITIVE",
  "B_NEGATIVE",
  "AB_POSITIVE",
  "AB_NEGATIVE",
  "O_POSITIVE",
  "O_NEGATIVE"
];

export function PatientsPage() {
  const auth = useAuth();
  const canWrite = auth.hasAnyRole(["ADMIN", "RECEPTIONIST"]);
  const canDelete = auth.hasAnyRole(["ADMIN"]);
  const canUseAiSummary = auth.hasAnyRole(["ADMIN", "DOCTOR"]);
  const [state, setState] = useState<LoadState>({ status: "loading", page: null, error: null });
  const [formMode, setFormMode] = useState<FormMode | null>(null);
  const [editingPatient, setEditingPatient] = useState<Patient | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formSuccess, setFormSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Patient | null>(null);
  const [summaryState, setSummaryState] = useState<PatientSummaryState>({
    status: "idle",
    patient: null,
    response: null,
    error: null
  });

  const loadPatients = useCallback(() => {
    setState({ status: "loading", page: null, error: null });
    patientApi.listPatients()
      .then((page) => setState({ status: "loaded", page, error: null }))
      .catch((error: unknown) => setState({ status: "error", page: null, error: toMessage(error) }));
  }, []);

  useEffect(() => {
    loadPatients();
  }, [loadPatients]);

  const patients = state.status === "loaded" ? state.page.content : [];
  const headingActions = useMemo(() => {
    if (!canWrite) {
      return null;
    }
    return (
      <button type="button" className="primary-button" onClick={startCreate}>
        New patient
      </button>
    );
  }, [canWrite]);

  function startCreate() {
    setFormMode("create");
    setEditingPatient(null);
    setForm(emptyForm);
    setFormError(null);
    setFieldErrors({});
    setFormSuccess(null);
  }

  function startEdit(patient: Patient) {
    setFormMode("edit");
    setEditingPatient(patient);
    setForm(formFromPatient(patient));
    setFormError(null);
    setFieldErrors({});
    setFormSuccess(null);
  }

  function closeForm(clearMessage = true) {
    setFormMode(null);
    setEditingPatient(null);
    setForm(emptyForm);
    setFormError(null);
    if (clearMessage) {
      setFormSuccess(null);
    }
    setIsSaving(false);
  }

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setFormSuccess(null);

    const validation = validateForm(form);
    if (validation) {
      setFormError(validation.message);
      setFieldErrors(validation.fields);
      return;
    }
    setFieldErrors({});

    const request = toPatientRequest(form);
    setIsSaving(true);
    try {
      const savedPatient = formMode === "edit" && editingPatient
        ? await patientApi.updatePatient(editingPatient.id, request)
        : await patientApi.createPatient(request);

      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }

        const content = formMode === "edit"
          ? current.page.content.map((patient) => patient.id === savedPatient.id ? savedPatient : patient)
          : [savedPatient, ...current.page.content];
        return {
          status: "loaded",
          page: {
            ...current.page,
            content,
            totalElements: formMode === "edit" ? current.page.totalElements : current.page.totalElements + 1
          },
          error: null
        };
      });
      setFormSuccess(formMode === "edit" ? "Patient updated" : "Patient created");
      closeForm(false);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function requestDelete(patient: Patient) {
    setFormError(null);
    setFormSuccess(null);
    setPendingDelete(patient);
  }

  async function handleDelete() {
    if (!pendingDelete) {
      return;
    }

    setDeletingId(pendingDelete.id);
    setFormError(null);
    setFormSuccess(null);
    try {
      await patientApi.deletePatient(pendingDelete.id);
      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }
        return {
          status: "loaded",
          page: {
            ...current.page,
            content: current.page.content.filter((item) => item.id !== pendingDelete.id),
            totalElements: Math.max(0, current.page.totalElements - 1)
          },
          error: null
        };
      });
      setFormSuccess("Patient deleted");
      setPendingDelete(null);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setDeletingId(null);
    }
  }

  async function generatePatientSummary(patient: Patient) {
    setSummaryState({ status: "loading", patient, response: null, error: null });
    try {
      const response = await aiApi.generatePatientHistorySummary(patient.id);
      setSummaryState({ status: "loaded", patient, response, error: null });
    } catch (error: unknown) {
      setSummaryState({ status: "error", patient, response: null, error: toAiSummaryMessage(error) });
    }
  }

  function closeSummary() {
    setSummaryState({ status: "idle", patient: null, response: null, error: null });
  }

  return (
    <section className="resource-page" aria-labelledby="patients-heading">
      <div className="page-heading page-heading-row">
        <div>
          <p className="eyebrow">Patient management</p>
          <h1 id="patients-heading">Patients</h1>
          <p className="page-description">Register, review, and maintain patient profiles.</p>
        </div>
        {headingActions}
      </div>

      {formSuccess && (
        <p className="form-success" role="status">
          {formSuccess}
        </p>
      )}

      {formMode && canWrite && (
        <section className="form-panel" aria-labelledby="patient-form-heading">
          <div className="section-heading section-heading-row">
            <h2 id="patient-form-heading">{formMode === "edit" ? "Edit patient" : "New patient"}</h2>
            <button type="button" className="secondary-button" onClick={() => closeForm()}>
              Cancel
            </button>
          </div>

          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}

          <PatientForm
                form={form}
                errors={fieldErrors}
            isSaving={isSaving}
            submitLabel={formMode === "edit" ? "Save changes" : "Create patient"}
            onChange={setForm}
            onSubmit={(event) => void submitForm(event)}
          />
        </section>
      )}

      {!formMode && formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      {state.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading...
        </div>
      )}

      {state.status === "error" && (
        <div className="form-error" role="alert">
          {state.error}
        </div>
      )}

      {state.status === "loaded" && patients.length === 0 && (
        <div className="resource-state empty-state">
          <strong>No patients found.</strong>
          <span>New patient records will appear here once they are created.</span>
        </div>
      )}

      {state.status === "loaded" && patients.length > 0 && (
        <div className="table-panel">
          <table className="resource-table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Date of birth</th>
                <th scope="col">Gender</th>
                <th scope="col">Blood group</th>
                <th scope="col">Phone</th>
                <th scope="col">Email</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {patients.map((patient) => (
                <tr key={patient.id}>
                  <td>
                    <Link className="table-primary entity-link" to={`/patients/${patient.id}`}>
                      {patient.firstName} {patient.lastName}
                    </Link>
                  </td>
                  <td>{formatDate(patient.dateOfBirth)}</td>
                  <td><span className="status-badge neutral">{formatEnum(patient.gender)}</span></td>
                  <td>{patient.bloodGroup ? <span className="status-badge">{formatEnum(patient.bloodGroup)}</span> : "-"}</td>
                  <td>{patient.phone}</td>
                  <td>{patient.email ?? "-"}</td>
                  <td>
                    <div className="row-actions">
                      <Link className="secondary-button compact-button" to={`/patients/${patient.id}`}>
                        View profile
                      </Link>
                        {canUseAiSummary && (
                          <button
                            type="button"
                            className="secondary-button compact-button"
                            onClick={() => void generatePatientSummary(patient)}
                          >
                            AI Summary
                          </button>
                        )}
                        {canWrite && (
                          <button type="button" className="secondary-button compact-button" onClick={() => startEdit(patient)}>
                            Edit
                          </button>
                        )}
                        {canDelete && (
                          <button
                            type="button"
                            className="secondary-button danger-button compact-button"
                            disabled={deletingId === patient.id}
                            onClick={() => requestDelete(patient)}
                          >
                            {deletingId === patient.id ? "Deleting..." : "Delete"}
                          </button>
                        )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pendingDelete && (
        <ConfirmDialog
          title="Delete patient"
          message={`Delete ${pendingDelete.firstName} ${pendingDelete.lastName}? This removes the patient profile from the current system.`}
          confirmLabel="Delete patient"
          confirmingLabel="Deleting..."
          isConfirming={deletingId === pendingDelete.id}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void handleDelete()}
        />
      )}

      {summaryState.status !== "idle" && (
        <PatientSummaryPanel state={summaryState} onClose={closeSummary} onRetry={() => {
          if (summaryState.patient) {
            void generatePatientSummary(summaryState.patient);
          }
        }} />
      )}
    </section>
  );
}

interface PatientFormProps {
  form: FormState;
  errors: FieldErrors;
  isSaving: boolean;
  submitLabel: string;
  onChange: (form: FormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

function PatientForm({ form, errors, isSaving, submitLabel, onChange, onSubmit }: PatientFormProps) {
  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    onChange({ ...form, [field]: value });
  }

  return (
    <form onSubmit={onSubmit}>
      <div className="field-grid">
        <div className="field-group">
          <label htmlFor="patient-first-name">First name</label>
          <input
            id="patient-first-name"
            value={form.firstName}
            maxLength={100}
            onChange={(event) => updateField("firstName", event.target.value)}
          />
          {errors.firstName && <p className="field-error" role="status">{errors.firstName}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-last-name">Last name</label>
          <input
            id="patient-last-name"
            value={form.lastName}
            maxLength={100}
            onChange={(event) => updateField("lastName", event.target.value)}
          />
          {errors.lastName && <p className="field-error" role="status">{errors.lastName}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-date-of-birth">Date of birth</label>
          <input
            id="patient-date-of-birth"
            type="date"
            value={form.dateOfBirth}
            onChange={(event) => updateField("dateOfBirth", event.target.value)}
          />
          {errors.dateOfBirth && <p className="field-error" role="status">{errors.dateOfBirth}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-gender">Gender</label>
          <select
            id="patient-gender"
            value={form.gender}
            onChange={(event) => updateField("gender", event.target.value as PatientGender | "")}
          >
            <option value="">Select gender</option>
            {genderOptions.map((gender) => (
              <option key={gender} value={gender}>{formatEnum(gender)}</option>
            ))}
          </select>
          {errors.gender && <p className="field-error" role="status">{errors.gender}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-blood-group">Blood group</label>
          <select
            id="patient-blood-group"
            value={form.bloodGroup}
            onChange={(event) => updateField("bloodGroup", event.target.value as BloodGroup | "")}
          >
            <option value="">Not recorded</option>
            {bloodGroupOptions.map((bloodGroup) => (
              <option key={bloodGroup} value={bloodGroup}>{formatEnum(bloodGroup)}</option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="patient-phone">Phone</label>
          <input
            id="patient-phone"
            value={form.phone}
            maxLength={25}
            onChange={(event) => updateField("phone", event.target.value)}
          />
          {errors.phone && <p className="field-error" role="status">{errors.phone}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-email">Email</label>
          <input
            id="patient-email"
            type="email"
            value={form.email}
            maxLength={255}
            onChange={(event) => updateField("email", event.target.value)}
          />
          {errors.email && <p className="field-error" role="status">{errors.email}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="patient-emergency-phone">Emergency phone</label>
          <input
            id="patient-emergency-phone"
            value={form.emergencyContactPhone}
            maxLength={25}
            onChange={(event) => updateField("emergencyContactPhone", event.target.value)}
          />
          {errors.emergencyContactPhone && <p className="field-error" role="status">{errors.emergencyContactPhone}</p>}
        </div>
      </div>

      <div className="field-group">
        <label htmlFor="patient-emergency-name">Emergency contact</label>
        <input
          id="patient-emergency-name"
          value={form.emergencyContactName}
          maxLength={150}
          onChange={(event) => updateField("emergencyContactName", event.target.value)}
        />
        {errors.emergencyContactName && <p className="field-error" role="status">{errors.emergencyContactName}</p>}
      </div>

      <div className="field-group">
        <label htmlFor="patient-address">Address</label>
        <textarea
          id="patient-address"
          value={form.address}
          maxLength={500}
          rows={3}
          onChange={(event) => updateField("address", event.target.value)}
        />
      </div>

      <div className="form-actions">
        <button type="submit" className="primary-button" disabled={isSaving}>
          {isSaving ? "Saving..." : submitLabel}
        </button>
      </div>
    </form>
  );
}

interface PatientSummaryPanelProps {
  state: PatientSummaryState;
  onClose: () => void;
  onRetry: () => void;
}

function PatientSummaryPanel({ state, onClose, onRetry }: PatientSummaryPanelProps) {
  return (
    <section className="form-panel" aria-labelledby="patient-summary-heading">
      <div className="section-heading section-heading-row">
        <div>
          <h2 id="patient-summary-heading">Clinora AI Summary</h2>
          {state.patient && (
            <p className="page-description">
              {state.patient.firstName} {state.patient.lastName}
            </p>
          )}
        </div>
        <button type="button" className="secondary-button" onClick={onClose}>
          Close
        </button>
      </div>

      {state.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Generating AI summary...
        </div>
      )}

      {state.status === "error" && (
        <>
          <p className="form-error" role="alert">
            {state.error}
          </p>
          <div className="form-actions">
            <button type="button" className="secondary-button" onClick={onRetry}>
              Retry
            </button>
          </div>
        </>
      )}

      {state.status === "loaded" && (
        <div className="ai-output-panel">
          <p className="status-line">
            <span className="status-badge neutral">Summary</span>
            <span>{state.response.notice}</span>
          </p>
          <p>{state.response.summary}</p>
          <AiSummarySection title="Recent Clinical Activity" items={state.response.recentClinicalActivity} />
          <AiSummarySection title="Medical History" items={state.response.documentedHistory} />
          <AiSummarySection title="Recent Prescriptions" items={state.response.recentPrescriptions} />
          <AiSummarySection title="Follow-up" items={state.response.followUp} />
        </div>
      )}
    </section>
  );
}

function AiSummarySection({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) {
    return null;
  }

  return (
    <section className="ai-summary-section" aria-label={title}>
      <h3>{title}</h3>
      <ul>
        {items.map((item, index) => (
          <li key={`${title}-${index}`}>{item}</li>
        ))}
      </ul>
    </section>
  );
}

function formFromPatient(patient: Patient): FormState {
  return {
    firstName: patient.firstName,
    lastName: patient.lastName,
    dateOfBirth: patient.dateOfBirth,
    gender: patient.gender,
    bloodGroup: patient.bloodGroup ?? "",
    phone: patient.phone,
    email: patient.email ?? "",
    address: patient.address ?? "",
    emergencyContactName: patient.emergencyContactName ?? "",
    emergencyContactPhone: patient.emergencyContactPhone ?? ""
  };
}

function toPatientRequest(form: FormState): PatientRequest {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    dateOfBirth: form.dateOfBirth,
    gender: form.gender as PatientGender,
    bloodGroup: form.bloodGroup === "" ? null : form.bloodGroup,
    phone: form.phone.trim(),
    email: nullableTrim(form.email),
    address: nullableTrim(form.address),
    emergencyContactName: nullableTrim(form.emergencyContactName),
    emergencyContactPhone: nullableTrim(form.emergencyContactPhone)
  };
}

function validateForm(form: FormState) {
  const fields: FieldErrors = {};
  const firstNameError = personNameError(form.firstName, "First name");
  const lastNameError = personNameError(form.lastName, "Last name");
  const emailError = optionalEmailError(form.email);
  if (firstNameError) fields.firstName = firstNameError;
  if (lastNameError) fields.lastName = lastNameError;
  if (!form.dateOfBirth) fields.dateOfBirth = "Date of birth is required";
  else if (form.dateOfBirth >= todayDateInputValue()) fields.dateOfBirth = "Date of birth must be in the past";
  if (!form.gender) fields.gender = "Gender is required";
  const phoneMessage = phoneError(form.phone);
  if (phoneMessage) fields.phone = phoneMessage;
  if (emailError) fields.email = emailError;
  const emergencyPhoneMessage = form.emergencyContactPhone.trim() ? phoneError(form.emergencyContactPhone, "Emergency phone") : null;
  if (emergencyPhoneMessage) fields.emergencyContactPhone = emergencyPhoneMessage;
  const emergencyNameError = form.emergencyContactName.trim() ? personNameError(form.emergencyContactName, "Emergency contact") : null;
  if (emergencyNameError) fields.emergencyContactName = emergencyNameError;
  return validationResult(fields);
}

function nullableTrim(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function todayDateInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load patients";
}

function toAiSummaryMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "AI assistance is temporarily unavailable. You can continue using the patient record manually.";
}
