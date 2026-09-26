import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import * as aiApi from "../api/aiApi";
import * as appointmentApi from "../api/appointmentApi";
import * as consultationApi from "../api/consultationApi";
import * as doctorApi from "../api/doctorApi";
import * as feedbackApi from "../api/feedbackApi";
import * as patientApi from "../api/patientApi";
import { useAuth } from "../auth/AuthContext";
import { ConfirmDialog } from "../components/ConfirmDialog";
import type { FormEvent } from "react";
import type { AiConsultationDraftResponse } from "../types/ai";
import type { PageResponse } from "../types/api";
import type { Appointment, AppointmentRequest, AppointmentStatus } from "../types/appointment";
import type { Consultation, ConsultationUpdateRequest } from "../types/consultation";
import type { Doctor } from "../types/doctor";
import type { FeedbackAccessTokenResponse } from "../types/feedback";
import type { Patient } from "../types/patient";
import { formatDateTime, formatEnum, maskSecureUrl, splitInternalMarker } from "../utils/formatters";
import { textError, type FieldErrors, validationResult } from "../utils/validation";

type LoadState =
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<Appointment>; error: null }
  | { status: "error"; page: null; error: string };

type OptionsState =
  | { status: "idle"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "loading"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "loaded"; patients: Patient[]; doctors: Doctor[]; error: null }
  | { status: "error"; patients: Patient[]; doctors: Doctor[]; error: string };

type FormMode = "create" | "edit";

type ConsultationState =
  | { status: "idle"; appointment: null; consultation: null; error: null }
  | { status: "loading"; appointment: Appointment; consultation: null; error: null }
  | { status: "loaded"; appointment: Appointment; consultation: Consultation; error: null }
  | { status: "error"; appointment: Appointment; consultation: null; error: string };

type AiDraftState =
  | { status: "idle"; response: null; error: null }
  | { status: "loading"; response: null; error: null }
  | { status: "loaded"; response: AiConsultationDraftResponse; error: null }
  | { status: "error"; response: null; error: string };

type FeedbackAccessState =
  | { status: "idle"; response: null; error: null; copyMessage: null }
  | { status: "loading"; response: null; error: null; copyMessage: null }
  | { status: "loaded"; response: FeedbackAccessTokenResponse; error: null; copyMessage: string | null }
  | { status: "error"; response: null; error: string; copyMessage: null };

interface FormState {
  patientId: string;
  doctorId: string;
  appointmentDateTime: string;
  reason: string;
  status: AppointmentStatus | "";
  notes: string;
  notesMarker: string;
}

interface ConsultationFormState {
  chiefComplaint: string;
  symptoms: string;
  examination: string;
  assessment: string;
  treatment: string;
  followUpInstructions: string;
}

const emptyForm: FormState = {
  patientId: "",
  doctorId: "",
  appointmentDateTime: "",
  reason: "",
  status: "SCHEDULED",
  notes: "",
  notesMarker: ""
};

const emptyConsultationForm: ConsultationFormState = {
  chiefComplaint: "",
  symptoms: "",
  examination: "",
  assessment: "",
  treatment: "",
  followUpInstructions: ""
};

const createStatusOptions: AppointmentStatus[] = ["SCHEDULED", "CONFIRMED"];
const consultationEligibleStatuses: AppointmentStatus[] = ["SCHEDULED", "CONFIRMED", "COMPLETED"];
const mutableAppointmentStatuses: AppointmentStatus[] = ["SCHEDULED", "CONFIRMED"];
const emptyFeedbackAccessState: FeedbackAccessState = {
  status: "idle",
  response: null,
  error: null,
  copyMessage: null
};

export function AppointmentsPage() {
  const auth = useAuth();
  const canCreate = auth.hasAnyRole(["ADMIN", "RECEPTIONIST"]);
  const canUpdateOrCancel = auth.hasAnyRole(["ADMIN", "DOCTOR", "RECEPTIONIST"]);
  const canManageConsultations = auth.hasAnyRole(["ADMIN", "DOCTOR"]);
  const [state, setState] = useState<LoadState>({ status: "loading", page: null, error: null });
  const [optionsState, setOptionsState] = useState<OptionsState>({
    status: "idle",
    patients: [],
    doctors: [],
    error: null
  });
  const [formMode, setFormMode] = useState<FormMode | null>(null);
  const [editingAppointment, setEditingAppointment] = useState<Appointment | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formSuccess, setFormSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [pendingCancel, setPendingCancel] = useState<Appointment | null>(null);
  const [consultationState, setConsultationState] = useState<ConsultationState>({
    status: "idle",
    appointment: null,
    consultation: null,
    error: null
  });
  const [consultationForm, setConsultationForm] = useState<ConsultationFormState>(emptyConsultationForm);
  const [loadedConsultationForm, setLoadedConsultationForm] = useState<ConsultationFormState>(emptyConsultationForm);
  const [consultationMessage, setConsultationMessage] = useState<string | null>(null);
  const [isConsultationSaving, setIsConsultationSaving] = useState(false);
  const [roughClinicalNotes, setRoughClinicalNotes] = useState("");
  const [aiDraftState, setAiDraftState] = useState<AiDraftState>({ status: "idle", response: null, error: null });
  const [feedbackAccessState, setFeedbackAccessState] = useState<FeedbackAccessState>(emptyFeedbackAccessState);

  const loadAppointments = useCallback(() => {
    setState({ status: "loading", page: null, error: null });
    appointmentApi.listAppointments()
      .then((page) => setState({ status: "loaded", page, error: null }))
      .catch((error: unknown) => setState({ status: "error", page: null, error: toMessage(error) }));
  }, []);

  const loadOptions = useCallback(() => {
    if (optionsState.status === "loaded" || optionsState.status === "loading") {
      return;
    }

    setOptionsState((current) => ({ status: "loading", patients: current.patients, doctors: current.doctors, error: null }));

    Promise.all([patientApi.listPatients(), doctorApi.listDoctors()])
      .then(([patientsPage, doctorsPage]) => {
        setOptionsState({
          status: "loaded",
          patients: patientsPage.content,
          doctors: doctorsPage.content,
          error: null
        });
      })
      .catch((error: unknown) => {
        setOptionsState((current) => ({
          status: "error",
          patients: current.patients,
          doctors: current.doctors,
          error: toMessage(error)
        }));
      });
  }, [optionsState.status]);

  useEffect(() => {
    loadAppointments();
  }, [loadAppointments]);

  const appointments = state.status === "loaded" ? state.page.content : [];
  const hasActions = canUpdateOrCancel || canManageConsultations;
  const statusOptions = formMode === "create" ? createStatusOptions : updateStatusOptions(form.status);
  const headingActions = useMemo(() => {
    if (!canCreate) {
      return null;
    }
    return (
      <button type="button" className="primary-button" onClick={startCreate}>
        New appointment
      </button>
    );
  }, [canCreate]);

  function startCreate() {
    setFormMode("create");
    setEditingAppointment(null);
    setForm(emptyForm);
    setFormError(null);
    setFormSuccess(null);
    loadOptions();
  }

  function startEdit(appointment: Appointment) {
    setFormMode("edit");
    setEditingAppointment(appointment);
    setForm(formFromAppointment(appointment));
    setFormError(null);
    setFormSuccess(null);
    loadOptions();
  }

  function closeForm(clearMessage = true) {
    setFormMode(null);
    setEditingAppointment(null);
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

    if (optionsState.status === "loading") {
      setFormError("Patient and doctor options are still loading");
      return;
    }

    if (optionsState.status === "error") {
      setFormError(optionsState.error);
      return;
    }

    const validation = validateForm(form);
    if (validation) {
      setFormError(validation.message);
      setFieldErrors(validation.fields);
      return;
    }
    setFieldErrors({});

    const request = toAppointmentRequest(form);
    setIsSaving(true);
    try {
      const savedAppointment = formMode === "edit" && editingAppointment
        ? await appointmentApi.updateAppointment(editingAppointment.id, request)
        : await appointmentApi.createAppointment(request);

      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }

        const content = formMode === "edit"
          ? current.page.content.map((appointment) => appointment.id === savedAppointment.id ? savedAppointment : appointment)
          : [savedAppointment, ...current.page.content];
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
      setFormSuccess(formMode === "edit" ? "Appointment updated" : "Appointment created");
      closeForm(false);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function requestCancel(appointment: Appointment) {
    setFormError(null);
    setFormSuccess(null);
    setPendingCancel(appointment);
  }

  async function handleCancel() {
    if (!pendingCancel) {
      return;
    }

    setCancellingId(pendingCancel.id);
    setFormError(null);
    setFormSuccess(null);
    try {
      await appointmentApi.cancelAppointment(pendingCancel.id);
      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }
        return {
          status: "loaded",
          page: {
            ...current.page,
            content: current.page.content.map((appointment) =>
              appointment.id === pendingCancel.id
                ? { ...appointment, status: "CANCELLED" }
                : appointment
            )
          },
          error: null
        };
      });
      setFormSuccess("Appointment cancelled");
      setPendingCancel(null);
    } catch (error: unknown) {
      setFormError(toMessage(error));
    } finally {
      setCancellingId(null);
    }
  }

  async function openConsultation(appointment: Appointment) {
    setConsultationState({ status: "loading", appointment, consultation: null, error: null });
    setConsultationForm(emptyConsultationForm);
    setLoadedConsultationForm(emptyConsultationForm);
    setConsultationMessage(null);
    setRoughClinicalNotes("");
    setAiDraftState({ status: "idle", response: null, error: null });
    setFeedbackAccessState(emptyFeedbackAccessState);
    try {
      const consultation = appointment.status === "COMPLETED"
        ? await consultationApi.getAppointmentConsultation(appointment.id)
        : await consultationApi.startAppointmentConsultation(appointment.id);
      setConsultationState({ status: "loaded", appointment, consultation, error: null });
      const loadedForm = consultationFormFromResponse(consultation);
      setConsultationForm(loadedForm);
      setLoadedConsultationForm(loadedForm);
      setConsultationMessage(appointment.status === "COMPLETED" ? null : "Consultation started");
    } catch (error: unknown) {
      if (error instanceof ApiError && error.status === 409) {
        try {
          const consultation = await consultationApi.getAppointmentConsultation(appointment.id);
          setConsultationState({ status: "loaded", appointment, consultation, error: null });
          const loadedForm = consultationFormFromResponse(consultation);
          setConsultationForm(loadedForm);
          setLoadedConsultationForm(loadedForm);
          return;
        } catch (loadError: unknown) {
          setConsultationState({ status: "error", appointment, consultation: null, error: consultationMessageFromError(loadError) });
          return;
        }
      }
      setConsultationState({ status: "error", appointment, consultation: null, error: consultationMessageFromError(error) });
    }
  }

  function closeConsultation() {
    setConsultationState({ status: "idle", appointment: null, consultation: null, error: null });
    setConsultationForm(emptyConsultationForm);
    setLoadedConsultationForm(emptyConsultationForm);
    setConsultationMessage(null);
    setIsConsultationSaving(false);
    setRoughClinicalNotes("");
    setAiDraftState({ status: "idle", response: null, error: null });
    setFeedbackAccessState(emptyFeedbackAccessState);
  }

  async function generateAiDraft() {
    if (consultationState.status !== "loaded" || consultationState.consultation.status !== "IN_PROGRESS") {
      return;
    }

    if (!roughClinicalNotes.trim()) {
      setAiDraftState({ status: "error", response: null, error: "Rough clinical notes are required" });
      return;
    }

    setAiDraftState({ status: "loading", response: null, error: null });
    try {
      const draft = await aiApi.generateConsultationDraft(consultationState.consultation.id, {
        roughNotes: roughClinicalNotes.trim(),
        ...toEditedConsultationDraftContext(consultationForm, loadedConsultationForm)
      });
      setAiDraftState({ status: "loaded", response: draft, error: null });
    } catch (error: unknown) {
      setAiDraftState({ status: "error", response: null, error: aiMessageFromError(error) });
    }
  }

  function applyAiDraft() {
    if (aiDraftState.status !== "loaded") {
      return;
    }

    const draft = aiDraftState.response.draft;
    setConsultationForm({
      chiefComplaint: providedDraftValue(draft.chiefComplaint, consultationForm.chiefComplaint),
      symptoms: providedDraftValue(draft.symptoms, consultationForm.symptoms),
      examination: providedDraftValue(draft.examination, consultationForm.examination),
      assessment: providedDraftValue(draft.assessment, consultationForm.assessment),
      treatment: providedDraftValue(draft.treatmentAdvice, consultationForm.treatment),
      followUpInstructions: providedDraftValue(draft.followUpInstructions, consultationForm.followUpInstructions)
    });
    setConsultationMessage("AI draft applied. Review before saving.");
  }

  async function saveConsultation() {
    if (consultationState.status !== "loaded" || consultationState.consultation.status !== "IN_PROGRESS") {
      return;
    }

    setIsConsultationSaving(true);
    setConsultationMessage(null);
    try {
      const consultation = await consultationApi.updateConsultation(
        consultationState.consultation.id,
        toConsultationUpdateRequest(consultationForm)
      );
      setConsultationState({
        status: "loaded",
        appointment: consultationState.appointment,
        consultation,
        error: null
      });
      const loadedForm = consultationFormFromResponse(consultation);
      setConsultationForm(loadedForm);
      setLoadedConsultationForm(loadedForm);
      setConsultationMessage("Consultation saved");
    } catch (error: unknown) {
      setConsultationState({
        status: "loaded",
        appointment: consultationState.appointment,
        consultation: consultationState.consultation,
        error: null
      });
      setConsultationMessage(consultationMessageFromError(error));
    } finally {
      setIsConsultationSaving(false);
    }
  }

  async function completeConsultation() {
    if (consultationState.status !== "loaded" || consultationState.consultation.status !== "IN_PROGRESS") {
      return;
    }

    const validationError = validateConsultationCompletion(consultationForm);
    if (validationError) {
      setConsultationMessage(validationError);
      return;
    }

    setIsConsultationSaving(true);
    setConsultationMessage(null);
    try {
      const savedConsultation = await consultationApi.updateConsultation(
        consultationState.consultation.id,
        toConsultationUpdateRequest(consultationForm)
      );
      const completedConsultation = await consultationApi.completeConsultation(savedConsultation.id);
      setConsultationState({
        status: "loaded",
        appointment: { ...consultationState.appointment, status: completedConsultation.appointmentStatus },
        consultation: completedConsultation,
        error: null
      });
      const loadedForm = consultationFormFromResponse(completedConsultation);
      setConsultationForm(loadedForm);
      setLoadedConsultationForm(loadedForm);
      setState((current) => {
        if (current.status !== "loaded") {
          return current;
        }
        return {
          status: "loaded",
          page: {
            ...current.page,
            content: current.page.content.map((appointment) =>
              appointment.id === completedConsultation.appointmentId
                ? { ...appointment, status: completedConsultation.appointmentStatus }
                : appointment
            )
          },
          error: null
        };
      });
      setConsultationMessage("Consultation completed");
      setFeedbackAccessState(emptyFeedbackAccessState);
    } catch (error: unknown) {
      setConsultationMessage(consultationMessageFromError(error));
    } finally {
      setIsConsultationSaving(false);
    }
  }

  async function createFeedbackLink() {
    if (consultationState.status !== "loaded" || consultationState.consultation.status !== "COMPLETED") {
      return;
    }

    setFeedbackAccessState({ status: "loading", response: null, error: null, copyMessage: null });
    try {
      const response = await feedbackApi.createFeedbackAccess(consultationState.consultation.id);
      setFeedbackAccessState({ status: "loaded", response, error: null, copyMessage: null });
    } catch (error: unknown) {
      setFeedbackAccessState({ status: "error", response: null, error: toMessage(error), copyMessage: null });
    }
  }

  async function copyFeedbackLink(feedbackUrl: string) {
    if (!navigator.clipboard) {
      setFeedbackAccessState((current) => current.status === "loaded"
        ? { ...current, copyMessage: "Copy is unavailable in this browser" }
        : current);
      return;
    }

    try {
      await navigator.clipboard.writeText(feedbackUrl);
      setFeedbackAccessState((current) => current.status === "loaded"
        ? { ...current, copyMessage: "Feedback link copied" }
        : current);
    } catch {
      setFeedbackAccessState((current) => current.status === "loaded"
        ? { ...current, copyMessage: "Copy is unavailable in this browser" }
        : current);
    }
  }

  return (
    <section className="resource-page" aria-labelledby="appointments-heading">
      <div className="page-heading page-heading-row">
        <div>
          <p className="eyebrow">Scheduling</p>
          <h1 id="appointments-heading">Appointments</h1>
          <p className="page-description">Schedule, update, and cancel patient appointments.</p>
        </div>
        {headingActions}
      </div>

      {formSuccess && (
        <p className="form-success" role="status">
          {formSuccess}
        </p>
      )}

      {formMode && (canCreate || canUpdateOrCancel) && (
        <section className="form-panel" aria-labelledby="appointment-form-heading">
          <div className="section-heading section-heading-row">
            <h2 id="appointment-form-heading">{formMode === "edit" ? "Edit appointment" : "New appointment"}</h2>
            <button type="button" className="secondary-button" onClick={() => closeForm()}>
              Cancel
            </button>
          </div>

          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}

          {optionsState.status === "loading" && (
            <div className="resource-state loading-state" aria-live="polite">
              <span className="loading-dot" aria-hidden="true" />
              Loading patient and doctor options...
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
              <span>Create at least one patient and one doctor before scheduling an appointment.</span>
            </div>
          )}

          <AppointmentForm
            errors={fieldErrors}
            form={form}
            isDisabled={optionsState.status !== "loaded" || optionsState.patients.length === 0 || optionsState.doctors.length === 0}
            isSaving={isSaving}
            doctors={withSelectedDoctor(optionsState.doctors, editingAppointment)}
            patients={withSelectedPatient(optionsState.patients, editingAppointment)}
            statusOptions={statusOptions}
            submitLabel={formMode === "edit" ? "Save changes" : "Create appointment"}
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

      {state.status === "loaded" && appointments.length === 0 && (
        <div className="resource-state empty-state">
          <strong>No appointments found.</strong>
          <span>Appointments will appear here once they are scheduled.</span>
        </div>
      )}

      {state.status === "loaded" && appointments.length > 0 && (
        <div className="table-panel">
          <table className="resource-table">
            <thead>
              <tr>
                <th scope="col">Date and time</th>
                <th scope="col">Patient</th>
                <th scope="col">Doctor</th>
                <th scope="col">Reason</th>
                <th scope="col">Status</th>
                {hasActions && <th scope="col">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {appointments.map((appointment) => (
                <tr key={appointment.id}>
                  <td>
                    <span className="table-primary">{formatDateTime(appointment.appointmentDateTime)}</span>
                  </td>
                  <td><Link className="entity-link" to={`/patients/${appointment.patientId}`}>{appointment.patientName}</Link></td>
                  <td>{appointment.doctorName}</td>
                  <td>{appointment.reason}</td>
                  <td><span className="status-badge accent">{formatEnum(appointment.status)}</span></td>
                  {hasActions && (
                    <td>
                      <div className="row-actions">
                        {canManageConsultations && consultationEligibleStatuses.includes(appointment.status) && (
                          <button
                            type="button"
                            className="primary-button compact-button"
                            onClick={() => void openConsultation(appointment)}
                          >
                            {appointment.status === "COMPLETED" ? "View consultation" : "Consultation"}
                          </button>
                        )}
                        {canUpdateOrCancel && mutableAppointmentStatuses.includes(appointment.status) && (
                          <>
                            <button type="button" className="secondary-button compact-button" onClick={() => startEdit(appointment)}>
                              Edit
                            </button>
                            <button
                              type="button"
                              className="danger-button compact-button"
                              disabled={cancellingId === appointment.id}
                              onClick={() => requestCancel(appointment)}
                            >
                              {cancellingId === appointment.id ? "Cancelling..." : "Cancel"}
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pendingCancel && (
        <ConfirmDialog
          title="Cancel appointment"
          message={`Cancel the appointment for ${pendingCancel.patientName} with ${pendingCancel.doctorName} on ${formatDateTime(pendingCancel.appointmentDateTime)}?`}
          confirmLabel="Cancel appointment"
          confirmingLabel="Cancelling..."
          isConfirming={cancellingId === pendingCancel.id}
          onCancel={() => setPendingCancel(null)}
          onConfirm={() => void handleCancel()}
        />
      )}

      {consultationState.status !== "idle" && (
        <ConsultationPanel
          state={consultationState}
          form={consultationForm}
          message={consultationMessage}
          isSaving={isConsultationSaving}
          roughClinicalNotes={roughClinicalNotes}
          aiDraftState={aiDraftState}
          feedbackAccessState={feedbackAccessState}
          onChange={setConsultationForm}
          onRoughNotesChange={setRoughClinicalNotes}
          onGenerateAiDraft={() => void generateAiDraft()}
          onApplyAiDraft={applyAiDraft}
          onCreateFeedbackLink={() => void createFeedbackLink()}
          onCopyFeedbackLink={(feedbackUrl) => void copyFeedbackLink(feedbackUrl)}
          onClose={closeConsultation}
          onSave={() => void saveConsultation()}
          onComplete={() => void completeConsultation()}
        />
      )}
    </section>
  );
}

interface AppointmentFormProps {
  form: FormState;
  errors: FieldErrors;
  isDisabled: boolean;
  isSaving: boolean;
  patients: Patient[];
  doctors: Doctor[];
  statusOptions: AppointmentStatus[];
  submitLabel: string;
  onChange: (form: FormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

function AppointmentForm({
  form,
  errors,
  isDisabled,
  isSaving,
  patients,
  doctors,
  statusOptions,
  submitLabel,
  onChange,
  onSubmit
}: AppointmentFormProps) {
  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    onChange({ ...form, [field]: value });
  }

  return (
    <form onSubmit={onSubmit}>
      <div className="field-grid">
        <div className="field-group">
          <label htmlFor="appointment-patient">Patient</label>
          <select
            id="appointment-patient"
            disabled={isDisabled}
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
          <label htmlFor="appointment-doctor">Doctor</label>
          <select
            id="appointment-doctor"
            disabled={isDisabled}
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
          <label htmlFor="appointment-date-time">Date and time</label>
          <input
            id="appointment-date-time"
            type="datetime-local"
            disabled={isDisabled}
            value={form.appointmentDateTime}
            onChange={(event) => updateField("appointmentDateTime", event.target.value)}
          />
          {errors.appointmentDateTime && <p className="field-error" role="status">{errors.appointmentDateTime}</p>}
        </div>
        <div className="field-group">
          <label htmlFor="appointment-status">Status</label>
          <select
            id="appointment-status"
            disabled={isDisabled}
            value={form.status}
            onChange={(event) => updateField("status", event.target.value as AppointmentStatus | "")}
          >
            {statusOptions.map((status) => (
              <option key={status} value={status}>
                {formatEnum(status)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="field-group">
        <label htmlFor="appointment-reason">Reason</label>
        <input
          id="appointment-reason"
          disabled={isDisabled}
          value={form.reason}
          maxLength={500}
          onChange={(event) => updateField("reason", event.target.value)}
        />
        {errors.reason && <p className="field-error" role="status">{errors.reason}</p>}
      </div>

      <div className="field-group">
        <label htmlFor="appointment-notes">Notes</label>
        <textarea
          id="appointment-notes"
          disabled={isDisabled}
          value={form.notes}
          maxLength={1000}
          rows={3}
          onChange={(event) => updateField("notes", event.target.value)}
        />
      </div>

      <div className="form-actions">
        <button type="submit" className="primary-button" disabled={isDisabled || isSaving}>
          {isSaving ? "Saving..." : submitLabel}
        </button>
      </div>
    </form>
  );
}

interface ConsultationPanelProps {
  state: ConsultationState;
  form: ConsultationFormState;
  message: string | null;
  isSaving: boolean;
  roughClinicalNotes: string;
  aiDraftState: AiDraftState;
  feedbackAccessState: FeedbackAccessState;
  onChange: (form: ConsultationFormState) => void;
  onRoughNotesChange: (value: string) => void;
  onGenerateAiDraft: () => void;
  onApplyAiDraft: () => void;
  onCreateFeedbackLink: () => void;
  onCopyFeedbackLink: (feedbackUrl: string) => void;
  onClose: () => void;
  onSave: () => void;
  onComplete: () => void;
}

function ConsultationPanel({
  state,
  form,
  message,
  isSaving,
  roughClinicalNotes,
  aiDraftState,
  feedbackAccessState,
  onChange,
  onRoughNotesChange,
  onGenerateAiDraft,
  onApplyAiDraft,
  onCreateFeedbackLink,
  onCopyFeedbackLink,
  onClose,
  onSave,
  onComplete
}: ConsultationPanelProps) {
  const consultation = state.status === "loaded" ? state.consultation : null;
  const isReadOnly = consultation?.status !== "IN_PROGRESS";
  const isGeneratingDraft = aiDraftState.status === "loading";

  function updateField<K extends keyof ConsultationFormState>(field: K, value: ConsultationFormState[K]) {
    onChange({ ...form, [field]: value });
  }

  return (
    <section className="form-panel consultation-workspace" aria-labelledby="consultation-heading">
      <div className="section-heading section-heading-row">
        <div>
          <p className="eyebrow">Clinical workspace</p>
          <h2 id="consultation-heading">Consultation</h2>
          {state.appointment && (
            <div className="consultation-context">
              <Link className="entity-link" to={`/patients/${state.appointment.patientId}`}>{state.appointment.patientName}</Link>
              <span>{state.appointment.doctorName} · {formatDateTime(state.appointment.appointmentDateTime)}</span>
              <small>{state.appointment.reason}</small>
            </div>
          )}
        </div>
        <button type="button" className="secondary-button" onClick={onClose}>
          Close
        </button>
      </div>

      {state.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading consultation...
        </div>
      )}

      {state.status === "error" && (
        <p className="form-error" role="alert">
          {state.error}
        </p>
      )}

      {message && (
        <p className={message.includes("required") || message.includes("denied") ? "form-error" : "form-success"} role="status">
          {message}
        </p>
      )}

      {consultation && (
        <>
          <p className="status-line">
            <span className="status-badge accent">{formatEnum(consultation.status)}</span>
            {consultation.completedAt ? <span>Completed at {formatDateTime(consultation.completedAt)}</span> : null}
          </p>

          {!isReadOnly && (
            <section className="ai-assist-section" aria-labelledby="ai-draft-heading">
              <div className="section-heading section-heading-row">
                <div>
                  <p className="eyebrow">Clinora AI</p>
                  <h3 id="ai-draft-heading">Consultation Note Assistant</h3>
                  <p className="page-description">AI-generated draft - review before saving.</p>
                </div>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={isGeneratingDraft}
                  onClick={onGenerateAiDraft}
                >
                  {isGeneratingDraft ? "Generating..." : "Generate AI draft"}
                </button>
              </div>

              <div className="field-group">
                <label htmlFor="consultation-rough-notes">Rough clinical notes</label>
                <textarea
                  id="consultation-rough-notes"
                  value={roughClinicalNotes}
                  maxLength={4000}
                  rows={4}
                  onChange={(event) => onRoughNotesChange(event.target.value)}
                />
              </div>

              {aiDraftState.status === "loading" && (
                <div className="resource-state loading-state" aria-live="polite">
                  <span className="loading-dot" aria-hidden="true" />
                  Generating AI draft...
                </div>
              )}

              {aiDraftState.status === "error" && (
                <p className="form-error" role="alert">
                  {aiDraftState.error}
                </p>
              )}

              {aiDraftState.status === "loaded" && (
                <div className="ai-output-panel">
                  <p className="status-line">
                    <span className="status-badge neutral">Draft</span>
                    <span>{aiDraftState.response.notice}</span>
                  </p>
                  <dl className="ai-output-list">
                    <div>
                      <dt>Chief complaint</dt>
                      <dd>{aiDraftState.response.draft.chiefComplaint}</dd>
                    </div>
                    <div>
                      <dt>Symptoms</dt>
                      <dd>{aiDraftState.response.draft.symptoms}</dd>
                    </div>
                    <div>
                      <dt>Examination / observations</dt>
                      <dd>{aiDraftState.response.draft.examination}</dd>
                    </div>
                    <div>
                      <dt>Assessment</dt>
                      <dd>{aiDraftState.response.draft.assessment}</dd>
                    </div>
                    <div>
                      <dt>Treatment / advice</dt>
                      <dd>{aiDraftState.response.draft.treatmentAdvice}</dd>
                    </div>
                    <div>
                      <dt>Follow-up</dt>
                      <dd>{aiDraftState.response.draft.followUpInstructions}</dd>
                    </div>
                  </dl>
                  <div className="form-actions">
                    <button type="button" className="secondary-button" onClick={onApplyAiDraft}>
                      Apply draft to form
                    </button>
                  </div>
                </div>
              )}
            </section>
          )}

          <div className="field-group">
            <label htmlFor="consultation-chief-complaint">Chief complaint</label>
            <textarea
              id="consultation-chief-complaint"
              value={form.chiefComplaint}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("chiefComplaint", event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="consultation-symptoms">Symptoms</label>
            <textarea
              id="consultation-symptoms"
              value={form.symptoms}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("symptoms", event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="consultation-examination">Examination / observations</label>
            <textarea
              id="consultation-examination"
              value={form.examination}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("examination", event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="consultation-assessment">Assessment / diagnosis</label>
            <textarea
              id="consultation-assessment"
              value={form.assessment}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("assessment", event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="consultation-treatment">Treatment / advice</label>
            <textarea
              id="consultation-treatment"
              value={form.treatment}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("treatment", event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="consultation-follow-up">Follow-up instructions</label>
            <textarea
              id="consultation-follow-up"
              value={form.followUpInstructions}
              disabled={isReadOnly}
              maxLength={4000}
              rows={3}
              onChange={(event) => updateField("followUpInstructions", event.target.value)}
            />
          </div>

          {isReadOnly ? (
            <>
              <div className="resource-state empty-state">
                <strong>Consultation completed.</strong>
                <span>Completed consultations are read-only.</span>
              </div>
              {consultation.status === "COMPLETED" && (
                <section className="ai-assist-section" aria-labelledby="feedback-link-heading">
                  <div className="section-heading section-heading-row">
                    <div>
                      <h3 id="feedback-link-heading">Patient feedback</h3>
                      <p className="page-description">Send a secure visit-rating link for this completed consultation.</p>
                    </div>
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={feedbackAccessState.status === "loading"}
                      onClick={onCreateFeedbackLink}
                    >
                      {feedbackAccessState.status === "loading" ? "Creating..." : "Create Feedback Link"}
                    </button>
                  </div>

                  {feedbackAccessState.status === "error" && (
                    <p className="form-error" role="alert">
                      {feedbackAccessState.error}
                    </p>
                  )}

                  {feedbackAccessState.status === "loaded" && (
                    <div className="feedback-link-box">
                      <div>
                        <span className="summary-label">Secure feedback link</span>
                        <code className="inline-code">{maskSecureUrl(feedbackAccessState.response.feedbackUrl)}</code>
                        <p className="summary-note">Expires {formatDateTime(feedbackAccessState.response.expiresAt)}</p>
                      </div>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => onCopyFeedbackLink(feedbackAccessState.response.feedbackUrl)}
                      >
                        Copy link
                      </button>
                      {feedbackAccessState.copyMessage && (
                        <p className="form-success" role="status">
                          {feedbackAccessState.copyMessage}
                        </p>
                      )}
                    </div>
                  )}
                </section>
              )}
            </>
          ) : (
            <div className="form-actions">
              <button type="button" className="secondary-button" disabled={isSaving} onClick={onSave}>
                {isSaving ? "Saving..." : "Save progress"}
              </button>
              <button type="button" className="primary-button" disabled={isSaving} onClick={onComplete}>
                {isSaving ? "Completing..." : "Complete consultation"}
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function formFromAppointment(appointment: Appointment): FormState {
  const notes = splitInternalMarker(appointment.notes);
  return {
    patientId: appointment.patientId,
    doctorId: appointment.doctorId,
    appointmentDateTime: toDateTimeInputValue(appointment.appointmentDateTime),
    reason: appointment.reason,
    status: appointment.status,
    notes: notes.text,
    notesMarker: notes.marker
  };
}

function toAppointmentRequest(form: FormState): AppointmentRequest {
  return {
    patientId: form.patientId,
    doctorId: form.doctorId,
    appointmentDateTime: toLocalDateTimeRequest(form.appointmentDateTime),
    reason: form.reason.trim(),
    status: form.status === "" ? null : form.status,
    notes: appointmentNotes(form)
  };
}

function validateForm(form: FormState) {
  const fields: FieldErrors = {};
  if (!form.patientId) fields.patientId = "Patient is required";
  if (!form.doctorId) fields.doctorId = "Doctor is required";
  if (!form.appointmentDateTime) {
    fields.appointmentDateTime = "Date and time are required";
  } else if (Number.isNaN(new Date(form.appointmentDateTime).getTime())) {
    fields.appointmentDateTime = "Date and time must be valid";
  } else if (new Date(form.appointmentDateTime) < new Date()) {
    fields.appointmentDateTime = "Appointment date and time must not be in the past";
  }
  const reasonError = textError(form.reason, "Reason", true);
  if (reasonError) fields.reason = reasonError;
  return validationResult(fields);
}

function withSelectedPatient(patients: Patient[], appointment: Appointment | null): Patient[] {
  if (!appointment || patients.some((patient) => patient.id === appointment.patientId)) {
    return patients;
  }
  return [
    {
      id: appointment.patientId,
      firstName: appointment.patientName,
      lastName: "",
      dateOfBirth: "",
      gender: "UNKNOWN",
      bloodGroup: null,
      phone: "",
      email: null,
      address: null,
      emergencyContactName: null,
      emergencyContactPhone: null,
      createdAt: "",
      updatedAt: ""
    },
    ...patients
  ];
}

function withSelectedDoctor(doctors: Doctor[], appointment: Appointment | null): Doctor[] {
  if (!appointment || doctors.some((doctor) => doctor.id === appointment.doctorId)) {
    return doctors;
  }
  return [
    {
      id: appointment.doctorId,
      firstName: appointment.doctorName,
      lastName: "",
      specialization: "",
      licenseNumber: "",
      phone: "",
      email: null,
      department: null,
      createdAt: "",
      updatedAt: ""
    },
    ...doctors
  ];
}

function toDateTimeInputValue(value: string): string {
  return value.length >= 16 ? value.slice(0, 16) : value;
}

function toLocalDateTimeRequest(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

function nullableTrim(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function consultationFormFromResponse(consultation: Consultation): ConsultationFormState {
  return {
    chiefComplaint: consultation.chiefComplaint ?? "",
    symptoms: consultation.symptoms ?? "",
    examination: consultation.examination ?? "",
    assessment: consultation.assessment ?? "",
    treatment: consultation.treatment ?? "",
    followUpInstructions: consultation.followUpInstructions ?? ""
  };
}

function toConsultationUpdateRequest(form: ConsultationFormState): ConsultationUpdateRequest {
  return {
    chiefComplaint: nullableTrim(form.chiefComplaint),
    symptoms: nullableTrim(form.symptoms),
    examination: nullableTrim(form.examination),
    assessment: nullableTrim(form.assessment),
    treatment: nullableTrim(form.treatment),
    followUpInstructions: nullableTrim(form.followUpInstructions)
  };
}

function toEditedConsultationDraftContext(
  form: ConsultationFormState,
  loadedForm: ConsultationFormState
): ConsultationUpdateRequest {
  return {
    chiefComplaint: editedValue(form.chiefComplaint, loadedForm.chiefComplaint),
    symptoms: editedValue(form.symptoms, loadedForm.symptoms),
    examination: editedValue(form.examination, loadedForm.examination),
    assessment: editedValue(form.assessment, loadedForm.assessment),
    treatment: editedValue(form.treatment, loadedForm.treatment),
    followUpInstructions: editedValue(form.followUpInstructions, loadedForm.followUpInstructions)
  };
}

function editedValue(value: string, loadedValue: string): string | null {
  return value === loadedValue ? null : nullableTrim(value);
}

function validateConsultationCompletion(form: ConsultationFormState): string | null {
  if (!form.chiefComplaint.trim()) {
    return "Chief complaint is required before completion";
  }
  if (!form.assessment.trim()) {
    return "Assessment is required before completion";
  }
  return null;
}

function appointmentNotes(form: FormState): string | null {
  const visibleNotes = nullableTrim(form.notes);
  if (!form.notesMarker) {
    return visibleNotes;
  }
  return visibleNotes ? `${form.notesMarker} ${visibleNotes}` : form.notesMarker;
}

function updateStatusOptions(currentStatus: AppointmentStatus | ""): AppointmentStatus[] {
  if (currentStatus === "SCHEDULED") {
    return ["SCHEDULED", "CONFIRMED", "CANCELLED", "NO_SHOW"];
  }
  if (currentStatus === "CONFIRMED") {
    return ["CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW"];
  }
  return currentStatus ? [currentStatus] : [];
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load appointments";
}

function consultationMessageFromError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load consultation";
}

function aiMessageFromError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "AI assistance is temporarily unavailable. You can continue entering the consultation manually.";
}

function providedDraftValue(value: string, fallback: string): string {
  const trimmed = value.trim();
  if (!trimmed || trimmed.toLowerCase() === "not provided") {
    return fallback;
  }
  return trimmed;
}
