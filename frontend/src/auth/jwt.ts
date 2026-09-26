import type { AuthUser, RoleName } from "../types/auth";

interface JwtPayload {
  sub?: unknown;
  username?: unknown;
  roles?: unknown;
  exp?: unknown;
}

export function userFromAccessToken(accessToken: string): AuthUser | null {
  const payload = decodeJwtPayload(accessToken);
  if (!payload) {
    return null;
  }

  if (
    typeof payload.sub !== "string" ||
    typeof payload.username !== "string" ||
    typeof payload.exp !== "number" ||
    !Array.isArray(payload.roles)
  ) {
    return null;
  }

  const roles = payload.roles.filter(isRoleName);
  if (roles.length === 0) {
    return null;
  }

  return {
    id: payload.sub,
    username: payload.username,
    roles,
    expiresAt: payload.exp
  };
}

export function isAccessTokenCurrent(accessToken: string): boolean {
  const user = userFromAccessToken(accessToken);
  if (!user) {
    return false;
  }
  return user.expiresAt * 1000 > Date.now();
}

function decodeJwtPayload(accessToken: string): JwtPayload | null {
  const parts = accessToken.split(".");
  if (parts.length !== 3) {
    return null;
  }

  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    return JSON.parse(window.atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

function isRoleName(value: unknown): value is RoleName {
  return value === "ADMIN" || value === "DOCTOR" || value === "RECEPTIONIST";
}
