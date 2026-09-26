import { getDocumentTitle } from "./DocumentTitle";

describe("getDocumentTitle", () => {
  it.each([
    ["/dashboard", "Clinora - Dashboard"],
    ["/patients", "Clinora - Patients"],
    ["/appointments", "Clinora - Appointments"],
    ["/prescription-access/public-token", "Clinora - Secure Prescription Access"],
    ["/feedback/public-token", "Clinora - Feedback"],
    ["/not-found", "Clinora"]
  ])("maps %s to a client-facing Clinora title", (pathname, expectedTitle) => {
    expect(getDocumentTitle(pathname)).toBe(expectedTitle);
  });
});
