import { getDemoConfiguration } from "./demo";

describe("getDemoConfiguration", () => {
  it("enables only an explicit demo build flag", () => {
    expect(getDemoConfiguration({ VITE_DEMO_MODE: "true", VITE_DEMO_USERNAME: "demo-admin@clinora.app", VITE_DEMO_PASSWORD: "public-demo" }))
      .toEqual({ enabled: true, username: "demo-admin@clinora.app", password: "public-demo" });
    expect(getDemoConfiguration({ VITE_DEMO_MODE: "false" }).enabled).toBe(false);
  });
});
