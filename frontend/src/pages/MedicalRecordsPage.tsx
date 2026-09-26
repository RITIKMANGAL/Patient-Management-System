import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/apiClient";
import * as doctorApi from "../api/doctorApi";
import * as medicalRecordApi from "../api/medicalRecordApi";
import * as patientApi from "../api/patientApi";
import { useAuth } from "../auth/AuthContext";
import type { FormEvent } from "react";
import type { PageResponse } from "../types/api";
import type { Doctor } from "../types/doctor";
import type { MedicalRecord, MedicalRecordRequest } from "../types/medicalRecord";
import type { Patient } from "../types/patient";
import { displayText, formatDate } from "../utils/formatters";
import { textError, type FieldErrors, validationResult } from "../utils/validation";

type RecordsState =
  | { status: "idle"; page: null; error: null }
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<MedicalRecord>; error: null }
  | { status: "error"; page: null; error: string };

type OptionsState =
  | { status: "loading"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "loaded"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "error"; patients: Patient[]; doctors: Doctor[]; error: string };

interface FormState {
  patientId: string;
  doctorId: string;
  diagnosis: string;
  symptoms: string;
  notes: string;
  recordDate: string;
}

const emptyForm: FormState = {
  patientId: "",
  doctorId: "",
  diagnosis: "",
  symptoms: "",
  notes: "",
  recordDate: todayDateInputValue()
};

export function MedicalRecordsPage() {
  const auth = useAuth();
  const canCreate = auth.hasAnyRole(["ADMIN", "DOCTOR"]);
  const [optionsState, setOptionsState] = useState<OptionsState>({
    status: "loading",
    patients: [],
    doctors: [],
    error: null
  });
  const [selectedPatientId, setSelectedPatientId] = useState("");
  const [recordsState, setRecordsState] = useState<RecordsState>({ status: "idle", page: null, error: null });
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formSuccess, setFormSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [detailRecord, setDetailRecord] = useState<MedicalRecord | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [loadingDetailId, setLoadingDetailId] = useState<string | null>(null);

  useEffect(() => {
    let isCurrent = true;

    setOptionsState({ status: "loading", patients: [], doctors: [], error: null });
    Promise.all([patientApi.listPatients(), doctorApi.listDoctors()])
      .then(([patientsPage, doctorsPage]) => {
        if (!isCurrent) {
          return;
        }

        setOptionsState({
          status: "loaded",
          patients: patientsPage.content,
          doctors: doctorsPage.content,
          error: null
        });
        setSelectedPatientId((current) => {
          if (current && patientsPage.content.some((patient) => patient.id === current)) {
            return current;
          }
          return patientsPage.content[0]?.id ?? "";
        });
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setOptionsState({ status: "error", patients: [], doctors: [], error: toMessage(error) });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  const loadRecords = useCallback((patientId: string) => {
    setRecordsState({ status: "loading", page: null, error: null });
    medicalRecordApi.listPatientMedicalRecords(patientId)
      .then((page) => setRecordsState({ status: "loaded", page, error: null }))
      .catch((error: unknown) => setRecordsState({ status: "error", page: null, error: toMessage(error) }));
  }, []);

  useEffect(() => {
    if (selectedPatientId) {
      loadRecords(selectedPatientId);
    } else if (optionsState.status === "loaded") {
      setRecordsState({ status: "idle", page: null, error: null });
    }
  }, [loadRecords, optionsState.status, selectedPatientId]);

  const records = recordsState.status === "loaded" ? recordsState.page.content : [];

  function startCreate() {
    setIsFormOpen(true);
    setForm({
      ...emptyForm,
      patientId: selectedPatientId,
      doctorId: optionsState.status === "loaded" ? optionsState.doctors[0]?.id ?? "" : ""
    });
    setFormError(null);
    setFieldErrors({});
    setFormSuccess(null);
  }

  function closeForm(clearMessage = true) {
    setIsFormOpen(false);
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

    if (optionsState.status !== "loaded") {
      setFormError(optionsState.status === "error" ? optionsState.error : "Patient and doctor options are still loading");
      return;
    }

    const validation = validateForm(form);
    if (validation) {
      setFormError(validation.message);
      setFieldErrors(validation.fields);
      return;
    }
    setFieldErrors({});

    setIsSaving(true);
    try {
      const savedRecord = await medicalRecordApi.createMedicalRecord(toMedicalRecordRequest(form));
      setSelectedPatientId(savedRecord.patientId);
      setRecordsState((current) => {
        if (current.status !== "loaded" || current.page.content.some((record) => record.id === savedRecord.id)) {
          return current;
        }

        return {
          status: "loaded",
          page: {
            ...current.page,
            content: [savedRecord, ...current.page.content],
            totalElements: current.page.totalElements + 1
          },
          error: null
        };
      });
      setDetailRecord(savedRecord);
      setFormSuccess("Medical record created");
      closeForm(false);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function viewRecord(record: MedicalRecord) {
    setDetailError(null);
    setLoadingDetailId(record.id);
    try {
      setDetailRecord(await medicalRecordApi.getMedicalRecord(record.id));
    } catch (error: unknown) {
      setDetailError(toMessage(error));
    } finally {
      setLoadingDetailId(null);
    }
  }

  const canOpenForm = canCreate
    && optionsState.status === "loaded"
    && optionsState.patients.length > 0
    && optionsState.doctors.length > 0;

  return (
    <section className="resource-page" aria-labelledby="medical-records-heading">
      <div className="page-heading page-heading-row">
        <div>
          <p className="eyebrow">Clinical documentation</p>
          <h1 id="medical-records-heading">Medical Records</h1>
          <p className="page-description">Create clinical notes and review records by patient.</p>
        </div>
        {canCreate && (
          <button type="button" className="primary-button" disabled={!canOpenForm} onClick={startCreate}>
            New medical record
          </button>
        )}
      </div>

      {formSuccess && (
        <p className="form-success" role="status">
          {formSuccess}
        </p>
      )}

      {optionsState.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading patients and doctors...
        </div>
      )}

      {optionsState.status === "error" && (
        <p className="form-error" role="alert">
          {optionsState.error}
        </p>
      )}

      {optionsState.status === "loaded" && (optionsState.patients.length === 0 || optionsState.doctors.length === 0) && (
        <div className="resource-state empty-state">
          <strong>Patient and doctor records are required.</strong>
          <span>Create at least one patient and one doctor before adding medical records.</span>
        </div>
      )}

      {optionsState.status === "loaded" && optionsState.patients.length > 0 && optionsState.doctors.length > 0 && (
        <>
          <section className="form-panel" aria-labelledby="medical-record-filter-heading">
            <div className="section-heading">
              <h2 id="medical-record-filter-heading">Patient records</h2>
            </div>
            <div className="field-group">
              <label htmlFor="medical-record-patient-filter">Patient</label>
              <select
                id="medical-record-patient-filter"
                value={selectedPatientId}
                onChange={(event) => {
                  setSelectedPatientId(event.target.value);
                  setDetailRecord(null);
                  setDetailError(null);
                }}
              >
                {optionsState.patients.map((patient) => (
                  <option key={patient.id} value={patient.id}>
                    {patient.firstName} {patient.lastName}
                  </option>
                ))}
              </select>
            </div>
          </section>

          {isFormOpen && (
            <section className="form-panel" aria-labelledby="medical-record-form-heading">
              <div className="section-heading section-heading-row">
                <h2 id="medical-record-form-heading">New medical record</h2>
                <button type="button" className="secondary-button" onClick={() => closeForm()}>
                  Cancel
                </button>
              </div>

              {formError && (
                <p className="form-error" role="alert">
                  {formError}
                </p>
              )}

              <MedicalRecordForm
                doctors={optionsState.doctors}
                form={form}
                errors={fieldErrors}
                isSaving={isSaving}
                patients={optionsState.patients}
                onChange={setForm}
                onSubmit={(event) => void submitForm(event)}
              />
            </section>
          )}

          {!isFormOpen && formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}
        </>
      )}

      {recordsState.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading...
        </div>
      )}

      {recordsState.status === "error" && (
        <p className="form-error" role="alert">
          {recordsState.error}
        </p>
      )}

      {recordsState.status === "loaded" && records.length === 0 && (
        <div className="resource-state empty-state">
          <strong>No medical records found.</strong>
          <span>Clinical records for the selected patient will appear here once they are created.</span>
        </div>
      )}

      {recordsState.status === "loaded" && records.length > 0 && (
        <div className="table-panel">
          <table className="resource-table">
            <thead>
              <tr>
                <th scope="col">Record date</th>
                <th scope="col">Patient</th>
                <th scope="col">Doctor</th>
                <th scope="col">Diagnosis</th>
                <th scope="col">Symptoms</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td><span className="table-primary">{formatDate(record.recordDate)}</span></td>
                  <td>{record.patientName}</td>
                  <td>{record.doctorName}</td>
                  <td>{record.diagnosis}</td>
                  <td>{record.symptoms ?? "-"}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        type="button"
                        className="secondary-button compact-button"
                        disabled={loadingDetailId === record.id}
                        onClick={() => void viewRecord(record)}
                      >
                        {loadingDetailId === record.id ? "Loading..." : "View"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {detailError && (
        <p className="form-error" role="alert">
          {detailError}
        </p>
      )}

      {detailRecord && (
        <section className="form-panel" aria-labelledby="medical-record-detail-heading">
          <div className="section-heading section-heading-row">
            <h2 id="medical-record-detail-heading">Record details</h2>
            <button type="button" className="secondary-button" onClick={() => setDetailRecord(null)}>
              Close
            </button>
          </div>
          <dl className="detail-grid">
            <div>
              <dt>Patient</dt>
              <dd>{detailRecord.patientName}</dd>
            </div>
            <div>
              <dt>Doctor</dt>
              <dd>{detailRecord.doctorName}</dd>
            </div>
            <div>
              <dt>Record date</dt>
              <dd>{formatDate(detailRecord.recordDate)}</dd>
            </div>
            <div>
              <dt>Diagnosis</dt>
              <dd>{detailRecord.diagnosis}</dd>
            </div>
            <div>
              <dt>Symptoms</dt>
              <dd>{detailRecord.symptoms ?? "-"}</dd>
            </div>
            <div>
              <dt>Notes</dt>
              <dd>{displayText(detailRecord.notes)}</dd>
            </div>
          </dl>
        </section>
      )}
    </section>
  );
}

interface MedicalRecordFormProps {
  form: FormState;
  errors: FieldErrors;
  isSaving: boolean;
  patients: Patient[];
  doctors: Doctor[];
  onChange: (form: FormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

function MedicalRecordForm({
  form,
  errors,
  isSaving,
  patients,
  doctors,
  onChange,
  onSubmit
}: MedicalRecordFormProps) {
  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    onChange({ ...form, [field]: value });
  }

  return (
    <form noValidate onSubmit={onSubmit}>
      <div className="field-grid">
        <div className="field-group">
          <label htmlFor="medical-record-patient">Patient</label>
          <select
            id="medical-record-patient"
            value={form.patientId}
            onChange={(event) => updateField("patientId", event.target.value)}
          >
            <option value="">Select patient</option>
            {patients.map((patient) => (
              <option key={patient.id} value={patient.id}>
                {patient.firstName} {patient.lastName}
              </option>
            ))}
          </select>
          {errors.patientId && <p className="field-error" role="status">{errors.patientId}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="medical-record-doctor">Doctor</label>
          <select
            id="medical-record-doctor"
            value={form.doctorId}
            onChange={(event) => updateField("doctorId", event.target.value)}
          >
            <option value="">Select doctor</option>
            {doctors.map((doctor) => (
              <option key={doctor.id} value={doctor.id}>
                {doctor.firstName} {doctor.lastName}
              </option>
            ))}
          </select>
          {errors.doctorId && <p className="field-error" role="status">{errors.doctorId}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="medical-record-date">Record date</label>
          <input
            id="medical-record-date"
            type="date"
            value={form.recordDate}
            max={todayDateInputValue()}
            onChange={(event) => updateField("recordDate", event.target.value)}
          />
          {errors.recordDate && <p className="field-error" role="status">{errors.recordDate}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="medical-record-diagnosis">Diagnosis</label>
          <input
            id="medical-record-diagnosis"
            value={form.diagnosis}
            maxLength={500}
            onChange={(event) => updateField("diagnosis", event.target.value)}
          />
          {errors.diagnosis && <p className="field-error" role="status">{errors.diagnosis}</p>}
        </div>
      </div>

      <div className="field-group">
        <label htmlFor="medical-record-symptoms">Symptoms</label>
        <textarea
          id="medical-record-symptoms"
          value={form.symptoms}
          maxLength={1000}
          rows={3}
          onChange={(event) => updateField("symptoms", event.target.value)}
        />
      </div>

      <div className="field-group">
        <label htmlFor="medical-record-notes">Notes</label>
        <textarea
          id="medical-record-notes"
          value={form.notes}
          maxLength={5000}
          rows={4}
          onChange={(event) => updateField("notes", event.target.value)}
        />
      </div>

      <div className="form-actions">
        <button type="submit" className="primary-button" disabled={isSaving}>
          {isSaving ? "Saving..." : "Create medical record"}
        </button>
      </div>
    </form>
  );
}

function toMedicalRecordRequest(form: FormState): MedicalRecordRequest {
  return {
    patientId: form.patientId,
    doctorId: form.doctorId,
    diagnosis: form.diagnosis.trim(),
    symptoms: nullableTrim(form.symptoms),
    notes: nullableTrim(form.notes),
    recordDate: form.recordDate
  };
}

function validateForm(form: FormState) {
  const fields: FieldErrors = {};
  if (!form.patientId) fields.patientId = "Patient is required";
  if (!form.doctorId) fields.doctorId = "Doctor is required";
  if (!form.recordDate) fields.recordDate = "Record date is required";
  else if (form.recordDate > todayDateInputValue()) fields.recordDate = "Record date must not be in the future";
  const diagnosisError = textError(form.diagnosis, "Diagnosis", true);
  if (diagnosisError) fields.diagnosis = diagnosisError;
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
  return "Unable to load medical records";
}
