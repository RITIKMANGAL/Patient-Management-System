import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { mockJsonResponse, renderApp } from "../test/testUtils";

describe("FeedbackPage", () => {
  it("loads public feedback context without an authorization header", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(feedbackContext()));

    renderApp("/feedback/public-token");

    expect(await screen.findByRole("heading", { name: "Rate Your Visit" })).toBeInTheDocument();
    expect(await screen.findByText("Kiran Shah")).toBeInTheDocument();
    const request = vi.mocked(window.fetch).mock.calls[0]?.[1];
    expect((request?.headers as Headers).has("Authorization")).toBe(false);
  });

  it("validates rating before submitting", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(feedbackContext()));

    renderApp("/feedback/public-token");
    await screen.findByText("Kiran Shah");
    await user.click(screen.getByRole("button", { name: /submit feedback/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("Rating is required");
    expect(window.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/feedback-access/public-token"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("submits feedback once through the public endpoint", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (init?.method === "POST") {
        return Promise.resolve(mockJsonResponse(feedbackResponse(), { status: 201 }));
      }
      return Promise.resolve(mockJsonResponse(feedbackContext()));
    });

    renderApp("/feedback/public-token");
    await screen.findByText("Kiran Shah");
    await user.click(screen.getByLabelText("5"));
    await user.type(screen.getByLabelText("Comment"), "Helpful visit");
    await user.click(screen.getByRole("button", { name: /submit feedback/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Thank you. Your feedback has been submitted.");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/feedback-access/public-token"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ rating: 5, comment: "Helpful visit" })
      })
    );
    const request = vi.mocked(window.fetch).mock.calls.find(([, init]) => init?.method === "POST")?.[1];
    expect((request?.headers as Headers).has("Authorization")).toBe(false);
  });

  it("shows already-submitted state from the context endpoint", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(feedbackContext({ submitted: true })));

    renderApp("/feedback/public-token");

    expect(await screen.findByRole("status")).toHaveTextContent("Feedback has already been submitted.");
    expect(screen.queryByRole("button", { name: /submit feedback/i })).not.toBeInTheDocument();
  });

  it("shows a generic error for invalid or expired feedback tokens", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 404,
      error: "Not Found",
      message: "Feedback link is invalid or expired",
      timestamp: "2026-09-06T00:00:00Z"
    }, { status: 404 }));

    renderApp("/feedback/expired-token");

    expect(await screen.findByRole("alert")).toHaveTextContent("Feedback link is invalid or expired");
    expect(screen.queryByRole("button", { name: /submit feedback/i })).not.toBeInTheDocument();
  });

  it("treats duplicate submission conflicts as already submitted", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "fetch").mockImplementation((input, init) => {
      if (init?.method === "POST") {
        return Promise.resolve(mockJsonResponse({
          status: 409,
          error: "Conflict",
          message: "Feedback already submitted",
          timestamp: "2026-09-06T00:00:00Z"
        }, { status: 409 }));
      }
      return Promise.resolve(mockJsonResponse(feedbackContext()));
    });

    renderApp("/feedback/public-token");
    await screen.findByText("Kiran Shah");
    await user.click(screen.getByLabelText("4"));
    await user.click(screen.getByRole("button", { name: /submit feedback/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("Feedback has already been submitted.");
  });
});

function feedbackContext(overrides = {}) {
  return {
    doctorName: "Kiran Shah",
    appointmentDateTime: "2026-09-06T10:00:00",
    submitted: false,
    ...overrides
  };
}

function feedbackResponse() {
  return {
    id: "66666666-6666-6666-6666-666666666666",
    consultationId: "55555555-5555-5555-5555-555555555555",
    patientId: "11111111-1111-1111-1111-111111111111",
    patientName: "Asha Rao",
    doctorId: "22222222-2222-2222-2222-222222222222",
    doctorName: "Kiran Shah",
    appointmentDateTime: "2026-09-06T10:00:00",
    rating: 5,
    comment: "Helpful visit",
    createdAt: "2026-09-06T10:05:00Z"
  };
}
