import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type { Doctor, DoctorRequest } from "../types/doctor";

const DOCTORS_PATH = "/api/v1/doctors";
const DEFAULT_LIST_PATH = `${DOCTORS_PATH}?size=20&sort=lastName,asc`;

export async function listDoctors(page = 0): Promise<PageResponse<Doctor>> {
  return toPageResponse<Doctor>(await apiRequest<unknown>(page === 0 ? DEFAULT_LIST_PATH : `${DEFAULT_LIST_PATH}&page=${page}`));
}

export function createDoctor(request: DoctorRequest): Promise<Doctor> {
  return apiRequest<Doctor>(DOCTORS_PATH, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function updateDoctor(id: string, request: DoctorRequest): Promise<Doctor> {
  return apiRequest<Doctor>(`${DOCTORS_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function deleteDoctor(id: string): Promise<void> {
  return apiRequest<void>(`${DOCTORS_PATH}/${id}`, {
    method: "DELETE"
  });
}
