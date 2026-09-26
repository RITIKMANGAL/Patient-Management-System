const INTERNAL_MARKER = /^\s*\[DEMO:[^\]]+\]\s*/;
const IMPLEMENTATION_COPY = /\b(?:demo-only|development-only|fictional clinical note)\b/i;
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function formatDate(value: string | Date): string {
  const date = value instanceof Date ? value : new Date(`${value}T00:00:00`);
  return `${twoDigits(date.getDate())} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`;
}

export function formatDateTime(value: string): string {
  const date = new Date(value);
  return `${formatDate(date)}, ${formatTimeParts(date)}`;
}

export function formatTime(value: string): string {
  return formatTimeParts(new Date(value));
}

export function formatEnum(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function displayText(value: string | null | undefined, fallback = "-"): string {
  const visible = value?.replace(INTERNAL_MARKER, "").trim();
  if (visible && IMPLEMENTATION_COPY.test(visible)) {
    return fallback;
  }
  return visible || fallback;
}

export function splitInternalMarker(value: string | null | undefined): { marker: string; text: string } {
  if (!value) {
    return { marker: "", text: "" };
  }
  const marker = value.match(INTERNAL_MARKER)?.[0].trim() ?? "";
  return { marker, text: value.replace(INTERNAL_MARKER, "").trim() };
}

export function maskSecureUrl(value: string): string {
  try {
    const url = new URL(value);
    const path = url.pathname.replace(/\/[^/]+\/?$/, "/********");
    return `${url.origin}${path}`;
  } catch {
    return "********";
  }
}

function formatTimeParts(value: Date): string {
  return `${twoDigits(value.getHours())}:${twoDigits(value.getMinutes())}`;
}

function twoDigits(value: number): string {
  return String(value).padStart(2, "0");
}
