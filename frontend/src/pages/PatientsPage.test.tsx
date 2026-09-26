import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";
import type { AiPatientHistorySummaryResponse } from "../types/ai";
import type { Patient } from "../types/patient";

describe("PatientsPage", () => {
  it("renders patients from the backend list endpoint", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([patient()])));

    renderApp("/patients");

    expect(await screen.findByRole("heading", { name: "Patients" })).toBeInTheDocument();
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/patients?size=20&sort=lastName,asc"),
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
  });

  it("creates a patient for receptionist users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    const createdPatient = patient({
      id: "22222222-2222-2222-2222-222222222222",
      firstName: "Mira",
      lastName: "Sen",
      phone: "+15555550122"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes("/api/v1/patients") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(createdPatient, { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(page([])));
    });

    renderApp("/patients");
    await user.click(await screen.findByRole("button", { name: /new patient/i }));
    await fillRequiredPatientFields(user, {
      firstName: "Mira",
      lastName: "Sen",
      phone: "+15555550122"
    });
    await user.click(screen.getByRole("button", { name: /create patient/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Patient created");
    expect(await screen.findByText("Mira Sen")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/patients"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"firstName\":\"Mira\"")
      })
    );
  });

  it("validates patient form input before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderApp("/patients");
    await user.click(await screen.findByRole("button", { name: /new patient/i }));
    await user.click(screen.getByRole("button", { name: /create patient/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("First name is required");
    expect(window.fetch).toHaveBeenCalledTimes(1);
  });

  it("rejects today's date of birth before submitting to match backend Past validation", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderApp("/patients");
    await user.click(await screen.findByRole("button", { name: /new patient/i }));
    await user.type(screen.getByLabelText("First name"), "Mira");
    await user.type(screen.getByLabelText("Last name"), "Sen");
    await user.type(screen.getByLabelText("Date of birth"), todayDateInputValue());
    await user.selectOptions(screen.getByLabelText("Gender"), "FEMALE");
    await user.type(screen.getByLabelText("Phone"), "+15555550122");
    await user.click(screen.getByRole("button", { name: /create patient/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Date of birth must be in the past");
    expect(window.fetch).toHaveBeenCalledTimes(1);
  });

  it("updates an existing patient", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    const updatedPatient = patient({ phone: "+15555550999" });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes(`/api/v1/patients/${updatedPatient.id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(updatedPatient));
      }
      return Promise.resolve(mockJsonResponse(page([patient()])));
    });

    renderApp("/patients");
    await user.click(await screen.findByRole("button", { name: /edit/i }));
    await user.clear(screen.getByLabelText("Phone"));
    await user.type(screen.getByLabelText("Phone"), "+15555550999");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Patient updated");
    expect(await screen.findByText("+15555550999")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/patients/${updatedPatient.id}`),
      expect.objectContaining({ method: "PUT" })
    );
  });

  it("allows admins to delete patients", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes("/api/v1/patients/") && init?.method === "DELETE") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(mockJsonResponse(page([patient()])));
    });

    renderApp("/patients");
    const row = await screen.findByRole("row", { name: /asha rao/i });
    await user.click(within(row).getByRole("button", { name: /delete/i }));
    expect(screen.getByRole("dialog", { name: /delete patient/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /delete patient/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Patient deleted");
    expect(screen.queryByText("Asha Rao")).not.toBeInTheDocument();
  });

  it("keeps doctor users in read-only mode", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([patient()])));

    renderApp("/patients");

    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new patient/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ai summary/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete/i })).not.toBeInTheDocument();
  });

  it("shows backend errors without clearing the session", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 403,
      error: "Forbidden",
      message: "Access is denied",
      timestamp: "2026-08-30T00:00:00Z"
    }, { status: 403 }));

    renderApp("/patients");

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("generates an AI patient history summary for doctor users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/patients/${patient().id}/ai/summary`)) {
        return Promise.resolve(mockJsonResponse(aiSummaryResponse()));
      }
      return Promise.resolve(mockJsonResponse(page([patient()])));
    });

    renderApp("/patients");
    const row = await screen.findByRole("row", { name: /asha rao/i });
    await user.click(within(row).getByRole("button", { name: /ai summary/i }));

    expect(await screen.findByRole("heading", { name: "Clinora AI Summary" })).toBeInTheDocument();
    expect(await screen.findByText("Summary is based only on documented records.")).toBeInTheDocument();
    expect(screen.getByText("AI-generated summary. Verify against the patient's records.")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/patients/${patient().id}/ai/summary`),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("hides AI patient history summary from receptionist users", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([patient()])));

    renderApp("/patients");

    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /ai summary/i })).not.toBeInTheDocument();
  });

  it("shows AI patient summary errors and retries", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    let summaryAttempts = 0;
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/patients/${patient().id}/ai/summary`)) {
        summaryAttempts += 1;
        if (summaryAttempts === 1) {
          return Promise.resolve(mockJsonResponse({
            status: 503,
            error: "Service Unavailable",
            message: "AI assistance is temporarily unavailable. You can continue using the patient record manually.",
            timestamp: "2026-09-03T00:00:00Z"
          }, { status: 503 }));
        }
        return Promise.resolve(mockJsonResponse(aiSummaryResponse()));
      }
      return Promise.resolve(mockJsonResponse(page([patient()])));
    });

    renderApp("/patients");
    const row = await screen.findByRole("row", { name: /asha rao/i });
    await user.click(within(row).getByRole("button", { name: /ai summary/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("AI assistance is temporarily unavailable");
    await user.click(screen.getByRole("button", { name: /retry/i }));
    expect(await screen.findByText("Summary is based only on documented records.")).toBeInTheDocument();
  });
});

async function fillRequiredPatientFields(
  user: ReturnType<typeof userEvent.setup>,
  values: { firstName: string; lastName: string; phone: string }
) {
  await user.type(screen.getByLabelText("First name"), values.firstName);
  await user.type(screen.getByLabelText("Last name"), values.lastName);
  await user.type(screen.getByLabelText("Date of birth"), "1990-01-01");
  await user.selectOptions(screen.getByLabelText("Gender"), "FEMALE");
  await user.selectOptions(screen.getByLabelText("Blood group"), "O_POSITIVE");
  await user.type(screen.getByLabelText("Phone"), values.phone);
  await user.type(screen.getByLabelText("Email"), `${values.firstName.toLowerCase()}@example.com`);
}

function patient(overrides: Partial<Patient> = {}): Patient {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    firstName: "Asha",
    lastName: "Rao",
    dateOfBirth: "1990-01-01",
    gender: "FEMALE",
    bloodGroup: "O_POSITIVE",
    phone: "+15555550100",
    email: "asha.rao@example.com",
    address: "123 Clinic Road",
    emergencyContactName: "Ravi Rao",
    emergencyContactPhone: "+15555550101",
    createdAt: "2026-08-30T00:00:00Z",
    updatedAt: "2026-08-30T00:00:00Z",
    ...overrides
  };
}

function page(content: Patient[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0
  };
}

function todayDateInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function aiSummaryResponse(overrides: Partial<AiPatientHistorySummaryResponse> = {}): AiPatientHistorySummaryResponse {
  return {
    summary: "Summary is based only on documented records.",
    recentClinicalActivity: ["Consultation on 2026-09-03"],
    documentedHistory: ["Medical record on 2026-09-03"],
    recentPrescriptions: ["Prescription on 2026-09-03"],
    followUp: ["Follow-up: Review in one week"],
    provider: "mock",
    model: "mock-clinical-assistant-v1",
    generatedAt: "2026-09-03T10:00:00Z",
    notice: "AI-generated summary. Verify against the patient's records.",
    ...overrides
  };
}
