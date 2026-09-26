import type { AppointmentStatus } from "./appointment";

export type ConsultationStatus = "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

export interface Consultation {
  id: string;
  appointmentId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  appointmentDateTime: string;
  appointmentStatus: AppointmentStatus;
  status: ConsultationStatus;
  chiefComplaint: string | null;
  symptoms: string | null;
  examination: string | null;
  assessment: string | null;
  treatment: string | null;
  followUpInstructions: string | null;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConsultationUpdateRequest {
  chiefComplaint: string | null;
  symptoms: string | null;
  examination: string | null;
  assessment: string | null;
  treatment: string | null;
  followUpInstructions: string | null;
}
