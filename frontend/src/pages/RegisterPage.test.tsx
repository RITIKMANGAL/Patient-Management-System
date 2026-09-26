import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";

describe("RegisterPage", () => {
  it("requires a doctor record for doctor provisioning", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    const fetch = vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      content: [], totalPages: 0, totalElements: 0, number: 0, size: 20, first: true, last: true
    }));
    renderApp("/register");
    await user.type(await screen.findByLabelText(/first name/i), "Synthetic");
    await user.type(screen.getByLabelText(/last name/i), "Doctor");
    await user.type(screen.getByLabelText(/email/i), "synthetic@example.com");
    await user.type(screen.getByLabelText(/password/i), "SyntheticPassword123");
    await user.selectOptions(screen.getByLabelText("Role"), "DOCTOR");
    expect(await screen.findByText("No doctors found.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /create account/i }));
    expect(screen.getByRole("alert")).toHaveTextContent("Select a doctor record");
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String(fetch.mock.calls[0][0])).toContain("/api/v1/doctors");
  });

  it("requires authentication", async () => {
    renderApp("/register");
    expect(await screen.findByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create one/i })).not.toBeInTheDocument();
  });

  it.each(["DOCTOR", "RECEPTIONIST"] as const)("denies %s staff provisioning", async (role) => {
    storeTokens(createAccessToken([role]));
    renderApp("/register");
    expect(await screen.findByText(/access denied/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /create account/i })).not.toBeInTheDocument();
  });

  it("validates registration input", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["ADMIN"]));
    renderApp("/register");

    await user.click(await screen.findByRole("button", { name: /create account/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("First and last name are required");
  });

  it("submits registration and shows the created role", async () => {
    const user = userEvent.setup();
    const accessToken = createAccessToken(["ADMIN"]);
    storeTokens(accessToken);
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      id: "11111111-1111-1111-1111-111111111111",
      username: "frontdesk@example.com",
      firstName: "Front",
      lastName: "Desk",
      enabled: true,
      roles: ["RECEPTIONIST"],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }, { status: 201 }));

    renderApp("/register");
    await user.type(await screen.findByLabelText(/first name/i), "Front");
    await user.type(screen.getByLabelText(/last name/i), "Desk");
    await user.type(screen.getByLabelText(/email/i), "frontdesk@example.com");
    await user.type(screen.getByLabelText(/password/i), "StrongPass123");
    await user.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("RECEPTIONIST");
    const [, options] = vi.mocked(window.fetch).mock.calls[0];
    expect(new Headers(options?.headers).get("Authorization")).toBe(`Bearer ${accessToken}`);
    expect(JSON.parse(String(options?.body)).role).toBe("RECEPTIONIST");
  });
});
