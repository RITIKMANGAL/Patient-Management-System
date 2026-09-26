import { ApiError } from "./apiClient";
import { generateConsultationDraft, generatePatientHistorySummary } from "./aiApi";
import { createAccessToken, mockJsonResponse, storeTokens } from "../test/testUtils";

describe("aiApi", () => {
  beforeEach(() => {
    storeTokens(createAccessToken(["DOCTOR"]));
  });

  it("rejects malformed AI consultation draft responses", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({ draft: null }));

    await expect(generateConsultationDraft("consultation-1", {
      roughNotes: "Patient notes",
      chiefComplaint: null,
      symptoms: null,
      examination: null,
      assessment: null,
      treatment: null,
      followUpInstructions: null
    })).rejects.toEqual(expect.objectContaining({
      name: "ApiError",
      message: "Malformed AI draft response"
    } satisfies Partial<ApiError>));
  });

  it("rejects malformed AI patient summary responses", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({ summary: "Missing sections" }));

    await expect(generatePatientHistorySummary("patient-1")).rejects.toEqual(expect.objectContaining({
      name: "ApiError",
      message: "Malformed AI summary response"
    } satisfies Partial<ApiError>));
  });
});
