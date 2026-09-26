import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type { Appointment, AppointmentRequest } from "../types/appointment";

const APPOINTMENTS_PATH = "/api/v1/appointments";
const DEFAULT_LIST_PATH = `${APPOINTMENTS_PATH}?size=20&sort=appointmentDateTime,asc`;

interface AppointmentListOptions {
  patientId?: string;
  doctorId?: string;
  from?: string;
  to?: string;
  size?: number;
  sort?: string;
}

export async function listAppointments(options: AppointmentListOptions = {}): Promise<PageResponse<Appointment>> {
  const searchParams = new URLSearchParams();
  if (options.patientId) {
    searchParams.set("patientId", options.patientId);
  }
  if (options.doctorId) {
    searchParams.set("doctorId", options.doctorId);
  }
  if (options.from) {
    searchParams.set("from", options.from);
  }
  if (options.to) {
    searchParams.set("to", options.to);
  }
  searchParams.set("size", String(options.size ?? 20));
  searchParams.set("sort", options.sort ?? "appointmentDateTime,asc");

  return toPageResponse<Appointment>(
    await apiRequest<unknown>(options.patientId || options.doctorId || options.from || options.to
      ? `${APPOINTMENTS_PATH}?${searchParams.toString()}`
      : DEFAULT_LIST_PATH)
  );
}

export function createAppointment(request: AppointmentRequest): Promise<Appointment> {
  return apiRequest<Appointment>(APPOINTMENTS_PATH, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function updateAppointment(id: string, request: AppointmentRequest): Promise<Appointment> {
  return apiRequest<Appointment>(`${APPOINTMENTS_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function cancelAppointment(id: string): Promise<void> {
  return apiRequest<void>(`${APPOINTMENTS_PATH}/${id}`, {
    method: "DELETE"
  });
}
