import { displayText, formatDate, formatDateTime, formatEnum, maskSecureUrl, splitInternalMarker } from "./formatters";

describe("formatters", () => {
  it("uses the shared Clinora date and enum presentation", () => {
    expect(formatDate("2026-09-02")).toBe("02 Sep 2026");
    expect(formatDateTime("2026-09-02T14:30:00")).toBe("02 Sep 2026, 14:30");
    expect(formatEnum("O_POSITIVE")).toBe("O Positive");
  });

  it("removes internal markers without losing safe visible text", () => {
    expect(displayText("[DEMO:APPOINTMENT_01] Bring recent reports.")).toBe("Bring recent reports.");
    expect(splitInternalMarker("[DEMO:APPOINTMENT_01] Bring recent reports.")).toEqual({
      marker: "[DEMO:APPOINTMENT_01]",
      text: "Bring recent reports."
    });
  });

  it("does not expose implementation-oriented seed copy", () => {
    expect(displayText("[DEMO:OLD] Demo-only content")).toBe("-");
  });

  it("masks the token segment of secure URLs", () => {
    expect(maskSecureUrl("https://clinic.example/prescription-access/raw-token"))
      .toBe("https://clinic.example/prescription-access/********");
  });
});
