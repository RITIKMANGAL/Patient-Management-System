import { apiBlobRequest, apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type {
  Prescription,
  PrescriptionAccessStatusResponse,
  PrescriptionAccessTokenResponse,
  PrescriptionRequest
} from "../types/prescription";

const PRESCRIPTIONS_PATH = "/api/v1/prescriptions";

export async function listPatientPrescriptions(patientId: string): Promise<PageResponse<Prescription>> {
  return toPageResponse<Prescription>(
    await apiRequest<unknown>(`/api/v1/patients/${patientId}/prescriptions?size=20&sort=prescriptionDate,desc`)
  );
}

export function getPrescription(id: string): Promise<Prescription> {
  return apiRequest<Prescription>(`${PRESCRIPTIONS_PATH}/${id}`);
}

export function createPrescription(request: PrescriptionRequest): Promise<Prescription> {
  return apiRequest<Prescription>(PRESCRIPTIONS_PATH, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function downloadPrescriptionPdf(id: string): Promise<Blob> {
  return apiBlobRequest(`${PRESCRIPTIONS_PATH}/${id}/pdf`);
}

export function createPrescriptionAccess(id: string): Promise<PrescriptionAccessTokenResponse> {
  return apiRequest<PrescriptionAccessTokenResponse>(`${PRESCRIPTIONS_PATH}/${id}/access`, {
    method: "POST"
  });
}

export function getPrescriptionAccessStatus(id: string): Promise<PrescriptionAccessStatusResponse> {
  return apiRequest<PrescriptionAccessStatusResponse>(`${PRESCRIPTIONS_PATH}/${id}/access`);
}

export function revokePrescriptionAccess(id: string): Promise<void> {
  return apiRequest<void>(`${PRESCRIPTIONS_PATH}/${id}/access`, {
    method: "DELETE"
  });
}

export function downloadPrescriptionAccessPdf(token: string): Promise<Blob> {
  return apiBlobRequest(`/api/v1/prescription-access/${encodeURIComponent(token)}/pdf`, {
    auth: false,
    skipRefresh: true
  });
}
