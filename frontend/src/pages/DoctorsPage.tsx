import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiError } from "../api/apiClient";
import * as doctorApi from "../api/doctorApi";
import { useAuth } from "../auth/AuthContext";
import { ConfirmDialog } from "../components/ConfirmDialog";
import type { FormEvent } from "react";
import type { PageResponse } from "../types/api";
import type { Doctor, DoctorRequest } from "../types/doctor";
import { optionalEmailError, personNameError, phoneError, textError, type FieldErrors, validationResult } from "../utils/validation";

type LoadState =
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<Doctor>; error: null }
  | { status: "error"; page: null; error: string };

type FormMode = "create" | "edit";

interface FormState {
  firstName: string;
  lastName: string;
  specialization: string;
  licenseNumber: string;
  phone: string;
  email: string;
  department: string;
}

const emptyForm: FormState = {
  firstName: "",
  lastName: "",
  specialization: "",
  licenseNumber: "",
  phone: "",
  email: "",
  department: ""
};

export function DoctorsPage() {
  const auth = useAuth();
  const canManageDoctors = auth.hasAnyRole(["ADMIN"]);
  const [state, setState] = useState<LoadState>({ status: "loading", page: null, error: null });
  const [formMode, setFormMode] = useState<FormMode | null>(null);
  const [editingDoctor, setEditingDoctor] = useState<Doctor | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formSuccess, setFormSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Doctor | null>(null);

  const loadDoctors = useCallback(() => {
    setState({ status: "loading", page: null, error: null });
    doctorApi.listDoctors()
      .then((page) => setState({ status: "loaded", page, error: null }))
      .catch((error: unknown) => setState({ status: "error", page: null, error: toMessage(error) }));
  }, []);

  useEffect(() => {
    loadDoctors();
  }, [loadDoctors]);

  const doctors = state.status === "loaded" ? state.page.content : [];
  const headingActions = useMemo(() => {
    if (!canManageDoctors) {
      return null;
    }
    return (
      <button type="button" className="primary-button" onClick={startCreate}>
        New doctor
      </button>
    );
  }, [canManageDoctors]);

  function startCreate() {
    setFormMode("create");
    setEditingDoctor(null);
    setForm(emptyForm);
    setFormError(null);
    setFieldErrors({});
    setFormSuccess(null);
  }

  function startEdit(doctor: Doctor) {
    setFormMode("edit");
    setEditingDoctor(doctor);
    setForm(formFromDoctor(doctor));
    setFormError(null);
    setFieldErrors({});
    setFormSuccess(null);
  }

  function closeForm(clearMessage = true) {
    setFormMode(null);
    setEditingDoctor(null);
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

    const request = toDoctorRequest(form);
    setIsSaving(true);
    try {
      const savedDoctor = formMode === "edit" && editingDoctor
        ? await doctorApi.updateDoctor(editingDoctor.id, request)
        : await doctorApi.createDoctor(request);

      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }

        const content = formMode === "edit"
          ? current.page.content.map((doctor) => doctor.id === savedDoctor.id ? savedDoctor : doctor)
          : [savedDoctor, ...current.page.content];
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
      setFormSuccess(formMode === "edit" ? "Doctor updated" : "Doctor created");
      closeForm(false);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function requestDelete(doctor: Doctor) {
    setFormError(null);
    setFormSuccess(null);
    setPendingDelete(doctor);
  }

  async function handleDelete() {
    if (!pendingDelete) {
      return;
    }

    setDeletingId(pendingDelete.id);
    setFormError(null);
    setFormSuccess(null);
    try {
      await doctorApi.deleteDoctor(pendingDelete.id);
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
      setFormSuccess("Doctor deleted");
      setPendingDelete(null);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <section className="resource-page" aria-labelledby="doctors-heading">
      <div className="page-heading page-heading-row">
        <div>
          <p className="eyebrow">Doctor directory</p>
          <h1 id="doctors-heading">Doctors</h1>
          <p className="page-description">Review clinician profiles, specialties, departments, and license details.</p>
        </div>
        {headingActions}
      </div>

      {formSuccess && (
        <p className="form-success" role="status">
          {formSuccess}
        </p>
      )}

      {formMode && canManageDoctors && (
        <section className="form-panel" aria-labelledby="doctor-form-heading">
          <div className="section-heading section-heading-row">
            <h2 id="doctor-form-heading">{formMode === "edit" ? "Edit doctor" : "New doctor"}</h2>
            <button type="button" className="secondary-button" onClick={() => closeForm()}>
              Cancel
            </button>
          </div>

          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}

          <DoctorForm
            form={form}
            errors={fieldErrors}
            isSaving={isSaving}
            submitLabel={formMode === "edit" ? "Save changes" : "Create doctor"}
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

      {state.status === "loaded" && doctors.length === 0 && (
        <div className="resource-state empty-state">
          <strong>No doctors found.</strong>
          <span>Doctor profiles will appear here once an administrator creates them.</span>
        </div>
      )}

      {state.status === "loaded" && doctors.length > 0 && (
        <div className="table-panel">
          <table className="resource-table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Specialization</th>
                <th scope="col">Department</th>
                <th scope="col">License</th>
                <th scope="col">Phone</th>
                <th scope="col">Email</th>
                {canManageDoctors && <th scope="col">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {doctors.map((doctor) => (
                <tr key={doctor.id}>
                  <td>
                    <span className="table-primary">{doctor.firstName} {doctor.lastName}</span>
                  </td>
                  <td><span className="status-badge">{doctor.specialization}</span></td>
                  <td>{doctor.department ?? "-"}</td>
                  <td><code className="inline-code">{doctor.licenseNumber}</code></td>
                  <td>{doctor.phone}</td>
                  <td>{doctor.email ?? "-"}</td>
                  {canManageDoctors && (
                    <td>
                      <div className="row-actions">
                        <button type="button" className="primary-button compact-button" onClick={() => startEdit(doctor)}>
                          Edit
                        </button>
                        <button
                          type="button"
                          className="danger-button compact-button"
                          disabled={deletingId === doctor.id}
                          onClick={() => requestDelete(doctor)}
                        >
                          {deletingId === doctor.id ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pendingDelete && (
        <ConfirmDialog
          title="Delete doctor"
          message={`Delete Dr. ${pendingDelete.firstName} ${pendingDelete.lastName}? This removes the doctor profile from the current system.`}
          confirmLabel="Delete doctor"
          confirmingLabel="Deleting..."
          isConfirming={deletingId === pendingDelete.id}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void handleDelete()}
        />
      )}
    </section>
  );
}

interface DoctorFormProps {
  form: FormState;
  errors: FieldErrors;
  isSaving: boolean;
  submitLabel: string;
  onChange: (form: FormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

function DoctorForm({ form, errors, isSaving, submitLabel, onChange, onSubmit }: DoctorFormProps) {
  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    onChange({ ...form, [field]: value });
  }

  return (
    <form onSubmit={onSubmit}>
      <div className="field-grid">
        <div className="field-group">
          <label htmlFor="doctor-first-name">First name</label>
          <input
            id="doctor-first-name"
            value={form.firstName}
            maxLength={100}
            onChange={(event) => updateField("firstName", event.target.value)}
          />
          {errors.firstName && <p className="field-error" role="status">{errors.firstName}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="doctor-last-name">Last name</label>
          <input
            id="doctor-last-name"
            value={form.lastName}
            maxLength={100}
            onChange={(event) => updateField("lastName", event.target.value)}
          />
          {errors.lastName && <p className="field-error" role="status">{errors.lastName}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="doctor-specialization">Specialization</label>
          <input
            id="doctor-specialization"
            value={form.specialization}
            maxLength={150}
            onChange={(event) => updateField("specialization", event.target.value)}
          />
          {errors.specialization && <p className="field-error" role="status">{errors.specialization}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="doctor-license-number">License number</label>
          <input
            id="doctor-license-number"
            value={form.licenseNumber}
            maxLength={100}
            onChange={(event) => updateField("licenseNumber", event.target.value)}
          />
          {errors.licenseNumber && <p className="field-error" role="status">{errors.licenseNumber}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="doctor-phone">Phone</label>
          <input
            id="doctor-phone"
            value={form.phone}
            maxLength={25}
            onChange={(event) => updateField("phone", event.target.value)}
          />
          {errors.phone && <p className="field-error" role="status">{errors.phone}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="doctor-email">Email</label>
          <input
            id="doctor-email"
            type="email"
            value={form.email}
            maxLength={255}
            onChange={(event) => updateField("email", event.target.value)}
          />
          {errors.email && <p className="field-error" role="status">{errors.email}</p>}
        </div>
      </div>

      <div className="field-group">
        <label htmlFor="doctor-department">Department</label>
        <input
          id="doctor-department"
          value={form.department}
          maxLength={150}
          onChange={(event) => updateField("department", event.target.value)}
        />
        {errors.department && <p className="field-error" role="status">{errors.department}</p>}
      </div>

      <div className="form-actions">
        <button type="submit" className="primary-button" disabled={isSaving}>
          {isSaving ? "Saving..." : submitLabel}
        </button>
      </div>
    </form>
  );
}

function formFromDoctor(doctor: Doctor): FormState {
  return {
    firstName: doctor.firstName,
    lastName: doctor.lastName,
    specialization: doctor.specialization,
    licenseNumber: doctor.licenseNumber,
    phone: doctor.phone,
    email: doctor.email ?? "",
    department: doctor.department ?? ""
  };
}

function toDoctorRequest(form: FormState): DoctorRequest {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    specialization: form.specialization.trim(),
    licenseNumber: form.licenseNumber.trim(),
    phone: form.phone.trim(),
    email: nullableTrim(form.email),
    department: nullableTrim(form.department)
  };
}

function validateForm(form: FormState) {
  const fields: FieldErrors = {};
  const firstNameError = personNameError(form.firstName, "First name");
  const lastNameError = personNameError(form.lastName, "Last name");
  const specializationError = textError(form.specialization, "Specialization", true);
  const licenseNumber = form.licenseNumber.trim();
  const licenseError = licenseNumber
    ? (/^[A-Za-z0-9][A-Za-z0-9 ./_-]{2,99}$/.test(licenseNumber) ? null : "License number must be valid")
    : "License number is required";
  const phoneMessage = phoneError(form.phone);
  const emailError = optionalEmailError(form.email);
  const departmentError = textError(form.department, "Department");
  if (firstNameError) fields.firstName = firstNameError;
  if (lastNameError) fields.lastName = lastNameError;
  if (specializationError) fields.specialization = specializationError;
  if (licenseError) fields.licenseNumber = licenseError;
  if (phoneMessage) fields.phone = phoneMessage;
  if (emailError) fields.email = emailError;
  if (departmentError) fields.department = departmentError;
  return validationResult(fields);
}

function nullableTrim(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load doctors";
}
