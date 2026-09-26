export interface FeedbackAccessTokenResponse {
  token: string;
  feedbackUrl: string;
  expiresAt: string;
}

export interface FeedbackContextResponse {
  doctorName: string;
  appointmentDateTime: string;
  submitted: boolean;
}

export interface FeedbackSubmitRequest {
  rating: number;
  comment: string | null;
}

export interface FeedbackResponse {
  id: string;
  consultationId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  appointmentDateTime: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}
