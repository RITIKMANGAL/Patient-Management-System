import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/apiClient";
import * as doctorApi from "../api/doctorApi";
import * as patientApi from "../api/patientApi";
import * as prescriptionApi from "../api/prescriptionApi";
import { useAuth } from "../auth/AuthContext";
import type { FormEvent } from "react";
import type { PageResponse } from "../types/api";
import type { Doctor } from "../types/doctor";
import type { Patient } from "../types/patient";
import type {
  Prescription,
  PrescriptionAccessStatusResponse,
  PrescriptionAccessTokenResponse,
  PrescriptionRequest
} from "../types/prescription";
import { displayText, formatDate, formatDateTime, maskSecureUrl } from "../utils/formatters";
import { textError, type FieldErrors, validationResult } from "../utils/validation";

type PrescriptionsState =
  | { status: "idle"; page: null; error: null }
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<Prescription>; error: null }
  | { status: "error"; page: null; error: string };

type OptionsState =
  | { status: "loading"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "loaded"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "error"; patients: Patient[]; doctors: Doctor[]; error: string };

interface ItemFormState {
  medicineName: string;
  dosage: string;
  frequency: string;
  duration: string;
  instructions: string;
}

interface FormState {
  patientId: string;
  doctorId: string;
  prescriptionDate: string;
  notes: string;
  items: ItemFormState[];
}

interface PatientAccessDisplay extends PrescriptionAccessTokenResponse {
  prescriptionId: string;
  url: string;
}

interface PatientAccessStatusDisplay extends PrescriptionAccessStatusResponse {
  prescriptionId: string;
}

const emptyItem: ItemFormState = {
  medicineName: "",
  dosage: "",
  frequency: "",
  duration: "",
  instructions: ""
};

const emptyForm: FormState = {
  patientId: "",
  doctorId: "",
  prescriptionDate: todayDateInputValue(),
  notes: "",
  items: [{ ...emptyItem }]
};

export function PrescriptionsPage() {
  const auth = useAuth();
  const canCreate = auth.hasAnyRole(["ADMIN", "DOCTOR"]);
  const [optionsState, setOptionsState] = useState<OptionsState>({
    status: "loading",
    patients: [],
    doctors: [],
    error: null
  });
  const [selectedPatientId, setSelectedPatientId] = useState("");
  const [prescriptionsState, setPrescriptionsState] = useState<PrescriptionsState>({
    status: "idle",
    page: null,
    error: null
  });
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formSuccess, setFormSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [detailPrescription, setDetailPrescription] = useState<Prescription | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [loadingDetailId, setLoadingDetailId] = useState<string | null>(null);
  const [loadingPdfId, setLoadingPdfId] = useState<string | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);
  const [loadingAccessId, setLoadingAccessId] = useState<string | null>(null);
  const [accessError, setAccessError] = useState<string | null>(null);
  const [patientAccess, setPatientAccess] = useState<PatientAccessDisplay | null>(null);
  const [patientAccessStatus, setPatientAccessStatus] = useState<PatientAccessStatusDisplay | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);

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

  const loadPrescriptions = useCallback((patientId: string) => {
    setPrescriptionsState({ status: "loading", page: null, error: null });
    prescriptionApi.listPatientPrescriptions(patientId)
      .then((page) => setPrescriptionsState({ status: "loaded", page, error: null }))
      .catch((error: unknown) => setPrescriptionsState({ status: "error", page: null, error: toMessage(error) }));
  }, []);

  useEffect(() => {
    if (selectedPatientId) {
      loadPrescriptions(selectedPatientId);
    } else if (optionsState.status === "loaded") {
      setPrescriptionsState({ status: "idle", page: null, error: null });
    }
  }, [loadPrescriptions, optionsState.status, selectedPatientId]);

  const prescriptions = prescriptionsState.status === "loaded" ? prescriptionsState.page.content : [];
  const canOpenForm = canCreate
    && optionsState.status === "loaded"
    && optionsState.patients.length > 0
    && optionsState.doctors.length > 0;

  function startCreate() {
    setIsFormOpen(true);
    setForm({
      ...emptyForm,
      patientId: selectedPatientId,
      doctorId: optionsState.status === "loaded" ? optionsState.doctors[0]?.id ?? "" : "",
      items: [{ ...emptyItem }]
    });
    setFormError(null);
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
      const savedPrescription = await prescriptionApi.createPrescription(toPrescriptionRequest(form));
      setSelectedPatientId(savedPrescription.patientId);
      setPrescriptionsState((current) => {
        if (
          current.status !== "loaded"
          || selectedPatientId !== savedPrescription.patientId
          || current.page.content.some((prescription) => prescription.id === savedPrescription.id)
        ) {
          return current;
        }

        return {
          status: "loaded",
          page: {
            ...current.page,
            content: [savedPrescription, ...current.page.content],
            totalElements: current.page.totalElements + 1
          },
          error: null
        };
      });
      setDetailPrescription(savedPrescription);
      setFormSuccess("Prescription created");
      closeForm(false);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function viewPrescription(prescription: Prescription) {
    setDetailError(null);
    setLoadingDetailId(prescription.id);
    try {
      setDetailPrescription(await prescriptionApi.getPrescription(prescription.id));
    } catch (error: unknown) {
      setDetailError(toMessage(error));
    } finally {
      setLoadingDetailId(null);
    }
  }

  async function openPrescriptionPdf(prescription: Prescription) {
    setPdfError(null);
    setLoadingPdfId(prescription.id);
    try {
      const pdf = await prescriptionApi.downloadPrescriptionPdf(prescription.id);
      openOrDownloadPdf(pdf, `prescription-${prescription.id}.pdf`);
    } catch (error: unknown) {
      setPdfError(toMessage(error));
    } finally {
      setLoadingPdfId(null);
    }
  }

  async function issuePatientAccess(prescription: Prescription) {
    setAccessError(null);
    setCopyStatus(null);
    setLoadingAccessId(prescription.id);
    try {
      const response = await prescriptionApi.createPrescriptionAccess(prescription.id);
      setPatientAccess({
        ...response,
        prescriptionId: prescription.id,
        url: `${window.location.origin}/prescription-access/${encodeURIComponent(response.token)}`
      });
      setPatientAccessStatus(null);
    } catch (error: unknown) {
      setAccessError(toMessage(error));
    } finally {
      setLoadingAccessId(null);
    }
  }

  async function inspectPatientAccess(prescription: Prescription) {
    setAccessError(null);
    setCopyStatus(null);
    setPatientAccess(null);
    setPatientAccessStatus(null);
    setLoadingAccessId(prescription.id);
    try {
      const response = await prescriptionApi.getPrescriptionAccessStatus(prescription.id);
      if (response.active) {
        setPatientAccessStatus({ ...response, prescriptionId: prescription.id });
      } else {
        await issuePatientAccess(prescription);
      }
    } catch (error: unknown) {
      setAccessError(toMessage(error));
    } finally {
      setLoadingAccessId(null);
    }
  }

  async function regeneratePatientAccess(prescriptionId: string) {
    if (!window.confirm("Regenerate this patient access link? The current link will stop working immediately.")) {
      return;
    }
    const prescription = prescriptions.find((item) => item.id === prescriptionId);
    if (prescription) {
      await issuePatientAccess(prescription);
    }
  }

  async function revokePatientAccess(prescriptionId: string) {
    setAccessError(null);
    setCopyStatus(null);
    setLoadingAccessId(prescriptionId);
    try {
      await prescriptionApi.revokePrescriptionAccess(prescriptionId);
      if (patientAccess?.prescriptionId === prescriptionId) {
        setPatientAccess(null);
      }
      if (patientAccessStatus?.prescriptionId === prescriptionId) {
        setPatientAccessStatus(null);
      }
      setCopyStatus("Patient access revoked");
    } catch (error: unknown) {
      setAccessError(toMessage(error));
    } finally {
      setLoadingAccessId(null);
    }
  }

  async function copyPatientAccessUrl(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopyStatus("Patient access link copied");
    } catch {
      setCopyStatus("Copy failed. Select and copy the link manually.");
    }
  }

  return (
    <section className="resource-page" aria-labelledby="prescriptions-heading">
      <div className="page-heading page-heading-row">
        <div>
          <p className="eyebrow">Medication orders</p>
          <h1 id="prescriptions-heading">Prescriptions</h1>
          <p className="page-description">Create prescriptions and review itemized medicines by patient.</p>
        </div>
        {canCreate && (
          <button type="button" className="primary-button" disabled={!canOpenForm} onClick={startCreate}>
            New prescription
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
          <span>Create at least one patient and one doctor before adding prescriptions.</span>
        </div>
      )}

      {optionsState.status === "loaded" && optionsState.patients.length > 0 && optionsState.doctors.length > 0 && (
        <>
          <section className="form-panel" aria-labelledby="prescription-filter-heading">
            <div className="section-heading">
              <h2 id="prescription-filter-heading">Patient prescriptions</h2>
            </div>
            <div className="field-group">
              <label htmlFor="prescription-patient-filter">Patient</label>
              <select
                id="prescription-patient-filter"
                value={selectedPatientId}
                onChange={(event) => {
                  setSelectedPatientId(event.target.value);
                  setDetailPrescription(null);
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
            <section className="form-panel" aria-labelledby="prescription-form-heading">
              <div className="section-heading section-heading-row">
                <h2 id="prescription-form-heading">New prescription</h2>
                <button type="button" className="secondary-button" onClick={() => closeForm()}>
                  Cancel
                </button>
              </div>

              {formError && (
                <p className="form-error" role="alert">
                  {formError}
                </p>
              )}

              <PrescriptionForm
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

      {prescriptionsState.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading...
        </div>
      )}

      {prescriptionsState.status === "error" && (
        <p className="form-error" role="alert">
          {prescriptionsState.error}
        </p>
      )}

      {prescriptionsState.status === "loaded" && prescriptions.length === 0 && (
        <div className="resource-state empty-state">
          <strong>No prescriptions found.</strong>
          <span>Prescriptions for the selected patient will appear here once they are created.</span>
        </div>
      )}

      {prescriptionsState.status === "loaded" && prescriptions.length > 0 && (
        <div className="table-panel">
          <table className="resource-table">
            <thead>
              <tr>
                <th scope="col">Prescription date</th>
                <th scope="col">Patient</th>
                <th scope="col">Doctor</th>
                <th scope="col">Medicines</th>
                <th scope="col">Notes</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {prescriptions.map((prescription) => (
                <tr key={prescription.id}>
                  <td><span className="table-primary">{formatDate(prescription.prescriptionDate)}</span></td>
                  <td>{prescription.patientName}</td>
                  <td>{prescription.doctorName}</td>
                  <td>{formatMedicineSummary(prescription)}</td>
                  <td>{displayPrescriptionNotes(prescription.notes)}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        type="button"
                        className="primary-button compact-button"
                        disabled={loadingDetailId === prescription.id}
                        onClick={() => void viewPrescription(prescription)}
                      >
                        {loadingDetailId === prescription.id ? "Loading..." : "View"}
                      </button>
                      {canCreate && (
                        <button
                          type="button"
                          className="secondary-button compact-button"
                          disabled={loadingPdfId === prescription.id}
                          onClick={() => void openPrescriptionPdf(prescription)}
                        >
                          {loadingPdfId === prescription.id ? "Preparing PDF..." : "PDF"}
                        </button>
                      )}
                      {canCreate && (
                        <button
                          type="button"
                          className="secondary-button compact-button"
                          disabled={loadingAccessId === prescription.id}
                        onClick={() => void inspectPatientAccess(prescription)}
                      >
                        {loadingAccessId === prescription.id ? "Checking..." : "Patient access"}
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

      {detailError && (
        <p className="form-error" role="alert">
          {detailError}
        </p>
      )}

      {pdfError && (
        <p className="form-error" role="alert">
          {pdfError}
        </p>
      )}

      {accessError && (
        <p className="form-error" role="alert">
          {accessError}
        </p>
      )}

      {copyStatus && (
        <p className="form-success" role="status">
          {copyStatus}
        </p>
      )}

      {patientAccess && (
        <section className="form-panel" aria-labelledby="patient-access-heading">
          <div className="section-heading section-heading-row">
            <div>
              <h2 id="patient-access-heading">Patient access link</h2>
            <p className="summary-note">This one-time link provides access to this prescription PDF until it expires. Keep it private.</p>
            </div>
            <button type="button" className="secondary-button" onClick={() => setPatientAccess(null)}>
              Dismiss
            </button>
          </div>
          <div className="field-group">
            <span className="field-label">Access URL</span>
            <code id="patient-access-url" className="masked-access-url">{maskSecureUrl(patientAccess.url)}</code>
          </div>
          <p className="summary-note">Expires {formatDateTime(patientAccess.expiresAt)}</p>
          <div className="form-actions">
            <button type="button" className="primary-button" onClick={() => void copyPatientAccessUrl(patientAccess.url)}>
              Copy link
            </button>
            <button
              type="button"
              className="danger-button"
              disabled={loadingAccessId === patientAccess.prescriptionId}
              onClick={() => void revokePatientAccess(patientAccess.prescriptionId)}
            >
              {loadingAccessId === patientAccess.prescriptionId ? "Revoking..." : "Revoke access"}
            </button>
          </div>
        </section>
      )}

      {patientAccessStatus?.active && (
        <section className="form-panel" aria-labelledby="active-patient-access-heading">
          <div className="section-heading section-heading-row">
            <div>
              <h2 id="active-patient-access-heading">Active patient access already exists</h2>
              <p className="summary-note">The existing link remains valid until it expires. Regenerating it revokes the current link first.</p>
            </div>
            <button type="button" className="secondary-button" onClick={() => setPatientAccessStatus(null)}>
              Dismiss
            </button>
          </div>
          <p className="summary-note">Expires {patientAccessStatus.expiresAt ? formatDateTime(patientAccessStatus.expiresAt) : "soon"}</p>
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={loadingAccessId === patientAccessStatus.prescriptionId}
              onClick={() => void regeneratePatientAccess(patientAccessStatus.prescriptionId)}
            >
              Regenerate access
            </button>
            <button
              type="button"
              className="danger-button"
              disabled={loadingAccessId === patientAccessStatus.prescriptionId}
              onClick={() => void revokePatientAccess(patientAccessStatus.prescriptionId)}
            >
              Revoke access
            </button>
          </div>
        </section>
      )}

      {detailPrescription && (
        <section className="form-panel" aria-labelledby="prescription-detail-heading">
          <div className="section-heading section-heading-row">
            <h2 id="prescription-detail-heading">Prescription details</h2>
            <button type="button" className="secondary-button" onClick={() => setDetailPrescription(null)}>
              Close
            </button>
          </div>
          <dl className="detail-grid">
            <div>
              <dt>Patient</dt>
              <dd>{detailPrescription.patientName}</dd>
            </div>
            <div>
              <dt>Doctor</dt>
              <dd>{detailPrescription.doctorName}</dd>
            </div>
            <div>
              <dt>Prescription date</dt>
              <dd>{formatDate(detailPrescription.prescriptionDate)}</dd>
            </div>
            <div>
              <dt>Notes</dt>
              <dd>{displayPrescriptionNotes(detailPrescription.notes)}</dd>
            </div>
          </dl>
          <div className="table-panel">
            <table className="resource-table">
              <thead>
                <tr>
                  <th scope="col">Medicine</th>
                  <th scope="col">Dosage</th>
                  <th scope="col">Frequency</th>
                  <th scope="col">Duration</th>
                  <th scope="col">Instructions</th>
                </tr>
              </thead>
              <tbody>
                {detailPrescription.items.map((item) => (
                  <tr key={item.id}>
                    <td><span className="table-primary">{item.medicineName}</span></td>
                    <td>{item.dosage}</td>
                    <td>{item.frequency}</td>
                    <td>{item.duration}</td>
                    <td>{item.instructions ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}

interface PrescriptionFormProps {
  form: FormState;
  errors: FieldErrors;
  isSaving: boolean;
  patients: Patient[];
  doctors: Doctor[];
  onChange: (form: FormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

function PrescriptionForm({
  form,
  errors,
  isSaving,
  patients,
  doctors,
  onChange,
  onSubmit
}: PrescriptionFormProps) {
  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    onChange({ ...form, [field]: value });
  }

  function updateItem(index: number, nextItem: ItemFormState) {
    onChange({
      ...form,
      items: form.items.map((item, itemIndex) => itemIndex === index ? nextItem : item)
    });
  }

  function addItem() {
    if (form.items.length >= 50) {
      return;
    }
    onChange({ ...form, items: [...form.items, { ...emptyItem }] });
  }

  function removeItem(index: number) {
    if (form.items.length <= 1) {
      return;
    }
    onChange({ ...form, items: form.items.filter((_, itemIndex) => itemIndex !== index) });
  }

  return (
    <form noValidate onSubmit={onSubmit}>
      <div className="field-grid">
        <div className="field-group">
          <label htmlFor="prescription-patient">Patient</label>
          <select
            id="prescription-patient"
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
          <label htmlFor="prescription-doctor">Doctor</label>
          <select
            id="prescription-doctor"
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
          <label htmlFor="prescription-date">Prescription date</label>
          <input
            id="prescription-date"
            type="date"
            value={form.prescriptionDate}
            max={todayDateInputValue()}
            onChange={(event) => updateField("prescriptionDate", event.target.value)}
          />
          {errors.prescriptionDate && <p className="field-error" role="status">{errors.prescriptionDate}</p>}
        </div>
      </div>

      <div className="field-group">
        <label htmlFor="prescription-notes">Notes</label>
        <textarea
          id="prescription-notes"
          value={form.notes}
          maxLength={5000}
          rows={3}
          onChange={(event) => updateField("notes", event.target.value)}
        />
      </div>

      <section className="repeatable-section" aria-labelledby="prescription-items-heading">
        <div className="section-heading section-heading-row">
          <h3 id="prescription-items-heading">Medicines</h3>
          <button type="button" className="secondary-button compact-button" disabled={form.items.length >= 50} onClick={addItem}>
            Add medicine
          </button>
        </div>
        <div className="repeatable-list">
          {form.items.map((item, index) => (
            <fieldset className="repeatable-item" key={index}>
              <legend>Medicine {index + 1}</legend>
              <div className="field-grid">
                <div className="field-group">
                  <label htmlFor={`prescription-medicine-${index}`}>Medicine name</label>
                  <input
                    id={`prescription-medicine-${index}`}
                    value={item.medicineName}
                    maxLength={200}
                    onChange={(event) => updateItem(index, { ...item, medicineName: event.target.value })}
                  />
                  {errors[`items.${index}.medicineName`] && <p className="field-error" role="status">{errors[`items.${index}.medicineName`]}</p>}
                </div>
                <div className="field-group">
                  <label htmlFor={`prescription-dosage-${index}`}>Dosage</label>
                  <input
                    id={`prescription-dosage-${index}`}
                    value={item.dosage}
                    maxLength={100}
                    onChange={(event) => updateItem(index, { ...item, dosage: event.target.value })}
                  />
                  {errors[`items.${index}.dosage`] && <p className="field-error" role="status">{errors[`items.${index}.dosage`]}</p>}
                </div>
                <div className="field-group">
                  <label htmlFor={`prescription-frequency-${index}`}>Frequency</label>
                  <input
                    id={`prescription-frequency-${index}`}
                    value={item.frequency}
                    maxLength={100}
                    onChange={(event) => updateItem(index, { ...item, frequency: event.target.value })}
                  />
                  {errors[`items.${index}.frequency`] && <p className="field-error" role="status">{errors[`items.${index}.frequency`]}</p>}
                </div>
                <div className="field-group">
                  <label htmlFor={`prescription-duration-${index}`}>Duration</label>
                  <input
                    id={`prescription-duration-${index}`}
                    value={item.duration}
                    maxLength={100}
                    onChange={(event) => updateItem(index, { ...item, duration: event.target.value })}
                  />
                  {errors[`items.${index}.duration`] && <p className="field-error" role="status">{errors[`items.${index}.duration`]}</p>}
                </div>
              </div>
              <div className="field-group">
                <label htmlFor={`prescription-instructions-${index}`}>Instructions</label>
                <textarea
                  id={`prescription-instructions-${index}`}
                  value={item.instructions}
                  maxLength={500}
                  rows={2}
                  onChange={(event) => updateItem(index, { ...item, instructions: event.target.value })}
                />
              </div>
              <div className="form-actions">
                <button
                  type="button"
                  className="secondary-button compact-button"
                  disabled={form.items.length <= 1}
                  onClick={() => removeItem(index)}
                >
                  Remove medicine
                </button>
              </div>
            </fieldset>
          ))}
        </div>
      </section>

      <div className="form-actions">
        <button type="submit" className="primary-button" disabled={isSaving}>
          {isSaving ? "Saving..." : "Create prescription"}
        </button>
      </div>
    </form>
  );
}

function toPrescriptionRequest(form: FormState): PrescriptionRequest {
  return {
    patientId: form.patientId,
    doctorId: form.doctorId,
    prescriptionDate: form.prescriptionDate,
    notes: nullableTrim(form.notes),
    items: form.items.map((item) => ({
      medicineName: item.medicineName.trim(),
      dosage: item.dosage.trim(),
      frequency: item.frequency.trim(),
      duration: item.duration.trim(),
      instructions: nullableTrim(item.instructions)
    }))
  };
}

function validateForm(form: FormState) {
  const fields: FieldErrors = {};
  if (!form.patientId) fields.patientId = "Patient is required";
  if (!form.doctorId) fields.doctorId = "Doctor is required";
  if (!form.prescriptionDate) fields.prescriptionDate = "Prescription date is required";
  else if (form.prescriptionDate > todayDateInputValue()) fields.prescriptionDate = "Prescription date must not be in the future";
  if (form.items.length === 0) fields.items = "At least one medicine is required";
  if (form.items.length > 50) fields.items = "No more than 50 medicines can be added";
  form.items.forEach((item, index) => {
    const prefix = `items.${index}`;
    const medicineError = textError(item.medicineName, `Medicine ${index + 1} name`, true);
    if (medicineError) fields[`${prefix}.medicineName`] = medicineError;
    if (!item.dosage.trim()) fields[`${prefix}.dosage`] = `Medicine ${index + 1} dosage is required`;
    if (!item.frequency.trim()) fields[`${prefix}.frequency`] = `Medicine ${index + 1} frequency is required`;
    if (!item.duration.trim()) fields[`${prefix}.duration`] = `Medicine ${index + 1} duration is required`;
  });
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

function formatMedicineSummary(prescription: Prescription): string {
  if (prescription.items.length === 0) {
    return "-";
  }
  if (prescription.items.length === 1) {
    return prescription.items[0].medicineName;
  }
  return `${prescription.items[0].medicineName} +${prescription.items.length - 1}`;
}

function displayPrescriptionNotes(notes: string | null | undefined): string {
  return displayText(notes);
}

function openOrDownloadPdf(pdf: Blob, filename: string): void {
  const url = URL.createObjectURL(pdf);
  const openedWindow = window.open(url, "_blank", "noopener,noreferrer");

  if (openedWindow) {
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return;
  }

  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load prescriptions";
}
