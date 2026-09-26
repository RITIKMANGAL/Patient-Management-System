import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";
import type { AiConsultationDraftResponse } from "../types/ai";
import type { Appointment } from "../types/appointment";
import type { Consultation } from "../types/consultation";
import type { Doctor } from "../types/doctor";
import type { Patient } from "../types/patient";

describe("AppointmentsPage", () => {
  it("renders appointments from the backend list endpoint", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([appointment()])));

    renderApp("/appointments");

    expect(await screen.findByRole("heading", { name: "Appointments" })).toBeInTheDocument();
    expect(await screen.findByText("Annual checkup")).toBeInTheDocument();
    expect(screen.getByText("01 Sep 2099, 10:30")).toBeInTheDocument();
    expect(screen.getByText("Scheduled")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/appointments?size=20&sort=appointmentDateTime,asc"),
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
  });

  it("shows the loading state", () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderApp("/appointments");

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("shows the empty state", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderApp("/appointments");

    expect(await screen.findByText("No appointments found.")).toBeInTheDocument();
  });

  it("shows API errors without clearing the session", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 403,
      error: "Forbidden",
      message: "Access is denied",
      timestamp: "2026-08-30T00:00:00Z"
    }, { status: 403 }));

    renderApp("/appointments");

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("creates an appointment for receptionist users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    const createdAppointment = appointment({
      id: "44444444-4444-4444-4444-444444444444",
      appointmentDateTime: "2099-09-02T11:00:00",
      reason: "Follow up",
      status: "CONFIRMED",
      notes: "Bring reports"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/appointments") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(createdAppointment, { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: /new appointment/i }));
    await fillRequiredAppointmentFields(user, {
      dateTime: "2099-09-02T11:00",
      reason: "Follow up",
      status: "CONFIRMED",
      notes: "Bring reports"
    });
    await user.click(screen.getByRole("button", { name: /create appointment/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Appointment created");
    expect(await screen.findByText("Follow up")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/appointments"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"appointmentDateTime\":\"2099-09-02T11:00:00\"")
      })
    );
  });

  it("validates appointment form input before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input)))));

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: /new appointment/i }));
    await screen.findByLabelText("Patient");
    await user.click(screen.getByRole("button", { name: /create appointment/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Patient is required");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/appointments"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("requires patient and doctor options before scheduling", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);
      if (url.includes("/api/v1/appointments")) {
        return Promise.resolve(mockJsonResponse(page([])));
      }
      return Promise.resolve(mockJsonResponse(page([])));
    });

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: /new appointment/i }));

    expect(await screen.findByText("Patient and doctor records are required.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create appointment/i })).toBeDisabled();
  });

  it("updates an existing appointment and preserves relationship IDs", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    const updatedAppointment = appointment({
      appointmentDateTime: "2099-09-03T12:15:00",
      reason: "Confirmed visit",
      status: "CONFIRMED"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${updatedAppointment.id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(updatedAppointment));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: /edit/i }));
    expect(screen.getByLabelText("Date and time")).toHaveValue("2099-09-01T10:30");
    await user.clear(screen.getByLabelText("Date and time"));
    await user.type(screen.getByLabelText("Date and time"), "2099-09-03T12:15");
    await user.clear(screen.getByLabelText("Reason"));
    await user.type(screen.getByLabelText("Reason"), "Confirmed visit");
    await user.selectOptions(screen.getByLabelText("Status"), "CONFIRMED");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Appointment updated");
    expect(await screen.findByText("Confirmed visit")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/appointments/${updatedAppointment.id}`),
      expect.objectContaining({
        method: "PUT",
        body: expect.stringContaining("\"patientId\":\"11111111-1111-1111-1111-111111111111\"")
      })
    );
  });

  it("hides internal appointment markers while preserving them on update", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const markedAppointment = appointment({ notes: "[DEMO:ACTIVE_CONSULTATION] Bring symptom diary." });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${markedAppointment.id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(markedAppointment));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [markedAppointment])));
    });

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: "Edit" }));

    expect(screen.getByLabelText("Notes")).toHaveValue("Bring symptom diary.");
    expect(screen.queryByDisplayValue(/\[DEMO:/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/appointments/${markedAppointment.id}`),
      expect.objectContaining({
        method: "PUT",
        body: expect.stringContaining("[DEMO:ACTIVE_CONSULTATION] Bring symptom diary.")
      })
    );
  });

  it("does not offer edit or cancel for terminal appointment states", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(pageForUrl("/api/v1/appointments", [
      appointment({ status: "COMPLETED" })
    ])));

    renderApp("/appointments");
    await screen.findByText("Completed");

    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View consultation" })).toBeInTheDocument();
  });

  it("cancels appointments with confirmation", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/appointments/") && init?.method === "DELETE") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^cancel$/i }));
    const dialog = screen.getByRole("dialog", { name: /cancel appointment/i });
    expect(dialog).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: /cancel appointment/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Appointment cancelled");
    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/appointments/${appointment().id}`),
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it("does not cancel when the confirmation is dismissed", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => Promise.resolve(mockJsonResponse(pageForUrl(String(input), [appointment()]))));

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^cancel$/i }));
    const dialog = screen.getByRole("dialog", { name: /cancel appointment/i });
    await user.click(within(dialog).getByRole("button", { name: /^cancel$/i }));

    expect(screen.queryByRole("dialog", { name: /cancel appointment/i })).not.toBeInTheDocument();
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/appointments/"),
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it.each([
    ["ADMIN", true],
    ["RECEPTIONIST", true],
    ["DOCTOR", false]
  ] as const)("shows create permissions for %s users", async (role, canCreate) => {
    storeTokens(createAccessToken([role]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([appointment()])));

    renderApp("/appointments");

    expect(await screen.findByText("Annual checkup")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new appointment/i }) !== null).toBe(canCreate);
    expect(screen.getByRole("button", { name: /edit/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^cancel$/i })).toBeInTheDocument();
  });

  it("shows forbidden errors returned by appointment mutations", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes("/api/v1/appointments") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 403 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url)));
    });

    renderApp("/appointments");
    await user.click(await screen.findByRole("button", { name: /new appointment/i }));
    await fillRequiredAppointmentFields(user, {
      dateTime: "2099-09-02T11:00",
      reason: "Follow up"
    });
    await user.click(screen.getByRole("button", { name: /create appointment/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("starts a consultation for doctor users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));

    expect(await screen.findByRole("heading", { name: "Consultation" })).toBeInTheDocument();
    expect(await screen.findByRole("status")).toHaveTextContent("Consultation started");
    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/appointments/${appointment().id}/consultation`),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("loads an existing consultation when the start request reports a duplicate", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 409,
          error: "Conflict",
          message: "Consultation already exists for appointment",
          timestamp: "2026-09-03T00:00:00Z"
        }, { status: 409 }));
      }
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`)) {
        return Promise.resolve(mockJsonResponse(consultation({ chiefComplaint: "Existing complaint" })));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));

    expect(await screen.findByDisplayValue("Existing complaint")).toBeInTheDocument();
  });

  it("saves consultation progress", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(consultation({ chiefComplaint: "Chest pain" })));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.type(await screen.findByLabelText("Chief complaint"), "Chest pain");
    await user.click(screen.getByRole("button", { name: /save progress/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Consultation saved");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}`),
      expect.objectContaining({
        method: "PUT",
        body: expect.stringContaining("\"chiefComplaint\":\"Chest pain\"")
      })
    );
  });

  it("completes a consultation and updates the appointment status", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}/complete`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation({
          status: "COMPLETED",
          appointmentStatus: "COMPLETED",
          completedAt: "2026-09-03T10:30:00Z"
        })));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(consultation({
          chiefComplaint: "Chest pain",
          assessment: "Musculoskeletal pain"
        })));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.type(await screen.findByLabelText("Chief complaint"), "Chest pain");
    await user.type(screen.getByLabelText("Assessment / diagnosis"), "Musculoskeletal pain");
    await user.click(screen.getByRole("button", { name: /complete consultation/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Consultation completed");
    expect(await screen.findAllByText("Completed")).toHaveLength(2);
    expect(await screen.findByText("Completed consultations are read-only.")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}/complete`),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("creates a feedback link for a completed consultation", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText }
    });
    storeTokens(createAccessToken(["DOCTOR"]));
    const completedAppointment = appointment({ status: "COMPLETED" });
    const completedConsultation = consultation({
      status: "COMPLETED",
      appointmentStatus: "COMPLETED",
      completedAt: "2026-09-03T10:30:00Z"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${completedAppointment.id}/consultation`)) {
        return Promise.resolve(mockJsonResponse(completedConsultation));
      }
      if (url.includes(`/api/v1/consultations/${completedConsultation.id}/feedback-access`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          token: "feedback-token",
          feedbackUrl: "http://localhost:5173/feedback/feedback-token",
          expiresAt: "2026-09-13T10:00:00Z"
        }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [completedAppointment])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /view consultation/i }));
    await user.click(await screen.findByRole("button", { name: /create feedback link/i }));

    expect(await screen.findByText("http://localhost:5173/feedback/********")).toBeInTheDocument();
    expect(screen.queryByText(/feedback-token/)).not.toBeInTheDocument();
    expect(screen.getByText(/Expires 13 Sep 2026/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /copy link/i }));
    expect(writeText).toHaveBeenCalledWith("http://localhost:5173/feedback/feedback-token");
    expect(await screen.findByRole("status")).toHaveTextContent("Feedback link copied");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${completedConsultation.id}/feedback-access`),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows feedback link creation errors returned by the backend", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const completedAppointment = appointment({ status: "COMPLETED" });
    const completedConsultation = consultation({
      status: "COMPLETED",
      appointmentStatus: "COMPLETED",
      completedAt: "2026-09-03T10:30:00Z"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${completedAppointment.id}/consultation`)) {
        return Promise.resolve(mockJsonResponse(completedConsultation));
      }
      if (url.includes(`/api/v1/consultations/${completedConsultation.id}/feedback-access`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-09-03T00:00:00Z"
        }, { status: 403 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [completedAppointment])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /view consultation/i }));
    await user.click(await screen.findByRole("button", { name: /create feedback link/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
  });

  it("validates consultation completion before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.click(await screen.findByRole("button", { name: /complete consultation/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Chief complaint is required before completion");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}/complete`),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows consultation API errors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 400,
          error: "Bad Request",
          message: "Consultation can only be started for scheduled or confirmed appointments",
          timestamp: "2026-09-03T00:00:00Z"
        }, { status: 400 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Consultation can only be started for scheduled or confirmed appointments");
  });

  it("does not show consultation actions to receptionist users", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([appointment()])));

    renderApp("/appointments");

    expect(await screen.findByText("Annual checkup")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^consultation$/i })).not.toBeInTheDocument();
  });

  it("generates an AI consultation draft without automatically saving clinical fields", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}/ai/draft`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(aiDraftResponse()));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(consultation({ chiefComplaint: "Fever" })));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.type(await screen.findByLabelText("Rough clinical notes"), "Patient has fever for 3 days.");
    await user.click(screen.getByRole("button", { name: /generate ai draft/i }));

    expect(await screen.findAllByText("AI-generated draft - review before saving.")).toHaveLength(2);
    expect(screen.getByText("Temperature 100.4 F")).toBeInTheDocument();
    expect(screen.getByLabelText("Chief complaint")).toHaveValue("");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}`),
      expect.objectContaining({ method: "PUT" })
    );

    await user.click(screen.getByRole("button", { name: /apply draft to form/i }));
    expect(screen.getByLabelText("Chief complaint")).toHaveValue("Fever");
    await user.click(screen.getByRole("button", { name: /save progress/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Consultation saved");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}`),
      expect.objectContaining({
        method: "PUT",
        body: expect.stringContaining("\"chiefComplaint\":\"Fever\"")
      })
    );
  });

  it("does not send unchanged existing consultation fields as AI draft context", async () => {
    const user = userEvent.setup();
    const roughNotes = "Patient reports mild headache for two days and symptoms are improving today.";
    const existingConsultation = consultation({
      chiefComplaint: "Synthetic active demo concern",
      symptoms: "Demo-only fever used to seed local test data",
      examination: "Demo-only exam finding",
      assessment: "Demo-only assessment",
      treatment: "Demo-only treatment",
      followUpInstructions: "Demo-only follow up"
    });
    const aiDraftBodies: Array<Record<string, unknown>> = [];
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(existingConsultation, { status: 201 }));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}/ai/draft`) && init?.method === "POST") {
        aiDraftBodies.push(JSON.parse(String(init.body ?? "{}")));
        return Promise.resolve(mockJsonResponse(aiDraftResponse()));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    expect(await screen.findByLabelText("Chief complaint")).toHaveValue("Synthetic active demo concern");
    await user.type(screen.getByLabelText("Rough clinical notes"), roughNotes);
    await user.click(screen.getByRole("button", { name: /generate ai draft/i }));

    expect(await screen.findAllByText("AI-generated draft - review before saving.")).toHaveLength(2);
    expect(aiDraftBodies).toHaveLength(1);
    expect(aiDraftBodies[0]).toMatchObject({
      roughNotes,
      chiefComplaint: null,
      symptoms: null,
      examination: null,
      assessment: null,
      treatment: null,
      followUpInstructions: null
    });
    expect(JSON.stringify(aiDraftBodies[0])).not.toContain("Synthetic active demo concern");
    expect(JSON.stringify(aiDraftBodies[0])).not.toContain("Demo-only fever");
  });

  it("requires rough notes before generating an AI consultation draft", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["DOCTOR"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.click(await screen.findByRole("button", { name: /generate ai draft/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Rough clinical notes are required");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/consultations/${consultation().id}/ai/draft`),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows AI consultation draft API errors and allows retry", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    let draftAttempts = 0;
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes(`/api/v1/appointments/${appointment().id}/consultation`) && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(consultation(), { status: 201 }));
      }
      if (url.includes(`/api/v1/consultations/${consultation().id}/ai/draft`) && init?.method === "POST") {
        draftAttempts += 1;
        if (draftAttempts === 1) {
          return Promise.resolve(mockJsonResponse({
            status: 503,
            error: "Service Unavailable",
            message: "AI assistance is temporarily unavailable. You can continue entering the consultation manually.",
            timestamp: "2026-09-03T00:00:00Z"
          }, { status: 503 }));
        }
        return Promise.resolve(mockJsonResponse(aiDraftResponse()));
      }
      return Promise.resolve(mockJsonResponse(pageForUrl(url, [appointment()])));
    });

    renderApp("/appointments");
    const row = await screen.findByRole("row", { name: /annual checkup/i });
    await user.click(within(row).getByRole("button", { name: /^consultation$/i }));
    await user.type(await screen.findByLabelText("Rough clinical notes"), "Patient has fever for 3 days.");
    await user.click(screen.getByRole("button", { name: /generate ai draft/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("AI assistance is temporarily unavailable");
    await user.click(screen.getByRole("button", { name: /generate ai draft/i }));
    expect(await screen.findAllByText("AI-generated draft - review before saving.")).toHaveLength(2);
  });
});

async function fillRequiredAppointmentFields(
  user: ReturnType<typeof userEvent.setup>,
  values: {
    dateTime: string;
    reason: string;
    status?: "SCHEDULED" | "CONFIRMED";
    notes?: string;
  }
) {
  await screen.findByLabelText("Patient");
  await user.selectOptions(screen.getByLabelText("Patient"), "11111111-1111-1111-1111-111111111111");
  await user.selectOptions(screen.getByLabelText("Doctor"), "22222222-2222-2222-2222-222222222222");
  await user.type(screen.getByLabelText("Date and time"), values.dateTime);
  if (values.status) {
    await user.selectOptions(screen.getByLabelText("Status"), values.status);
  }
  await user.type(screen.getByLabelText("Reason"), values.reason);
  if (values.notes) {
    await user.type(screen.getByLabelText("Notes"), values.notes);
  }
}

function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: "33333333-3333-3333-3333-333333333333",
    patientId: "11111111-1111-1111-1111-111111111111",
    patientName: "Asha Rao",
    doctorId: "22222222-2222-2222-2222-222222222222",
    doctorName: "Kiran Shah",
    appointmentDateTime: "2099-09-01T10:30:00",
    reason: "Annual checkup",
    status: "SCHEDULED",
    notes: "First visit",
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

function pageForUrl(url: string, appointmentContent: Appointment[] = []) {
  if (url.includes("/api/v1/patients")) {
    return page([patient()]);
  }
  if (url.includes("/api/v1/doctors")) {
    return page([doctor()]);
  }
  return page(appointmentContent);
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

function consultation(overrides: Partial<Consultation> = {}): Consultation {
  return {
    id: "55555555-5555-5555-5555-555555555555",
    appointmentId: "33333333-3333-3333-3333-333333333333",
    patientId: "11111111-1111-1111-1111-111111111111",
    patientName: "Asha Rao",
    doctorId: "22222222-2222-2222-2222-222222222222",
    doctorName: "Kiran Shah",
    appointmentDateTime: "2099-09-01T10:30:00",
    appointmentStatus: "CONFIRMED",
    status: "IN_PROGRESS",
    chiefComplaint: null,
    symptoms: null,
    examination: null,
    assessment: null,
    treatment: null,
    followUpInstructions: null,
    startedAt: "2026-09-03T10:00:00Z",
    completedAt: null,
    createdAt: "2026-09-03T10:00:00Z",
    updatedAt: "2026-09-03T10:00:00Z",
    ...overrides
  };
}

function aiDraftResponse(overrides: Partial<AiConsultationDraftResponse> = {}): AiConsultationDraftResponse {
  return {
    draft: {
      chiefComplaint: "Fever",
      symptoms: "Cough",
      examination: "Temperature 100.4 F",
      assessment: "Not provided",
      treatmentAdvice: "Rest and fluids",
      followUpInstructions: "Not provided"
    },
    provider: "mock",
    model: "mock-clinical-assistant-v1",
    generatedAt: "2026-09-03T10:00:00Z",
    notice: "AI-generated draft - review before saving.",
    ...overrides
  };
}
