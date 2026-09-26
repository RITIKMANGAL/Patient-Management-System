import { CalendarDays, ClipboardPlus, FilePlus2, UserPlus, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { ApiError, apiRequest } from "../api/apiClient";
import * as appointmentApi from "../api/appointmentApi";
import { toPageResponse } from "../api/pageResponse";
import { useAuth } from "../auth/AuthContext";
import type { Appointment } from "../types/appointment";
import type { RoleName } from "../types/auth";
import { formatDate, formatEnum, formatTime } from "../utils/formatters";

interface OverviewMetric {
  id: string;
  label: string;
  count: number | null;
  status: "loading" | "loaded" | "error";
  message: string | null;
}

type ScheduleState =
  | { status: "loading"; appointments: Appointment[]; error: null }
  | { status: "loaded"; appointments: Appointment[]; error: null }
  | { status: "error"; appointments: Appointment[]; error: string };

interface QuickAction {
  label: string;
  detail: string;
  path: string;
  roles: RoleName[];
  icon: typeof UserPlus;
}

const overviewItems = [
  { id: "patients", label: "Patients", apiPath: "/api/v1/patients?size=1", icon: UsersRound },
  { id: "appointments", label: "Total appointments", apiPath: "/api/v1/appointments?size=1", icon: CalendarDays },
  { id: "doctors", label: "Doctors", apiPath: "/api/v1/doctors?size=1", icon: ClipboardPlus }
] as const;

const quickActions: QuickAction[] = [
  { label: "New patient", detail: "Register a patient", path: "/patients", roles: ["ADMIN", "RECEPTIONIST"], icon: UserPlus },
  { label: "New appointment", detail: "Schedule a visit", path: "/appointments", roles: ["ADMIN", "RECEPTIONIST"], icon: CalendarDays },
  { label: "Medical record", detail: "Document clinical care", path: "/medical-records", roles: ["ADMIN", "DOCTOR"], icon: ClipboardPlus },
  { label: "Prescription", detail: "Create a prescription", path: "/prescriptions", roles: ["ADMIN", "DOCTOR"], icon: FilePlus2 }
];

export function DashboardPage() {
  const auth = useAuth();
  const roleKey = auth.user?.roles.join("|") ?? "";
  const [overview, setOverview] = useState<OverviewMetric[]>(() => loadingOverview());
  const [schedule, setSchedule] = useState<ScheduleState>({ status: "loading", appointments: [], error: null });
  const visibleQuickActions = useMemo(
    () => quickActions.filter((action) => auth.hasAnyRole(action.roles)),
    [auth.hasAnyRole, roleKey]
  );
  const isDoctor = auth.hasAnyRole(["DOCTOR"]);
  const primaryRole = auth.user?.roles[0] ?? "RECEPTIONIST";

  useEffect(() => {
    let isCurrent = true;
    setOverview(loadingOverview());
    setSchedule({ status: "loading", appointments: [], error: null });

    void Promise.all([
      Promise.all(overviewItems.map(async (item): Promise<OverviewMetric> => {
        try {
          const page = toPageResponse<unknown>(await apiRequest<unknown>(item.apiPath));
          return { id: item.id, label: item.label, count: page.totalElements, status: "loaded", message: null };
        } catch (error: unknown) {
          return { id: item.id, label: item.label, count: null, status: "error", message: toMessage(error) };
        }
      })),
      appointmentApi.listAppointments({ ...todayWindow(), size: 20, sort: "appointmentDateTime,asc" })
    ]).then(([metrics, appointments]) => {
      if (!isCurrent) return;
      setOverview(metrics);
      setSchedule({ status: "loaded", appointments: appointments.content, error: null });
    }).catch((error: unknown) => {
      if (isCurrent) setSchedule({ status: "error", appointments: [], error: toMessage(error) });
    });

    return () => {
      isCurrent = false;
    };
  }, [roleKey]);

  return (
    <section className="dashboard-page" aria-labelledby="dashboard-heading">
      <div className="dashboard-hero">
        <div className="page-heading">
          <p className="eyebrow">Clinora workspace</p>
          <h1 id="dashboard-heading">Dashboard</h1>
          <p className="page-description">{greeting()}, {displayName(auth.user?.username)}. {roleContext(primaryRole)}</p>
        </div>
        <span className="status-badge neutral">{formatEnum(primaryRole)} workspace</span>
      </div>

      <section aria-labelledby="today-overview-heading">
        <div className="section-heading section-heading-row">
          <div>
            <p className="eyebrow">Today</p>
            <h2 id="today-overview-heading">Operational overview</h2>
          </div>
          <span className="summary-note">{formatDate(new Date())}</span>
        </div>
        <div className="summary-grid dashboard-metrics">
          {overview.map((item) => {
            const Icon = overviewItems.find((overviewItem) => overviewItem.id === item.id)?.icon ?? UsersRound;
            return (
              <article className="summary-card metric-card" key={item.id}>
                <Icon className="metric-icon" aria-hidden="true" />
                <span className="summary-label">{item.label}</span>
                {item.status === "loading" && <strong className="summary-value">Loading...</strong>}
                {item.status === "loaded" && <strong className="summary-value metric-value">{item.count}</strong>}
                {item.status === "loaded" && item.count === 0 && <p className="summary-note">No records yet</p>}
                {item.status === "error" && <p className="summary-note">{item.message}</p>}
              </article>
            );
          })}
        </div>
      </section>

      <section className="dashboard-schedule" aria-labelledby="today-appointments-heading">
        <div className="section-heading section-heading-row">
          <div>
            <p className="eyebrow">Today&apos;s schedule</p>
            <h2 id="today-appointments-heading">{isDoctor ? "Appointments requiring clinical attention" : "Today's appointments"}</h2>
          </div>
          <Link className="secondary-button compact-button" to="/appointments">View all appointments</Link>
        </div>

        {schedule.status === "loading" && (
          <div className="resource-state loading-state" aria-live="polite">
            <span className="loading-dot" aria-hidden="true" />
            Loading today&apos;s appointments...
          </div>
        )}
        {schedule.status === "error" && <p className="form-error" role="alert">{schedule.error}</p>}
        {schedule.status === "loaded" && schedule.appointments.length === 0 && (
          <div className="resource-state empty-state">
            <strong>No appointments today.</strong>
            <span>{isDoctor ? "Clinical work will appear here as appointments are scheduled." : "New appointments can be scheduled from the appointments workspace."}</span>
          </div>
        )}
        {schedule.status === "loaded" && schedule.appointments.length > 0 && (
          <div className="table-panel schedule-table-panel">
            <table className="resource-table">
              <thead>
                <tr>
                  <th scope="col">Time</th>
                  <th scope="col">Patient</th>
                  <th scope="col">Doctor</th>
                  <th scope="col">Status</th>
                  <th scope="col"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {schedule.appointments.map((appointment) => (
                  <tr key={appointment.id}>
                    <td><time className="table-primary" dateTime={appointment.appointmentDateTime}>{formatTime(appointment.appointmentDateTime)}</time></td>
                    <td>
                      <Link className="entity-link" to={`/patients/${appointment.patientId}`}>{appointment.patientName}</Link>
                      <span className="table-secondary">{appointment.reason}</span>
                    </td>
                    <td>{appointment.doctorName}</td>
                    <td><span className={`status-badge appointment-status ${appointment.status.toLowerCase()}`}>{formatEnum(appointment.status)}</span></td>
                    <td><Link className="secondary-button compact-button" to="/appointments">Open</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-labelledby="quick-actions-heading">
        <div className="section-heading">
          <p className="eyebrow">Next step</p>
          <h2 id="quick-actions-heading">Quick actions</h2>
        </div>
        <div className="quick-actions-grid">
          {visibleQuickActions.map((action) => {
            const Icon = action.icon;
            return (
              <Link className="quick-action" key={action.label} to={action.path}>
                <Icon aria-hidden="true" />
                <span>
                  <strong>{action.label}</strong>
                  <small>{action.detail}</small>
                </span>
              </Link>
            );
          })}
        </div>
      </section>
    </section>
  );
}

function loadingOverview(): OverviewMetric[] {
  return overviewItems.map((item) => ({ id: item.id, label: item.label, count: null, status: "loading", message: null }));
}

function todayWindow(): { from: string; to: string } {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { from: toLocalDateTime(start), to: toLocalDateTime(end) };
}

function toLocalDateTime(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  const hour = String(value.getHours()).padStart(2, "0");
  const minute = String(value.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day}T${hour}:${minute}:00`;
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function displayName(username?: string): string {
  return username?.split("@")[0] || "there";
}

function roleContext(role: RoleName): string {
  if (role === "DOCTOR") return "Review today's patient schedule and continue your clinical work.";
  if (role === "ADMIN") return "Keep an eye on clinic activity and move operational work forward.";
  return "Keep the day moving with patient registration and appointment scheduling.";
}

function toMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "Unable to load today's appointments";
}
