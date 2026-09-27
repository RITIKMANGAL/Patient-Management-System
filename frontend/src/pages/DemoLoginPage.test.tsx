import { screen } from "@testing-library/react";
import { createAccessToken, renderApp, storeTokens } from "../test/testUtils";

vi.mock("../config/demo", () => ({
  demoConfiguration: {
    enabled: true,
    username: "demo-admin@clinora.app",
    password: "public-demo-password"
  }
}));

describe("demo presentation", () => {
  it("shows the configured synthetic-data notice and public demo credentials on login", async () => {
    renderApp("/login");

    expect(await screen.findByText("DEMO ENVIRONMENT")).toBeInTheDocument();
    expect(screen.getByText("Synthetic data only. Data may be reset.")).toBeInTheDocument();
    expect(screen.getByText("demo-admin@clinora.app")).toBeInTheDocument();
    expect(screen.getByText("public-demo-password")).toBeInTheDocument();
  });

  it("shows the demo banner inside the authenticated application shell", async () => {
    storeTokens(createAccessToken(["ADMIN"]));
    renderApp("/dashboard");

    expect(await screen.findByText("DEMO ENVIRONMENT")).toBeInTheDocument();
  });
});
