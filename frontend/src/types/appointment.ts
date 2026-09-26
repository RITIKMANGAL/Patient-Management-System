export type AppointmentStatus = "SCHEDULED" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "NO_SHOW";

export interface Appointment {
  id: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  appointmentDateTime: string;
  reason: string;
  status: AppointmentStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AppointmentRequest {
  patientId: string;
  doctorId: string;
  appointmentDateTime: string;
  reason: string;
  status: AppointmentStatus | null;
  notes: string | null;
}
