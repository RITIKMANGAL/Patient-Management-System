import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";
import type { Doctor } from "../types/doctor";

describe("DoctorsPage", () => {
  it("renders doctors from the backend list endpoint", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([doctor()])));

    renderApp("/doctors");

    expect(await screen.findByRole("heading", { name: "Doctors" })).toBeInTheDocument();
    expect(await screen.findByText("Kiran Shah")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/doctors?size=20&sort=lastName,asc"),
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
  });

  it("shows the loading state", () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderApp("/doctors");

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("shows the empty state", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderApp("/doctors");

    expect(await screen.findByText("No doctors found.")).toBeInTheDocument();
  });

  it("shows API errors without clearing the session", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 403,
      error: "Forbidden",
      message: "Access is denied",
      timestamp: "2026-08-30T00:00:00Z"
    }, { status: 403 }));

    renderApp("/doctors");

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).not.toBeNull();
  });

  it("creates a doctor for admin users", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const createdDoctor = doctor({
      id: "22222222-2222-2222-2222-222222222222",
      firstName: "Meera",
      lastName: "Nair",
      licenseNumber: "LIC-200"
    });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes("/api/v1/doctors") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(createdDoctor, { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(page([])));
    });

    renderApp("/doctors");
    await user.click(await screen.findByRole("button", { name: /new doctor/i }));
    await fillRequiredDoctorFields(user, {
      firstName: "Meera",
      lastName: "Nair",
      specialization: "Neurology",
      licenseNumber: "LIC-200",
      phone: "+15555550222"
    });
    await user.click(screen.getByRole("button", { name: /create doctor/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Doctor created");
    expect(await screen.findByText("Meera Nair")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/doctors"),
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("\"licenseNumber\":\"LIC-200\"")
      })
    );
  });

  it("validates doctor form input before submitting", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderApp("/doctors");
    await user.click(await screen.findByRole("button", { name: /new doctor/i }));
    await user.click(screen.getByRole("button", { name: /create doctor/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("First name is required");
    expect(window.fetch).toHaveBeenCalledTimes(1);
  });

  it("updates an existing doctor", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const updatedDoctor = doctor({ department: "Emergency Medicine" });
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes(`/api/v1/doctors/${updatedDoctor.id}`) && init?.method === "PUT") {
        return Promise.resolve(mockJsonResponse(updatedDoctor));
      }
      return Promise.resolve(mockJsonResponse(page([doctor()])));
    });

    renderApp("/doctors");
    await user.click(await screen.findByRole("button", { name: /edit/i }));
    await user.clear(screen.getByLabelText("Department"));
    await user.type(screen.getByLabelText("Department"), "Emergency Medicine");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Doctor updated");
    expect(await screen.findByText("Emergency Medicine")).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/doctors/${updatedDoctor.id}`),
      expect.objectContaining({ method: "PUT" })
    );
  });

  it("allows admins to delete doctors", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes("/api/v1/doctors/") && init?.method === "DELETE") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(mockJsonResponse(page([doctor()])));
    });

    renderApp("/doctors");
    const row = await screen.findByRole("row", { name: /kiran shah/i });
    await user.click(within(row).getByRole("button", { name: /delete/i }));
    expect(screen.getByRole("dialog", { name: /delete doctor/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /delete doctor/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Doctor deleted");
    expect(screen.queryByText("Kiran Shah")).not.toBeInTheDocument();
  });

  it.each([
    "DOCTOR",
    "RECEPTIONIST"
  ] as const)("keeps %s users in read-only mode", async (role) => {
    storeTokens(createAccessToken([role]));
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([doctor()])));

    renderApp("/doctors");

    expect(await screen.findByText("Kiran Shah")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new doctor/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete/i })).not.toBeInTheDocument();
  });

  it("shows duplicate license errors from the backend", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (String(input).includes("/api/v1/doctors") && init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 409,
          error: "Conflict",
          message: "Doctor license number already exists",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 409 }));
      }
      return Promise.resolve(mockJsonResponse(page([])));
    });

    renderApp("/doctors");
    await user.click(await screen.findByRole("button", { name: /new doctor/i }));
    await fillRequiredDoctorFields(user, {
      firstName: "Kiran",
      lastName: "Shah",
      specialization: "Cardiology",
      licenseNumber: "LIC-100",
      phone: "+15555550200"
    });
    await user.click(screen.getByRole("button", { name: /create doctor/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Doctor license number already exists");
  });
});

async function fillRequiredDoctorFields(
  user: ReturnType<typeof userEvent.setup>,
  values: {
    firstName: string;
    lastName: string;
    specialization: string;
    licenseNumber: string;
    phone: string;
  }
) {
  await user.type(screen.getByLabelText("First name"), values.firstName);
  await user.type(screen.getByLabelText("Last name"), values.lastName);
  await user.type(screen.getByLabelText("Specialization"), values.specialization);
  await user.type(screen.getByLabelText("License number"), values.licenseNumber);
  await user.type(screen.getByLabelText("Phone"), values.phone);
  await user.type(screen.getByLabelText("Email"), `${values.firstName.toLowerCase()}@example.com`);
  await user.type(screen.getByLabelText("Department"), values.specialization);
}

function doctor(overrides: Partial<Doctor> = {}): Doctor {
  return {
    id: "11111111-1111-1111-1111-111111111111",
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

function page(content: Doctor[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0
  };
}
