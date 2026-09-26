import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type { Consultation, ConsultationUpdateRequest } from "../types/consultation";

export function startAppointmentConsultation(appointmentId: string): Promise<Consultation> {
  return apiRequest<Consultation>(`/api/v1/appointments/${appointmentId}/consultation`, {
    method: "POST"
  });
}

export function getAppointmentConsultation(appointmentId: string): Promise<Consultation> {
  return apiRequest<Consultation>(`/api/v1/appointments/${appointmentId}/consultation`);
}

export function getConsultation(id: string): Promise<Consultation> {
  return apiRequest<Consultation>(`/api/v1/consultations/${id}`);
}

export function updateConsultation(id: string, request: ConsultationUpdateRequest): Promise<Consultation> {
  return apiRequest<Consultation>(`/api/v1/consultations/${id}`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function completeConsultation(id: string): Promise<Consultation> {
  return apiRequest<Consultation>(`/api/v1/consultations/${id}/complete`, {
    method: "POST"
  });
}

export async function listPatientConsultations(patientId: string): Promise<PageResponse<Consultation>> {
  return toPageResponse<Consultation>(
    await apiRequest<unknown>(`/api/v1/patients/${patientId}/consultations?size=20&sort=startedAt,desc`)
  );
}
