import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../auth/AuthContext";
import { AppRoutes } from "../routes/AppRoutes";
import type { RoleName } from "../types/auth";

export function renderApp(initialPath = "/") {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>
  );
}

export function createAccessToken(roles: RoleName[] = ["RECEPTIONIST"], expiresInSeconds = 900): string {
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    sub: "11111111-1111-1111-1111-111111111111",
    username: "user@example.com",
    roles,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds
  };

  return `${base64Url(header)}.${base64Url(payload)}.synthetic-signature`;
}

export function storeTokens(accessToken = createAccessToken(), refreshToken = "refresh-token") {
  window.localStorage.setItem(
    "patient-management.auth.tokens.v1",
    JSON.stringify({
      accessToken,
      refreshToken,
      tokenType: "Bearer",
      expiresIn: 900
    })
  );
}

export function mockJsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: {
      "Content-Type": "application/json",
      ...(init.headers ?? {})
    }
  });
}

function base64Url(value: unknown): string {
  return window
    .btoa(JSON.stringify(value))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}
