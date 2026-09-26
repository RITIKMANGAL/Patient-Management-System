import { useEffect } from "react";
import { useLocation } from "react-router-dom";

const titles: Record<string, string> = {
  "/login": "Sign in",
  "/register": "Staff accounts",
  "/dashboard": "Dashboard",
  "/patients": "Patients",
  "/doctors": "Doctors",
  "/appointments": "Appointments",
  "/medical-records": "Medical Records",
  "/prescriptions": "Prescriptions"
};

export function DocumentTitle() {
  const { pathname } = useLocation();

  useEffect(() => {
    document.title = getDocumentTitle(pathname);
  }, [pathname]);

  return null;
}

export function getDocumentTitle(pathname: string): string {
  if (pathname.startsWith("/patients/")) {
    return "Clinora - Patient Profile";
  }
  if (pathname.startsWith("/prescription-access/")) {
    return "Clinora - Secure Prescription Access";
  }
  if (pathname.startsWith("/feedback/")) {
    return "Clinora - Feedback";
  }

  const pageName = titles[pathname];
  return pageName ? `Clinora - ${pageName}` : "Clinora";
}
