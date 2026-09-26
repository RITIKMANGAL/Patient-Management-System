import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";
import type { Doctor } from "../types/doctor";
import type { MedicalRecord } from "../types/medicalRecord";
import type { Patient } from "../types/patient";

describe("MedicalRecordsPage", () => {
  it("renders medical records for the selected patient", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input), [medicalRecord()]))));

    renderApp("/medical-records");

    expect(await screen.findByRole("heading", { name: "Medical Records" })).toBeInTheDocument();
    expect(await screen.findByText("Synthetic diagnosis")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/patients/11111111-1111-1111-1111-111111111111/medical-records?size=20&sort=recordDate,desc"),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("shows loading state while patient and doctor options load", () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderApp("/medical-records");

    expect(screen.getByText("Loading patients and doctors...")).toBeInTheDocument();
  });

  it("shows an empty prerequisite state when patients or doctors are missing", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes("/api/v1/patients/") && url.includes("/medical-records")) {
        return Promise.resolve(mockJsonResponse(page([])));
      }
      return Promise.resolve(mockJsonResponse(page([])));
    });

    renderApp("/medical-records");

    expect(await screen.findByText("Patient and doctor records are required.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /new medical record/i })).toBeDisabled();
  });

  it("shows record list API errors without clearing the session", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes("/medical-records")) {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 403 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/medical-records");

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("creates a medical record using the backend request fields", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const createdRecord = medicalRecord({
      id: "44444444-4444-4444-4444-444444444444",
      diagnosis: "Migraine",
      symptoms: "Headache",
      notes: "Rest and hydration"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/medical-records") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(createdRecord, { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/medical-records");
    await user.click(await screen.findByRole("button", { name: /new medical record/i }));
    await user.clear(screen.getByLabelText("Diagnosis"));
    await user.type(screen.getByLabelText("Diagnosis"), "Migraine");
    await user.type(screen.getByLabelText("Symptoms"), "Headache");
    await user.type(screen.getByLabelText("Notes"), "Rest and hydration");
    await user.click(screen.getByRole("button", { name: /create medical record/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Medical record created");
    expect(await screen.findAllByText("Migraine")).toHaveLength(2);
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/medical-records"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"patientId\":\"11111111-1111-1111-1111-111111111111\"")
      })
    );
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/medical-records"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"doctorId\":\"22222222-2222-2222-2222-222222222222\"")
      })
    );
  });

  it("validates required medical record fields before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input)))));

    renderApp("/medical-records");
    await user.click(await screen.findByRole("button", { name: /new medical record/i }));
    await user.clear(screen.getByLabelText("Diagnosis"));
    await user.click(screen.getByRole("button", { name: /create medical record/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Diagnosis is required");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/medical-records"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("rejects future record dates before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input)))));

    renderApp("/medical-records");
    await user.click(await screen.findByRole("button", { name: /new medical record/i }));
    await user.clear(screen.getByLabelText("Record date"));
    await user.type(screen.getByLabelText("Record date"), "2099-01-01");
    await user.clear(screen.getByLabelText("Diagnosis"));
    await user.type(screen.getByLabelText("Diagnosis"), "Future diagnosis");
    await user.click(screen.getByRole("button", { name: /create medical record/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Record date must not be in the future");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/medical-records"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("retrieves a medical record detail view", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const record = medicalRecord({ notes: "Detailed clinical note" });
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/medical-records/${record.id}`)) {
        return Promise.resolve(mockJsonResponse(record));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [record])));
    });

    renderApp("/medical-records");
    const row = await screen.findByRole("row", { name: /synthetic diagnosis/i });
    await user.click(within(row).getByRole("button", { name: /view/i }));

    expect(await screen.findByRole("heading", { name: /record details/i })).toBeInTheDocument();
    expect(screen.getByText("Detailed clinical note")).toBeInTheDocument();
  });

  it("keeps receptionist users out of the medical records route", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));

    renderApp("/medical-records");

    expect(await screen.findByRole("heading", { name: /access denied/i })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Medical Records" })).not.toBeInTheDocument();
  });
});

function pageForUrl(url: string, recordContent: MedicalRecord[] = []) {
  if (url.includes("/api/v1/patients/") && url.includes("/medical-records")) {
    return page(recordContent);
  }
  if (url.includes("/api/v1/patients")) {
    return page([patient()]);
  }
  if (url.includes("/api/v1/doctors")) {
    return page([doctor()]);
  }
  return page([]);
}

function medicalRecord(overrides: Partial<MedicalRecord> = {}): MedicalRecord {
  return {
    id: "33333333-3333-3333-3333-333333333333",
    patientId: "11111111-1111-1111-1111-111111111111",
    patientName: "Asha Rao",
    doctorId: "22222222-2222-2222-2222-222222222222",
    doctorName: "Kiran Shah",
    diagnosis: "Synthetic diagnosis",
    symptoms: "Synthetic symptoms",
    notes: "Synthetic notes",
    recordDate: "2026-08-30",
    createdAt: "2026-08-30T00:00:00Z",
    updatedAt: "2026-08-30T00:00:00Z",
    ...overrides
  };
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

function doctor(overrides: Partial<Doctor> = {}): Doctor {
  return {
    id: "22222222-2222-2222-2222-222222222222",
    firstName: "Kiran",
    lastName: "Shah",
    specialization: "Cardiology",
    licenseNumber: "LIC-100",
    phone: "+15555550200",
    email: "kiran.shah@example.com",
    department: "Cardiology",
    createdAt: "2026-08-30T00:00:00Z",
    updatedAt: "2026-08-30T00:00:00Z",
    ...overrides
  };
}

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0
  };
}
