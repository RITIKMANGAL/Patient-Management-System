export interface AiConsultationDraft {
  chiefComplaint: string;
  symptoms: string;
  examination: string;
  assessment: string;
  treatmentAdvice: string;
  followUpInstructions: string;
}

export interface AiConsultationDraftRequest {
  roughNotes: string;
  chiefComplaint: string | null;
  symptoms: string | null;
  examination: string | null;
  assessment: string | null;
  treatment: string | null;
  followUpInstructions: string | null;
}

export interface AiConsultationDraftResponse {
  draft: AiConsultationDraft;
  provider: string;
  model: string;
  generatedAt: string;
  notice: string;
}

export interface AiPatientHistorySummaryResponse {
  summary: string;
  recentClinicalActivity: string[];
  documentedHistory: string[];
  recentPrescriptions: string[];
  followUp: string[];
  provider: string;
  model: string;
  generatedAt: string;
  notice: string;
}
