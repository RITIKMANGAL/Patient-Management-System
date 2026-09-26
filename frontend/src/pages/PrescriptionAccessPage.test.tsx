import { screen } from "@testing-library/react";
import { mockJsonResponse, renderApp } from "../test/testUtils";

describe("PrescriptionAccessPage", () => {
  it("loads a prescription PDF through the public access token endpoint", async () => {
    const { createObjectUrl } = stubPdfBrowser();
    vi.spyOn(window, "fetch").mockResolvedValue(new Response(new Blob(["%PDF-1.7"], {
      type: "application/pdf"
    }), {
      status: 200,
      headers: {
        "Content-Type": "application/pdf"
      }
    }));

    renderApp("/prescription-access/public-token");

    expect(await screen.findByRole("heading", { name: "Prescription PDF" })).toBeInTheDocument();
    expect(await screen.findByText("Your prescription is ready to view or download securely.")).toBeInTheDocument();
    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescription-access/public-token/pdf"),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
    const request = vi.mocked(window.fetch).mock.calls[0]?.[1];
    expect((request?.headers as Headers).has("Authorization")).toBe(false);
  });

  it("shows a generic error for invalid patient access tokens", async () => {
    stubPdfBrowser();
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 404,
      error: "Not Found",
      message: "Prescription access link is invalid or expired",
      timestamp: "2026-09-06T00:00:00Z"
    }, { status: 404 }));

    renderApp("/prescription-access/expired-token");

    expect(await screen.findByRole("alert")).toHaveTextContent("Prescription access link is invalid or expired");
    expect(screen.queryByText("Your prescription is ready to view or download securely.")).not.toBeInTheDocument();
  });
});

function stubPdfBrowser() {
  const createObjectUrl = vi.fn(() => "blob:patient-prescription-pdf");
  const revokeObjectUrl = vi.fn();
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: createObjectUrl
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: revokeObjectUrl
  });

  return { createObjectUrl, revokeObjectUrl };
}
