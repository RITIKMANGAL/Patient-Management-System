import { CalendarDays, ChevronLeft, ClipboardList, FileText, Sparkles } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiError } from "../api/apiClient";
import * as aiApi from "../api/aiApi";
import * as appointmentApi from "../api/appointmentApi";
import * as consultationApi from "../api/consultationApi";
import * as medicalRecordApi from "../api/medicalRecordApi";
import * as patientApi from "../api/patientApi";
import * as prescriptionApi from "../api/prescriptionApi";
import { useAuth } from "../auth/AuthContext";
import type { AiPatientHistorySummaryResponse } from "../types/ai";
import type { Appointment } from "../types/appointment";
import type { Consultation } from "../types/consultation";
import type { MedicalRecord } from "../types/medicalRecord";
import type { Patient } from "../types/patient";
import type { Prescription } from "../types/prescription";
import { formatDate, formatDateTime, formatEnum } from "../utils/formatters";

interface PatientDetailData {
  patient: Patient;
  appointments: Appointment[];
  consultations: Consultation[];
  records: MedicalRecord[];
  prescriptions: Prescription[];
}

type DetailState =
  | { status: "loading"; data: null; error: null }
  | { status: "loaded"; data: PatientDetailData; error: null }
  | { status: "error"; data: null; error: string };

type SummaryState =
  | { status: "idle"; response: null; error: null }
  | { status: "loading"; response: null; error: null }
  | { status: "loaded"; response: AiPatientHistorySummaryResponse; error: null }
  | { status: "error"; response: null; error: string };

interface TimelineItem {
  id: string;
  date: string;
  label: string;
  detail: string;
  type: "appointment" | "consultation" | "record" | "prescription";
}

export function PatientDetailPage() {
  const { patientId } = useParams();
  const auth = useAuth();
  const canViewClinicalData = auth.hasAnyRole(["ADMIN", "DOCTOR"]);
  const [state, setState] = useState<DetailState>({ status: "loading", data: null, error: null });
  const [summaryState, setSummaryState] = useState<SummaryState>({ status: "idle", response: null, error: null });

  const loadPatientDetail = useCallback(() => {
    if (!patientId) {
      setState({ status: "error", data: null, error: "Patient profile is unavailable" });
      return;
    }

    let isCurrent = true;
    setState({ status: "loading", data: null, error: null });
    setSummaryState({ status: "idle", response: null, error: null });

    const clinicalRequests = canViewClinicalData
      ? Promise.all([
        consultationApi.listPatientConsultations(patientId),
        medicalRecordApi.listPatientMedicalRecords(patientId),
        prescriptionApi.listPatientPrescriptions(patientId)
      ])
      : Promise.resolve([{ content: [] }, { content: [] }, { content: [] }]);

    void Promise.all([
      patientApi.getPatient(patientId),
      appointmentApi.listAppointments({ patientId, size: 20, sort: "appointmentDateTime,desc" }),
      clinicalRequests
    ]).then(([patient, appointments, [consultations, records, prescriptions]]) => {
      if (!isCurrent) return;
      setState({
        status: "loaded",
        data: {
          patient,
          appointments: appointments.content,
          consultations: consultations.content,
          records: records.content,
          prescriptions: prescriptions.content
        },
        error: null
      });
    }).catch((error: unknown) => {
      if (isCurrent) setState({ status: "error", data: null, error: toMessage(error) });
    });

    return () => {
      isCurrent = false;
    };
  }, [canViewClinicalData, patientId]);

  useEffect(() => loadPatientDetail(), [loadPatientDetail]);

  const timeline = useMemo(() => state.status === "loaded" ? buildTimeline(state.data) : [], [state]);
  const nextAppointment = state.status === "loaded" ? upcomingAppointment(state.data.appointments) : null;

  async function generateSummary() {
    if (!patientId || !canViewClinicalData) return;
    setSummaryState({ status: "loading", response: null, error: null });
    try {
      const response = await aiApi.generatePatientHistorySummary(patientId);
      setSummaryState({ status: "loaded", response, error: null });
    } catch (error: unknown) {
      setSummaryState({ status: "error", response: null, error: toMessage(error) });
    }
  }

  if (state.status === "loading") {
    return <PageState loadingMessage="Loading patient profile..." />;
  }

  if (state.status === "error") {
    return <PageState error={state.error} onRetry={loadPatientDetail} />;
  }

  const { patient, appointments, consultations, records, prescriptions } = state.data;
  return (
    <section className="patient-detail-page" aria-labelledby="patient-detail-heading">
      <Link className="back-link" to="/patients"><ChevronLeft aria-hidden="true" /> Back to patients</Link>

      <header className="patient-profile-header">
        <div>
          <p className="eyebrow">Patient profile</p>
          <h1 id="patient-detail-heading">{patient.firstName} {patient.lastName}</h1>
          <p className="page-description">A concise view of this patient&apos;s appointments and documented clinical history.</p>
        </div>
        <div className="patient-header-badges" aria-label="Patient identity details">
          <span className="status-badge neutral">{formatEnum(patient.gender)}</span>
          {patient.bloodGroup && <span className="status-badge">{formatEnum(patient.bloodGroup)}</span>}
        </div>
      </header>

      <section className="patient-context-grid" aria-label="Patient contact and appointment context">
        <article className="detail-card">
          <h2>Patient details</h2>
          <dl className="detail-grid">
            <div><dt>Date of birth</dt><dd>{formatDate(patient.dateOfBirth)}</dd></div>
            <div><dt>Phone</dt><dd>{patient.phone}</dd></div>
            <div><dt>Email</dt><dd>{patient.email ?? "Not recorded"}</dd></div>
            <div><dt>Emergency contact</dt><dd>{patient.emergencyContactName ?? "Not recorded"}</dd></div>
          </dl>
        </article>
        <article className="detail-card upcoming-appointment-card">
          <div className="card-title-row"><CalendarDays aria-hidden="true" /><h2>Upcoming appointment</h2></div>
          {nextAppointment ? (
            <>
              <strong>{formatDateTime(nextAppointment.appointmentDateTime)}</strong>
              <span>{nextAppointment.doctorName}</span>
              <small>{nextAppointment.reason}</small>
            </>
          ) : <p className="summary-note">No upcoming appointment is recorded.</p>}
        </article>
      </section>

      <section className="timeline-section" aria-labelledby="patient-timeline-heading">
        <div className="section-heading section-heading-row">
          <div>
            <p className="eyebrow">Patient journey</p>
            <h2 id="patient-timeline-heading">Clinical timeline</h2>
          </div>
          <span className="summary-note">{timeline.length} documented event{timeline.length === 1 ? "" : "s"}</span>
        </div>
        {timeline.length === 0 ? (
          <div className="resource-state empty-state">
            <strong>No patient activity yet.</strong>
            <span>Appointments and clinical records will appear here as they are documented.</span>
          </div>
        ) : (
          <ol className="clinical-timeline">
            {timeline.map((item) => {
              const Icon = timelineIcon(item.type);
              return (
                <li key={item.id} className={`timeline-item ${item.type}`}>
                  <span className="timeline-icon"><Icon aria-hidden="true" /></span>
                  <div>
                    <time>{formatDateTime(item.date)}</time>
                    <strong>{item.label}</strong>
                    <p>{item.detail}</p>
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </section>

      {canViewClinicalData && (
        <section className="patient-history-grid" aria-label="Clinical history overview">
          <HistoryCard icon={ClipboardList} title="Medical records" count={records.length} emptyCopy="No medical records available yet." />
          <HistoryCard icon={FileText} title="Prescriptions" count={prescriptions.length} emptyCopy="No prescriptions available yet." />
          <HistoryCard icon={CalendarDays} title="Consultations" count={consultations.length} emptyCopy="No consultations available yet." />
        </section>
      )}

      {canViewClinicalData && (
        <section className="ai-summary-workspace" aria-labelledby="patient-ai-summary-heading">
          <div className="section-heading section-heading-row">
            <div>
              <p className="eyebrow">Clinora AI</p>
              <h2 id="patient-ai-summary-heading">Patient History Summary</h2>
              <p className="page-description">Review before clinical use. AI output is never saved automatically.</p>
            </div>
            <button type="button" className="secondary-button" disabled={summaryState.status === "loading"} onClick={() => void generateSummary()}>
              <Sparkles aria-hidden="true" /> {summaryState.status === "loading" ? "Generating..." : "Generate summary"}
            </button>
          </div>
          {summaryState.status === "error" && <p className="form-error" role="alert">{summaryState.error}</p>}
          {summaryState.status === "loaded" && (
            <div className="ai-output-panel">
              <p className="summary-note">{summaryState.response.notice}</p>
              <p>{summaryState.response.summary}</p>
              <SummaryList title="Recent Clinical Activity" items={summaryState.response.recentClinicalActivity} />
              <SummaryList title="Medical History" items={summaryState.response.documentedHistory} />
              <SummaryList title="Recent Prescriptions" items={summaryState.response.recentPrescriptions} />
              <SummaryList title="Follow-up" items={summaryState.response.followUp} />
            </div>
          )}
        </section>
      )}

      <section className="patient-related-actions" aria-label="Related patient workspaces">
        <Link className="secondary-button" to="/appointments">View appointments</Link>
        {canViewClinicalData && <Link className="secondary-button" to="/medical-records">View medical records</Link>}
        {canViewClinicalData && <Link className="secondary-button" to="/prescriptions">View prescriptions</Link>}
      </section>
    </section>
  );
}

function PageState({ loadingMessage, error, onRetry }: { loadingMessage?: string; error?: string; onRetry?: () => void }) {
  return (
    <section className="patient-detail-page">
      <Link className="back-link" to="/patients"><ChevronLeft aria-hidden="true" /> Back to patients</Link>
      <div className={`resource-state ${loadingMessage ? "loading-state" : "empty-state"}`} aria-live="polite">
        {loadingMessage && <span className="loading-dot" aria-hidden="true" />}
        <strong>{error ?? loadingMessage}</strong>
        {error && <button type="button" className="secondary-button compact-button" onClick={onRetry}>Try again</button>}
      </div>
    </section>
  );
}

function HistoryCard({ icon: Icon, title, count, emptyCopy }: { icon: typeof ClipboardList; title: string; count: number; emptyCopy: string }) {
  return (
    <article className="history-card">
      <Icon aria-hidden="true" />
      <span className="summary-label">{title}</span>
      <strong className="metric-value">{count}</strong>
      <p>{count === 0 ? emptyCopy : `${count} documented item${count === 1 ? "" : "s"}.`}</p>
    </article>
  );
}

function SummaryList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return <section className="ai-summary-section"><h3>{title}</h3><ul>{items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

function buildTimeline(data: PatientDetailData): TimelineItem[] {
  return [
    ...data.appointments.map((item) => ({ id: `appointment-${item.id}`, date: item.appointmentDateTime, label: `Appointment: ${formatEnum(item.status)}`, detail: `${item.reason} with ${item.doctorName}`, type: "appointment" as const })),
    ...data.consultations.map((item) => ({ id: `consultation-${item.id}`, date: item.completedAt ?? item.startedAt, label: `Consultation: ${formatEnum(item.status)}`, detail: item.assessment ?? item.chiefComplaint ?? `Consultation with ${item.doctorName}`, type: "consultation" as const })),
    ...data.records.map((item) => ({ id: `record-${item.id}`, date: item.recordDate, label: "Medical record", detail: item.diagnosis, type: "record" as const })),
    ...data.prescriptions.map((item) => ({ id: `prescription-${item.id}`, date: item.prescriptionDate, label: "Prescription", detail: item.items.length ? item.items.map((medicine) => medicine.medicineName).join(", ") : `Prescription from ${item.doctorName}`, type: "prescription" as const }))
  ].sort((left, right) => new Date(right.date).getTime() - new Date(left.date).getTime());
}

function upcomingAppointment(appointments: Appointment[]): Appointment | undefined {
  const now = new Date();
  return appointments.filter((appointment) => new Date(appointment.appointmentDateTime) >= now && ["SCHEDULED", "CONFIRMED"].includes(appointment.status))
    .sort((left, right) => new Date(left.appointmentDateTime).getTime() - new Date(right.appointmentDateTime).getTime())[0];
}

function timelineIcon(type: TimelineItem["type"]) {
  if (type === "appointment") return CalendarDays;
  if (type === "consultation") return ClipboardList;
  if (type === "record") return ClipboardList;
  return FileText;
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) return "Patient profile was not found.";
  return error instanceof ApiError ? error.message : "Unable to load this patient profile.";
}
