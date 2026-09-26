import type { TokenResponse } from "../types/auth";

const STORAGE_KEY = "patient-management.auth.tokens.v1";

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export function loadStoredTokens(): StoredTokens | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) {
      return null;
    }
    const parsed = JSON.parse(stored) as Partial<StoredTokens>;
    if (
      typeof parsed.accessToken !== "string" ||
      typeof parsed.refreshToken !== "string" ||
      parsed.tokenType !== "Bearer" ||
      typeof parsed.expiresIn !== "number"
    ) {
      clearStoredTokens();
      return null;
    }
    return {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      tokenType: parsed.tokenType,
      expiresIn: parsed.expiresIn
    };
  } catch {
    clearStoredTokens();
    return null;
  }
}

export function saveStoredTokens(tokens: TokenResponse): void {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: tokens.tokenType,
      expiresIn: tokens.expiresIn
    })
  );
}

export function clearStoredTokens(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}
