import { apiRequest } from "./apiClient";
import { toPageResponse } from "./pageResponse";
import type { PageResponse } from "../types/api";
import type {
  FeedbackAccessTokenResponse,
  FeedbackContextResponse,
  FeedbackResponse,
  FeedbackSubmitRequest
} from "../types/feedback";

export function createFeedbackAccess(consultationId: string): Promise<FeedbackAccessTokenResponse> {
  return apiRequest<FeedbackAccessTokenResponse>(`/api/v1/consultations/${consultationId}/feedback-access`, {
    method: "POST"
  });
}

export function revokeFeedbackAccess(consultationId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/consultations/${consultationId}/feedback-access`, {
    method: "DELETE"
  });
}

export function getFeedbackContext(token: string): Promise<FeedbackContextResponse> {
  return apiRequest<FeedbackContextResponse>(`/api/v1/feedback-access/${encodeURIComponent(token)}`, {
    auth: false,
    skipRefresh: true
  });
}

export function submitFeedback(token: string, request: FeedbackSubmitRequest): Promise<FeedbackResponse> {
  return apiRequest<FeedbackResponse>(`/api/v1/feedback-access/${encodeURIComponent(token)}`, {
    method: "POST",
    body: JSON.stringify(request),
    auth: false,
    skipRefresh: true
  });
}

export async function listFeedback(): Promise<PageResponse<FeedbackResponse>> {
  return toPageResponse<FeedbackResponse>(
    await apiRequest<unknown>("/api/v1/feedback?size=20&sort=createdAt,desc")
  );
}
