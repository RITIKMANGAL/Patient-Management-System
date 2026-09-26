export type FieldErrors = Record<string, string>;

export interface ValidationResult {
  message: string;
  fields: FieldErrors;
}

export function personNameError(value: string, label: string): string | null {
  if (!value.trim()) {
    return `${label} is required`;
  }
  return /^[\p{L}][\p{L}\p{M}'’.-]*(?:[ \t]+[\p{L}][\p{L}\p{M}'’.-]*)*$/u.test(value.trim())
    ? null
    : `${label} must be a valid name`;
}

export function phoneError(value: string, label = "Phone"): string | null {
  const normalized = value.trim();
  return /^(?=(?:.*\d){7,})\+?[0-9 .()\-]{7,25}$/.test(normalized)
    ? null
    : `${label} must be a valid phone number`;
}

export function optionalEmailError(value: string): string | null {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized) ? null : "Email must be valid";
}

export function textError(value: string, label: string, required = false): string | null {
  const normalized = value.trim();
  if (!normalized) {
    return required ? `${label} is required` : null;
  }
  return /\p{L}/u.test(normalized) ? null : `${label} must contain letters`;
}

export function validationResult(fields: FieldErrors): ValidationResult | null {
  const first = Object.values(fields)[0];
  return first ? { message: first, fields } : null;
}
