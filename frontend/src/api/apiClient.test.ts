import { apiBlobRequest, apiRequest, configureApiClient } from "./apiClient";
import { mockJsonResponse } from "../test/testUtils";

describe("apiClient", () => {
  it("does not clear the session on forbidden responses", async () => {
    const clearSession = vi.fn();
    configureApiClient({
      getAccessToken: () => "access-token",
      refreshSession: vi.fn(),
      clearSession
    });
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 403,
      error: "Forbidden",
      message: "Access is denied",
      timestamp: new Date().toISOString()
    }, { status: 403 }));

    await expect(apiRequest("/api/v1/patients")).rejects.toMatchObject({ status: 403 });
    expect(clearSession).not.toHaveBeenCalled();
  });

  it("refreshes once on unauthorized protected requests", async () => {
    const refreshSession = vi.fn().mockResolvedValue("new-access-token");
    configureApiClient({
      getAccessToken: () => "access-token",
      refreshSession,
      clearSession: vi.fn()
    });
    vi.spyOn(window, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({
        status: 401,
        error: "Unauthorized",
        message: "Authentication is required",
        timestamp: new Date().toISOString()
      }, { status: 401 }))
      .mockResolvedValueOnce(mockJsonResponse({ content: [] }));

    await expect(apiRequest("/api/v1/patients")).resolves.toEqual({ content: [] });
    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(window.fetch).toHaveBeenCalledTimes(2);
  });

  it("reports malformed successful JSON responses through the shared API error type", async () => {
    configureApiClient({
      getAccessToken: () => "access-token",
      refreshSession: vi.fn(),
      clearSession: vi.fn()
    });
    vi.spyOn(window, "fetch").mockResolvedValue(new Response("{", {
      status: 200,
      headers: {
        "Content-Type": "application/json"
      }
    }));

    await expect(apiRequest("/api/v1/patients")).rejects.toMatchObject({
      status: 200,
      message: "Malformed server response"
    });
  });

  it("returns blobs for authenticated binary responses", async () => {
    configureApiClient({
      getAccessToken: () => "access-token",
      refreshSession: vi.fn(),
      clearSession: vi.fn()
    });
    vi.spyOn(window, "fetch").mockResolvedValue(new Response(new Blob(["%PDF-1.7"], {
      type: "application/pdf"
    }), {
      status: 200,
      headers: {
        "Content-Type": "application/pdf"
      }
    }));

    const blob = await apiBlobRequest("/api/v1/prescriptions/123/pdf");

    expect(blob.type).toBe("application/pdf");
    expect(window.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prescriptions/123/pdf"),
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("does not attach bearer tokens to public blob requests", async () => {
    configureApiClient({
      getAccessToken: () => "access-token",
      refreshSession: vi.fn(),
      clearSession: vi.fn()
    });
    vi.spyOn(window, "fetch").mockResolvedValue(new Response(new Blob(["%PDF-1.7"], {
      type: "application/pdf"
    }), {
      status: 200,
      headers: {
        "Content-Type": "application/pdf"
      }
    }));

    await apiBlobRequest("/api/v1/prescription-access/public-token/pdf", { auth: false, skipRefresh: true });

    const request = vi.mocked(window.fetch).mock.calls[0]?.[1];
    expect(request?.headers).toBeInstanceOf(Headers);
    expect((request?.headers as Headers).has("Authorization")).toBe(false);
  });
});
