import { ApiError, apiRequest } from "./apiClient";
import type {
  AiConsultationDraftRequest,
  AiConsultationDraftResponse,
  AiPatientHistorySummaryResponse
} from "../types/ai";

export async function generateConsultationDraft(
  consultationId: string,
  request: AiConsultationDraftRequest
): Promise<AiConsultationDraftResponse> {
  const response = await apiRequest<AiConsultationDraftResponse>(`/api/v1/consultations/${consultationId}/ai/draft`, {
    method: "POST",
    body: JSON.stringify(request)
  });
  return validateDraftResponse(response);
}

export async function generatePatientHistorySummary(patientId: string): Promise<AiPatientHistorySummaryResponse> {
  const response = await apiRequest<AiPatientHistorySummaryResponse>(`/api/v1/patients/${patientId}/ai/summary`);
  return validateSummaryResponse(response);
}

function validateDraftResponse(response: AiConsultationDraftResponse): AiConsultationDraftResponse {
  if (!response?.draft
    || !isString(response.draft.chiefComplaint)
    || !isString(response.draft.symptoms)
    || !isString(response.draft.examination)
    || !isString(response.draft.assessment)
    || !isString(response.draft.treatmentAdvice)
    || !isString(response.draft.followUpInstructions)
  ) {
    throw new ApiError(200, "Malformed AI draft response", "Malformed Response");
  }
  return response;
}

function validateSummaryResponse(response: AiPatientHistorySummaryResponse): AiPatientHistorySummaryResponse {
  if (!isString(response?.summary)
    || !Array.isArray(response.recentClinicalActivity)
    || !Array.isArray(response.documentedHistory)
    || !Array.isArray(response.recentPrescriptions)
    || !Array.isArray(response.followUp)
  ) {
    throw new ApiError(200, "Malformed AI summary response", "Malformed Response");
  }
  return response;
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}
