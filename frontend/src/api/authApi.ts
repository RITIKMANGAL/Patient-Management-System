import { apiRequest } from "./apiClient";
import type { LoginRequest, RegisterRequest, TokenResponse, UserResponse } from "../types/auth";

export function login(request: LoginRequest): Promise<TokenResponse> {
  return apiRequest<TokenResponse>("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: JSON.stringify(request)
  });
}

export function register(request: RegisterRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function refresh(refreshToken: string): Promise<TokenResponse> {
  return apiRequest<TokenResponse>("/api/v1/auth/refresh", {
    method: "POST",
    auth: false,
    skipRefresh: true,
    body: JSON.stringify({ refreshToken })
  });
}

export function logout(refreshToken: string): Promise<void> {
  return apiRequest<void>("/api/v1/auth/logout", {
    method: "POST",
    skipRefresh: true,
    body: JSON.stringify({ refreshToken })
  });
}
