import type { ErrorResponse } from "../types/api";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

interface ApiClientConfig {
  getAccessToken: () => string | null;
  refreshSession: () => Promise<string | null>;
  clearSession: () => void;
}

interface ApiRequestOptions extends RequestInit {
  auth?: boolean;
  skipRefresh?: boolean;
}

let config: ApiClientConfig | null = null;

export class ApiError extends Error {
  readonly status: number;
  readonly error: string;
  readonly details?: ErrorResponse;

  constructor(status: number, message: string, error: string, details?: ErrorResponse) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.error = error;
    this.details = details;
  }
}

export function configureApiClient(nextConfig: ApiClientConfig): void {
  config = nextConfig;
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  return requestWithOptionalRefresh<T>(path, options, false);
}

export async function apiBlobRequest(path: string, options: ApiRequestOptions = {}): Promise<Blob> {
  return requestBlobWithOptionalRefresh(path, options, false);
}

async function requestWithOptionalRefresh<T>(
  path: string,
  options: ApiRequestOptions,
  hasRetried: boolean
): Promise<T> {
  const response = await fetch(buildUrl(path), buildRequest(options));

  if (response.status === 401 && shouldAttemptRefresh(options, hasRetried)) {
    const refreshedAccessToken = await config?.refreshSession();
    if (refreshedAccessToken) {
      return requestWithOptionalRefresh<T>(path, options, true);
    }
    config?.clearSession();
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    throw new ApiError(response.status, "Malformed server response", "Malformed Response");
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(response.status, "Malformed server response", "Malformed Response");
  }
}

async function requestBlobWithOptionalRefresh(
  path: string,
  options: ApiRequestOptions,
  hasRetried: boolean
): Promise<Blob> {
  const response = await fetch(buildUrl(path), buildRequest(options));

  if (response.status === 401 && shouldAttemptRefresh(options, hasRetried)) {
    const refreshedAccessToken = await config?.refreshSession();
    if (refreshedAccessToken) {
      return requestBlobWithOptionalRefresh(path, options, true);
    }
    config?.clearSession();
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.blob();
}

function buildRequest(options: ApiRequestOptions): RequestInit {
  const headers = new Headers(options.headers);

  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }

  if (options.auth !== false) {
    const accessToken = config?.getAccessToken();
    if (accessToken) {
      headers.set("Authorization", `Bearer ${accessToken}`);
    }
  }

  return {
    ...options,
    headers
  };
}

function buildUrl(path: string): string {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }
  return `${API_BASE_URL.replace(/\/$/, "")}${path.startsWith("/") ? path : `/${path}`}`;
}

function shouldAttemptRefresh(options: ApiRequestOptions, hasRetried: boolean): boolean {
  return options.auth !== false && !options.skipRefresh && !hasRetried && config !== null;
}

async function toApiError(response: Response): Promise<ApiError> {
  const details = await parseError(response);
  const message = details?.message ?? fallbackMessage(response.status);
  const error = details?.error ?? response.statusText;
  return new ApiError(response.status, message, error, details);
}

async function parseError(response: Response): Promise<ErrorResponse | undefined> {
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    return undefined;
  }

  try {
    return (await response.json()) as ErrorResponse;
  } catch {
    return undefined;
  }
}

function fallbackMessage(status: number): string {
  if (status === 401) {
    return "Authentication is required";
  }
  if (status === 403) {
    return "Access is denied";
  }
  return "Request failed";
}
