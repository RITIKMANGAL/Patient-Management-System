import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { mockJsonResponse, renderApp, storeTokens, createAccessToken } from "../test/testUtils";

const patientId = "11111111-1111-1111-1111-111111111111";

describe("PatientDetailPage", () => {
  it("renders a scoped patient profile with its real supported timeline data", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    mockPatientDetailRequests();

    renderApp(`/patients/${patientId}`);

    expect(await screen.findByRole("heading", { name: "Asha Rao" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Clinical timeline" })).toBeInTheDocument();
    expect(screen.getByText("Annual review with Dr. Kiran Shah")).toBeInTheDocument();
    expect(screen.getAllByText("Hypertension review").length).toBeGreaterThan(0);
    expect(screen.getByText("Lisinopril")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /generate summary/i })).toBeInTheDocument();
  });

  it("keeps clinical history and AI controls out of receptionist UX", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    mockPatientDetailRequests();

    renderApp(`/patients/${patientId}`);

    expect(await screen.findByRole("heading", { name: "Asha Rao" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Patient History Summary" })).not.toBeInTheDocument();
    expect(screen.queryByText("Medical records")).not.toBeInTheDocument();
  });

  it("renders a safe not-found state for an invalid patient ID", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 404,
      error: "Not Found",
      message: "Patient not found"
    }, { status: 404 }));

    renderApp(`/patients/${patientId}`);

    expect(await screen.findByText("Patient profile was not found.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("generates an AI summary only after explicit clinician action", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    mockPatientDetailRequests();

    renderApp(`/patients/${patientId}`);
    await user.click(await screen.findByRole("button", { name: /generate summary/i }));

    expect(await screen.findByText("AI-generated summary. Review before clinical use.")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/patients/${patientId}/ai/summary`),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });
});

function mockPatientDetailRequests() {
  vi.spyOn(window, "fetch").mockImplementation((input) => {
    const url = String(input);
    if (url.includes(`/api/v1/patients/${patientId}/ai/summary`)) {
      return Promise.resolve(mockJsonResponse({
        summary: "Documented patient history summary.",
        recentClinicalActivity: ["Appointment documented"],
        documentedHistory: ["Hypertension review"],
        recentPrescriptions: ["Lisinopril"],
        followUp: [],
        provider: "mock",
        model: "mock-clinical-assistant-v1",
        generatedAt: "2026-09-21T10:00:00Z",
        notice: "AI-generated summary. Review before clinical use."
      }));
    }
    if (url.includes(`/api/v1/patients/${patientId}/consultations`)) return Promise.resolve(mockJsonResponse(page([consultation()])));
    if (url.includes(`/api/v1/patients/${patientId}/medical-records`)) return Promise.resolve(mockJsonResponse(page([medicalRecord()])));
    if (url.includes(`/api/v1/patients/${patientId}/prescriptions`)) return Promise.resolve(mockJsonResponse(page([prescription()])));
    if (url.includes("/api/v1/appointments")) return Promise.resolve(mockJsonResponse(page([appointment()])));
    if (url.includes(`/api/v1/patients/${patientId}`)) return Promise.resolve(mockJsonResponse(patient()));
    return Promise.resolve(mockJsonResponse(page([])));
  });
}

function patient() {
  return {
    id: patientId, firstName: "Asha", lastName: "Rao", dateOfBirth: "1990-01-01", gender: "FEMALE", bloodGroup: "O_POSITIVE",
    phone: "+15555550100", email: "asha@example.com", address: null, emergencyContactName: null, emergencyContactPhone: null,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z"
  };
}

function appointment() {
  return {
    id: "appointment-id", patientId, patientName: "Asha Rao", doctorId: "doctor-id", doctorName: "Dr. Kiran Shah",
    appointmentDateTime: "2026-09-22T10:00:00", reason: "Annual review", status: "SCHEDULED", notes: null,
    createdAt: "2026-09-01T00:00:00Z", updatedAt: "2026-09-01T00:00:00Z"
  };
}

function consultation() {
  return {
    id: "consultation-id", appointmentId: "appointment-id", patientId, patientName: "Asha Rao", doctorId: "doctor-id", doctorName: "Dr. Kiran Shah",
    appointmentDateTime: "2026-09-20T10:00:00", appointmentStatus: "COMPLETED", status: "COMPLETED", chiefComplaint: "Review",
    symptoms: null, examination: null, assessment: "Hypertension review", treatment: null, followUpInstructions: null,
    startedAt: "2026-09-20T10:00:00", completedAt: "2026-09-20T10:30:00", createdAt: "2026-09-20T10:00:00", updatedAt: "2026-09-20T10:30:00"
  };
}

function medicalRecord() {
  return { id: "record-id", patientId, patientName: "Asha Rao", doctorId: "doctor-id", doctorName: "Dr. Kiran Shah", diagnosis: "Hypertension review", symptoms: null, notes: null, recordDate: "2026-09-20", createdAt: "2026-09-20T00:00:00Z", updatedAt: "2026-09-20T00:00:00Z" };
}

function prescription() {
  return { id: "prescription-id", patientId, patientName: "Asha Rao", doctorId: "doctor-id", doctorName: "Dr. Kiran Shah", prescriptionDate: "2026-09-20", notes: null, items: [{ id: "item-id", medicineName: "Lisinopril", dosage: "10mg", frequency: "Daily", duration: "30 days", instructions: null }], createdAt: "2026-09-20T00:00:00Z", updatedAt: "2026-09-20T00:00:00Z" };
}

function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, size: 20, number: 0 };
}
