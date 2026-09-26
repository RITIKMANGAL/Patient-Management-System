import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp } from "../test/testUtils";

describe("LoginPage", () => {
  it("renders the login form", async () => {
    renderApp("/login");

    expect(await screen.findByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it("shows client validation before submitting", async () => {
    const user = userEvent.setup();
    renderApp("/login");

    await user.click(await screen.findByRole("button", { name: /sign in/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Email is required");
  });

  it("navigates to the dashboard after successful login", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      accessToken: createAccessToken(["ADMIN"]),
      refreshToken: "refresh-token",
      tokenType: "Bearer",
      expiresIn: 900
    }));

    renderApp("/login");
    await user.type(await screen.findByLabelText(/email/i), "admin@example.com");
    await user.type(screen.getByLabelText(/password/i), "StrongPass123");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("heading", { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByText("Role: ADMIN")).toBeInTheDocument();
  });

  it("shows backend validation errors when login fails", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 401,
      error: "Unauthorized",
      message: "Authentication failed",
      timestamp: new Date().toISOString()
    }, { status: 401 }));

    renderApp("/login");
    await user.type(await screen.findByLabelText(/email/i), "missing@example.com");
    await user.type(screen.getByLabelText(/password/i), "WrongPass123");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Authentication failed");
    expect(screen.queryByRole("heading", { name: /dashboard/i })).not.toBeInTheDocument();
  });

  it("redirects protected routes to login for unauthenticated users", async () => {
    renderApp("/dashboard");

    await waitFor(() => expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument());
  });
});
