import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createAccessToken, mockJsonResponse, renderApp, storeTokens } from "../test/testUtils";

describe("AppRoutes", () => {
  it("renders route-backed resource pages from sidebar navigation", async () => {
    const user = userEvent.setup();
    storeTokens(createAccessToken(["RECEPTIONIST"]));
    mockResourceRequests();

    renderApp("/dashboard");

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Patients" }));
    expect(await screen.findByRole("heading", { name: "Patients" })).toBeInTheDocument();
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Dashboard" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Doctors" }));
    expect(await screen.findByRole("heading", { name: "Doctors" })).toBeInTheDocument();
    expect(await screen.findByText("Kiran Shah")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Appointments" }));
    expect(await screen.findByRole("heading", { name: "Appointments" })).toBeInTheDocument();
    expect(await screen.findByText("Annual checkup")).toBeInTheDocument();
  });

  it.each([
    ["/patients", "Patients", "Asha Rao"],
    ["/doctors", "Doctors", "Kiran Shah"],
    ["/medical-records", "Medical Records", "Synthetic diagnosis"],
    ["/prescriptions", "Prescriptions", "Synthetic medicine A"],
    ["/appointments", "Appointments", "Annual checkup"]
  ])("renders %s from direct navigation", async (path, heading, content) => {
    storeTokens(createAccessToken(["DOCTOR"]));
    mockResourceRequests();

    renderApp(path);

    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
    expect(await screen.findByText(content)).toBeInTheDocument();
  });

  it("highlights only the matching real navigation item", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    mockResourceRequests();

    renderApp("/prescriptions");

    expect(await screen.findByRole("heading", { name: "Prescriptions" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).not.toHaveClass("active");
    expect(screen.getByRole("link", { name: "Medical Records" })).not.toHaveClass("active");
    expect(screen.getByRole("link", { name: "Prescriptions" })).toHaveClass("active");
  });

  it("uses consistent icon components for navigation without letter indicators", async () => {
    storeTokens(createAccessToken(["DOCTOR"]));
    mockResourceRequests();

    const { container } = renderApp("/dashboard");

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(container.querySelectorAll(".sidebar .nav-icon")).toHaveLength(6);
    expect(container.querySelector(".sidebar .nav-initial")).toBeNull();
  });
});

function mockResourceRequests() {
  vi.spyOn(window, "fetch").mockImplementation((input) => {
    const url = String(input);

    if (url.includes("/api/v1/patients/") && url.includes("/medical-records")) {
      return Promise.resolve(mockJsonResponse(page([
        {
          id: "44444444-4444-4444-4444-444444444444",
          patientId: "11111111-1111-1111-1111-111111111111",
          patientName: "Asha Rao",
          doctorId: "22222222-2222-2222-2222-222222222222",
          doctorName: "Kiran Shah",
          diagnosis: "Synthetic diagnosis",
          symptoms: "Synthetic symptoms",
          notes: "Synthetic notes",
          recordDate: "2026-08-30",
          createdAt: "2026-08-30T00:00:00Z",
          updatedAt: "2026-08-30T00:00:00Z"
        }
      ])));
    }

    if (url.includes("/api/v1/patients/") && url.includes("/prescriptions")) {
      return Promise.resolve(mockJsonResponse(page([
        {
          id: "55555555-5555-5555-5555-555555555555",
          patientId: "11111111-1111-1111-1111-111111111111",
          patientName: "Asha Rao",
          doctorId: "22222222-2222-2222-2222-222222222222",
          doctorName: "Kiran Shah",
          prescriptionDate: "2026-08-30",
          notes: "Synthetic prescription note",
          items: [
            {
              id: "66666666-6666-6666-6666-666666666666",
              medicineName: "Synthetic medicine A",
              dosage: "10mg",
              frequency: "Once daily",
              duration: "5 days",
              instructions: "After food"
            }
          ],
          createdAt: "2026-08-30T00:00:00Z",
          updatedAt: "2026-08-30T00:00:00Z"
        }
      ])));
    }

    if (url.includes("/api/v1/patients")) {
      return Promise.resolve(mockJsonResponse(page([
        {
          id: "11111111-1111-1111-1111-111111111111",
          firstName: "Asha",
          lastName: "Rao",
          dateOfBirth: "1990-01-01",
          gender: "FEMALE",
          bloodGroup: "O_POSITIVE",
          phone: "+15555550100",
          email: "asha.rao@example.com"
        }
      ])));
    }

    if (url.includes("/api/v1/doctors")) {
      return Promise.resolve(mockJsonResponse(page([
        {
          id: "22222222-2222-2222-2222-222222222222",
          firstName: "Kiran",
          lastName: "Shah",
          specialization: "Cardiology",
          licenseNumber: "LIC-100",
          phone: "+15555550200",
          email: "kiran.shah@example.com",
          department: "Cardiology"
        }
      ])));
    }

    if (url.includes("/api/v1/appointments")) {
      return Promise.resolve(mockJsonResponse(page([
        {
          id: "33333333-3333-3333-3333-333333333333",
          patientName: "Asha Rao",
          doctorName: "Kiran Shah",
          appointmentDateTime: "2026-09-01T10:00:00",
          reason: "Annual checkup",
          status: "SCHEDULED"
        }
      ])));
    }

    return Promise.resolve(mockJsonResponse(page([])));
  });
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
