import { ApiError } from "./apiClient";
import type { PageResponse } from "../types/api";

export function toPageResponse<T>(value: unknown): PageResponse<T> {
  if (!isObject(value) || !Array.isArray(value.content)) {
    throw malformedPageResponse();
  }

  if (
    typeof value.totalElements !== "number" ||
    typeof value.totalPages !== "number" ||
    typeof value.size !== "number" ||
    typeof value.number !== "number"
  ) {
    throw malformedPageResponse();
  }

  return {
    content: value.content as T[],
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    size: value.size,
    number: value.number
  };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function malformedPageResponse(): ApiError {
  return new ApiError(0, "Malformed server response", "Malformed Response");
}
