import { screen } from "@testing-library/react";
import { DomainListPage } from "./DomainListPage";
import { mockJsonResponse } from "../test/testUtils";
import { AuthProvider } from "../auth/AuthContext";
import { MemoryRouter } from "react-router-dom";
import { render } from "@testing-library/react";

interface TestItem {
  id: string;
  name: string;
}

describe("DomainListPage", () => {
  it("shows a loading state", () => {
    vi.spyOn(window, "fetch").mockReturnValue(new Promise<Response>(() => undefined));

    renderDomainList();

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("shows an empty state", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse(page([])));

    renderDomainList();

    expect(await screen.findByText("No records found.")).toBeInTheDocument();
  });

  it("shows an API error state", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({
      status: 403,
      error: "Forbidden",
      message: "Access is denied",
      timestamp: "2026-08-30T00:00:00Z"
    }, { status: 403 }));

    renderDomainList();

    expect(await screen.findByRole("alert")).toHaveTextContent("Access is denied");
  });

  it("shows an error state for malformed page responses", async () => {
    vi.spyOn(window, "fetch").mockResolvedValue(mockJsonResponse({ content: [] }));

    renderDomainList();

    expect(await screen.findByRole("alert")).toHaveTextContent("Malformed server response");
  });
});

function renderDomainList() {
  render(
    <MemoryRouter>
      <AuthProvider>
        <DomainListPage<TestItem>
          title="Test Records"
          eyebrow="Testing"
          apiPath="/api/v1/test-records"
          emptyMessage="No records found."
          getRowKey={(item) => item.id}
          columns={[
            { header: "Name", render: (item) => item.name }
          ]}
        />
      </AuthProvider>
    </MemoryRouter>
  );
}

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0
  };
}
