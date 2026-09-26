import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";

describe("DashboardPage", () => {
  it("allows authenticated users to access the dashboard", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    mockOverviewRequests();

    renderApp("/dashboard");

    expect(await screen.findByRole("heading", { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByText("Role: DOCTOR")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /appointments requiring clinical attention/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /medical record document clinical care/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /new appointment/i })).not.toBeInTheDocument();
    expect(await screen.findByText("12")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("clears authentication state after logout", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    renderApp("/dashboard");
    await user.click(await screen.findByRole("button", { name: /logout/i }));

    await waitFor(() => expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument());
    expect(window.localStorage.getItem("patient-management.auth.tokens.v1")).toBeNull();
  });

  it("shows dashboard overview loading state", () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderApp("/dashboard");

    expect(screen.getAllByText("Loading...")).toHaveLength(3);
  });

  it("shows dashboard overview empty and error states", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    vi.spyOn(window, "fetch").mockImplementation((input) => {
      const url = String(input);

      if (url.includes("/api/v1/patients")) {
        return Promise.resolve(mockJsonResponse(page([], 0)));
      }

      if (url.includes("/api/v1/doctors")) {
        return Promise.resolve(mockJsonResponse({
          status: 403,
          error: "Forbidden",
          message: "Access is denied",
          timestamp: "2026-08-30T00:00:00Z"
        }, { status: 403 }));
      }

      return Promise.resolve(mockJsonResponse(page([], 3)));
    });

    renderApp("/dashboard");

    expect(await screen.findByText("No records yet")).toBeInTheDocument();
    expect(await screen.findByText("Access is denied")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("prioritizes patient registration and scheduling for receptionist users", async () => {
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    mockOverviewRequests();

    renderApp("/dashboard");

    expect(await screen.findByRole("heading", { name: /today's appointments/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /new patient/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /new appointment/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /^medical record/i })).not.toBeInTheDocument();
  });

  it("shows administrative workflow actions for admin users", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    mockOverviewRequests();

    renderApp("/dashboard");

    expect(await screen.findByText("Role: ADMIN")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /new patient/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /new appointment/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /medical record document clinical care/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /prescription create a prescription/i })).toBeInTheDocument();
  });

  it("refreshes an expired stored session before showing the dashboard", async () => {
    storeTokens(createAccessToken(["ADMIN"], -60), "stored-refresh-token");
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      accessToken: createAccessToken(["ADMIN"]),
      refreshToken: "rotated-refresh-token",
      tokenType: "Bearer",
      expiresIn: 900
    }));

    renderApp("/dashboard");

    expect(await screen.findByRole("heading", { name: /dashboard/i })).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/auth/refresh"),
      expect.objectContaining({ method: "POST" })
    );
  });
});

function mockOverviewRequests() {
  vi.spyOn(window, "fetch").mockImplementation((input) => {
    const url = String(input);

    if (url.includes("/api/v1/patients")) {
      return Promise.resolve(mockJsonResponse(page([], 12)));
    }

    if (url.includes("/api/v1/doctors")) {
      return Promise.resolve(mockJsonResponse(page([], 4)));
    }

    if (url.includes("/api/v1/appointments")) {
      return Promise.resolve(mockJsonResponse(page([], 7)));
    }

    return Promise.resolve(mockJsonResponse(page([], 0)));
  });
}

function page<T>(content: T[], totalElements = content.length) {
  return {
    content,
    totalElements,
    totalPages: totalElements > 0 ? 1 : 0,
    size: 1,
    number: 0
  };
}
