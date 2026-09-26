import { toPageResponse } from "./pageResponse";

describe("toPageResponse", () => {
  it("accepts the Spring page response shape used by backend list endpoints", () => {
    expect(toPageResponse({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 20,
      number: 0
    })).toEqual({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 20,
      number: 0
    });
  });

  it("rejects malformed page responses before page components render them", () => {
    expect(() => toPageResponse({ content: [] })).toThrow("Malformed server response");
  });
});
