import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type { MedicalRecord, MedicalRecordRequest } from "../types/medicalRecord";

const MEDICAL_RECORDS_PATH = "/api/v1/medical-records";

export async function listPatientMedicalRecords(patientId: string): Promise<PageResponse<MedicalRecord>> {
  return toPageResponse<MedicalRecord>(
    await apiRequest<unknown>(`/api/v1/patients/${patientId}/medical-records?size=20&sort=recordDate,desc`)
  );
}

export function getMedicalRecord(id: string): Promise<MedicalRecord> {
  return apiRequest<MedicalRecord>(`${MEDICAL_RECORDS_PATH}/${id}`);
}

export function createMedicalRecord(request: MedicalRecordRequest): Promise<MedicalRecord> {
  return apiRequest<MedicalRecord>(MEDICAL_RECORDS_PATH, {
    method: "POST",
    body: JSON.stringify(request)
  });
}
