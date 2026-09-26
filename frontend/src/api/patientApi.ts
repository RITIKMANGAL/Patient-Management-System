import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type { Patient, PatientRequest } from "../types/patient";

const PATIENTS_PATH = "/api/v1/patients";
const DEFAULT_LIST_PATH = `${PATIENTS_PATH}?size=20&sort=lastName,asc`;

export async function listPatients(): Promise<PageResponse<Patient>> {
  return toPageResponse<Patient>(await apiRequest<unknown>(DEFAULT_LIST_PATH));
}

export function getPatient(id: string): Promise<Patient> {
  return apiRequest<Patient>(`${PATIENTS_PATH}/${id}`);
}

export function createPatient(request: PatientRequest): Promise<Patient> {
  return apiRequest<Patient>(PATIENTS_PATH, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function updatePatient(id: string, request: PatientRequest): Promise<Patient> {
  return apiRequest<Patient>(`${PATIENTS_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function deletePatient(id: string): Promise<void> {
  return apiRequest<void>(`${PATIENTS_PATH}/${id}`, {
    method: "DELETE"
  });
}
