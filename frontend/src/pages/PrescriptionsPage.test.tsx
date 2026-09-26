import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";
import type { Doctor } from "../types/doctor";
import type { Patient } from "../types/patient";
import type { Prescription } from "../types/prescription";

describe("PrescriptionsPage", () => {
  it("renders prescriptions for the selected patient", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => (
      Promise.resolve(mockJsonResponse(pageForUrl(String(input), [prescription()])))
    ));

    renderApp("/prescriptions");

    expect(await screen.findByRole("heading", { name: "Prescriptions" })).toBeInTheDocument();
    expect(await screen.findByText("Synthetic medicine A")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "PDF" })).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/patients/11111111-1111-1111-1111-111111111111/prescriptions?size=20&sort=prescriptionDate,desc"),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("shows loading state while patient and doctor options load", () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderApp("/prescriptions");

    expect(screen.getByText("Loading patients and doctors...")).toBeInTheDocument();
  });

  it("shows an empty prerequisite state when patients or doctors are missing", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation(() => Promise.resolve(mockJsonResponse(page([]))));

    renderApp("/prescriptions");

    expect(await screen.findByText("Patient and doctor records are required.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /new prescription/i })).toBeDisabled();
  });

  it("shows prescription list API errors without clearing the session", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes("/prescriptions")) {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 403 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/prescriptions");

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("shows malformed response errors for invalid prescription pages", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes("/api/v1/patients/") && url.includes("/prescriptions")) {
        return Promise.resolve(mockJsonResponse({ content: [] }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/prescriptions");

    expect(await screen.findByRole("alert")).toHaveTextContent("Malformed server response");
  });

  it("creates a prescription using the backend request fields", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const createdPrescription = prescription({
      id: "44444444-4444-4444-4444-444444444444",
      notes: "Take with food",
      items: [
        prescriptionItem({
          id: "55555555-5555-5555-5555-555555555555",
          medicineName: "Amoxicillin",
          dosage: "500mg",
          frequency: "Twice daily",
          duration: "7 days",
          instructions: "After meals"
        })
      ]
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/prescriptions") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(createdPrescription, { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/prescriptions");
    await user.click(await screen.findByRole("button", { name: /new prescription/i }));
    await user.type(screen.getByLabelText("Medicine name"), "Amoxicillin");
    await user.type(screen.getByLabelText("Dosage"), "500mg");
    await user.type(screen.getByLabelText("Frequency"), "Twice daily");
    await user.type(screen.getByLabelText("Duration"), "7 days");
    await user.type(screen.getByLabelText("Instructions"), "After meals");
    await user.type(screen.getByLabelText("Notes"), "Take with food");
    await user.click(screen.getByRole("button", { name: /create prescription/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Prescription created");
    expect(await screen.findAllByText("Amoxicillin")).toHaveLength(2);
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescriptions"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"patientId\":\"11111111-1111-1111-1111-111111111111\"")
      })
    );
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescriptions"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"medicineName\":\"Amoxicillin\"")
      })
    );
  });

  it("validates required prescription item fields before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input)))));

    renderApp("/prescriptions");
    await user.click(await screen.findByRole("button", { name: /new prescription/i }));
    await user.click(screen.getByRole("button", { name: /create prescription/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Medicine 1 name is required");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescriptions"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("rejects future prescription dates before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input)))));

    renderApp("/prescriptions");
    await user.click(await screen.findByRole("button", { name: /new prescription/i }));
    await user.clear(screen.getByLabelText("Prescription date"));
    await user.type(screen.getByLabelText("Prescription date"), "2099-01-01");
    await user.type(screen.getByLabelText("Medicine name"), "Future medicine");
    await user.type(screen.getByLabelText("Dosage"), "10mg");
    await user.type(screen.getByLabelText("Frequency"), "Daily");
    await user.type(screen.getByLabelText("Duration"), "3 days");
    await user.click(screen.getByRole("button", { name: /create prescription/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Prescription date must not be in the future");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescriptions"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows backend create errors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/prescriptions") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 400,
          error: "Bad Request",
          message: "Prescription must contain at least one item",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 400 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/prescriptions");
    await user.click(await screen.findByRole("button", { name: /new prescription/i }));
    await user.type(screen.getByLabelText("Medicine name"), "Amoxicillin");
    await user.type(screen.getByLabelText("Dosage"), "500mg");
    await user.type(screen.getByLabelText("Frequency"), "Twice daily");
    await user.type(screen.getByLabelText("Duration"), "7 days");
    await user.click(screen.getByRole("button", { name: /create prescription/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Prescription must contain at least one item");
  });

  it("retrieves a prescription detail view", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const currentPrescription = prescription({ notes: "Detailed prescription note" });
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}`)) {
        return Promise.resolve(mockJsonResponse(currentPrescription));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: /view/i }));

    expect(await screen.findByRole("heading", { name: /prescription details/i })).toBeInTheDocument();
    expect(screen.getAllByText("Detailed prescription note")).toHaveLength(2);
  });

  it("does not render development seed markers in prescription notes", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const seededPrescription = prescription({
      notes: "[DEMO:PRESCRIPTION_AVAILABLE] Development-only prescription note"
    });
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${seededPrescription.id}`)) {
        return Promise.resolve(mockJsonResponse(seededPrescription));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [seededPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    expect(within(row).queryByText(/DEMO:PRESCRIPTION_AVAILABLE/)).not.toBeInTheDocument();
    expect(within(row).queryByText(/Development-only prescription note/)).not.toBeInTheDocument();

    await user.click(within(row).getByRole("button", { name: /view/i }));
    expect(await screen.findByRole("heading", { name: /prescription details/i })).toBeInTheDocument();
    expect(screen.queryByText(/DEMO:PRESCRIPTION_AVAILABLE/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Development-only prescription note/)).not.toBeInTheDocument();
  });

  it("opens a generated prescription PDF for authorized users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const currentPrescription = prescription();
    const { createObjectUrl, openWindow } = stubPdfBrowser();
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/pdf`)) {
        return Promise.resolve(new Response(new Blob(["%PDF-1.7"], { type: "application/pdf" }), {
          status: 200,
          headers: {
            "Content-Type": "application/pdf"
          }
        }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "PDF" }));

    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(openWindow).toHaveBeenCalledWith("blob:prescription-pdf", "_blank", "noopener,noreferrer");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/prescriptions/${currentPrescription.id}/pdf`),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("creates and copies a patient access link without persisting the raw token", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const currentPrescription = prescription();
    const clipboard = stubClipboard();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          token: "patient-token-123",
          expiresAt: "2026-09-13T00:00:00Z"
        }));
      }
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && !init?.method) {
        return Promise.resolve(mockJsonResponse({ active: false, expiresAt: null }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "Patient access" }));

    expect(await screen.findByText(/\/prescription-access\/\*{8}$/)).toBeInTheDocument();
    expect(screen.queryByText(/patient-token-123/)).not.toBeInTheDocument();
    expect(screen.getByText(/This one-time link provides access to this prescription PDF/)).toBeInTheDocument();
    expect(screen.getByText(/Expires/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Copy link" }));
    expect(clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining("/prescription-access/patient-token-123"));
    expect(await screen.findByRole("status")).toHaveTextContent("Patient access link copied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toContain("patient-token-123");
    expect(window.sessionStorage.getItem("patient-token-123")).toBeNull();
  });

  it("shows a loading state while patient access is checked", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const currentPrescription = prescription();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && init?.method === "POST") {
        return new Promise<Response>(() => undefined);
      }
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && !init?.method) {
        return Promise.resolve(mockJsonResponse({ active: false, expiresAt: null }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "Patient access" }));

    expect(within(row).getByRole("button", { name: "Checking..." })).toBeDisabled();
  });

  it("shows patient access API errors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const currentPrescription = prescription();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-09-06T00:00:00Z"
        }, { status: 403 }));
      }
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && !init?.method) {
        return Promise.resolve(mockJsonResponse({ active: false, expiresAt: null }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "Patient access" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
  });

  it("revokes the displayed patient access link", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const currentPrescription = prescription();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          token: "patient-token-123",
          expiresAt: "2026-09-13T00:00:00Z"
        }));
      }
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && init?.method === "DELETE") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/access`) && !init?.method) {
        return Promise.resolve(mockJsonResponse({ active: false, expiresAt: null }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "Patient access" }));
    expect(await screen.findByText(/\/prescription-access\/\*{8}$/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Revoke access" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Patient access revoked");
    expect(screen.queryByText(/\/prescription-access\/\*{8}$/)).not.toBeInTheDocument();
  });

  it("shows a loading state while prescription PDF is generated", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const currentPrescription = prescription();
    stubPdfBrowser();
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/pdf`)) {
        return new Promise<Response>(() => undefined);
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "PDF" }));

    expect(within(row).getByRole("button", { name: "Preparing PDF..." })).toBeDisabled();
  });

  it("shows prescription PDF API errors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const currentPrescription = prescription();
    stubPdfBrowser();
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}/pdf`)) {
        return Promise.resolve(mockJsonResponse({
          status: 500,
          error: "Internal Server Error",
          message: "Unable to generate prescription PDF",
          timestamp: "2026-09-05T00:00:00Z"
        }, { status: 500 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: "PDF" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to generate prescription PDF");
  });

  it("shows detail 404 errors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const currentPrescription = prescription();
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes(`/api/v1/prescriptions/${currentPrescription.id}`)) {
        return Promise.resolve(mockJsonResponse({
          status: 404,
          error: "Not Found",
          message: "Prescription not found",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 404 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [currentPrescription])));
    });

    renderApp("/prescriptions");
    const row = await screen.findByRole("row", { name: /synthetic medicine a/i });
    await user.click(within(row).getByRole("button", { name: /view/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Prescription not found");
  });

  it("keeps receptionist users out of the prescriptions route", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));

    renderApp("/prescriptions");

    expect(await screen.findByRole("heading", { name: /access denied/i })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Prescriptions" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Prescriptions" })).not.toBeInTheDocument();
  });
});

function pageForUrl(url: string, prescriptionContent: Prescription[] = []) {
  if (url.includes("/api/v1/patients/") && url.includes("/prescriptions")) {
    return page(prescriptionContent);
  }
  if (url.includes("/api/v1/patients")) {
    return page([patient()]);
  }
  if (url.includes("/api/v1/doctors")) {
    return page([doctor()]);
  }
  return page([]);
}

function prescription(overrides: Partial<Prescription> = {}): Prescription {
  return {
    id: "33333333-3333-3333-3333-333333333333",
    patientId: "11111111-1111-1111-1111-111111111111",
    patientName: "Asha Rao",
    doctorId: "22222222-2222-2222-2222-222222222222",
    doctorName: "Kiran Shah",
    prescriptionDate: "2026-08-30",
    notes: "Synthetic prescription note",
    items: [prescriptionItem()],
    createdAt: "2026-08-30T00:00:00Z",
    updatedAt: "2026-08-30T00:00:00Z",
    ...overrides
  };
}

function prescriptionItem(overrides: Partial<Prescription["items"][number]> = {}): Prescription["items"][number] {
  return {
    id: "55555555-5555-5555-5555-555555555555",
    medicineName: "Synthetic medicine A",
    dosage: "10mg",
    frequency: "Once daily",
    duration: "5 days",
    instructions: "After food",
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

function stubPdfBrowser() {
  const createObjectUrl = vi.fn(() => "blob:prescription-pdf");
  const revokeObjectUrl = vi.fn();
  const openWindow = vi.spyOn(window, "open").mockReturnValue({} as Window);
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: createObjectUrl
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: revokeObjectUrl
  });

  return { createObjectUrl, openWindow, revokeObjectUrl };
}

function stubClipboard() {
  const clipboard = {
    writeText: vi.fn().mockResolvedValue(undefined)
  };
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: clipboard
  });
  return clipboard;
}
