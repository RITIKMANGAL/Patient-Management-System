import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "../layouts/AppLayout";
import { DocumentTitle } from "../components/DocumentTitle";
import { AppointmentsPage } from "../pages/AppointmentsPage";
import { DashboardPage } from "../pages/DashboardPage";
import { DoctorsPage } from "../pages/DoctorsPage";
import { FeedbackPage } from "../pages/FeedbackPage";
import { LoginPage } from "../pages/LoginPage";
import { MedicalRecordsPage } from "../pages/MedicalRecordsPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { PatientsPage } from "../pages/PatientsPage";
import { PatientDetailPage } from "../pages/PatientDetailPage";
import { PrescriptionAccessPage } from "../pages/PrescriptionAccessPage";
import { PrescriptionsPage } from "../pages/PrescriptionsPage";
import { RegisterPage } from "../pages/RegisterPage";
import { ProtectedRoute } from "./ProtectedRoute";
import { PublicRoute } from "./PublicRoute";

export function AppRoutes() {
  return (
    <>
      <DocumentTitle />
      <Routes>
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>

        <Route path="/prescription-access/:token" element={<PrescriptionAccessPage />} />
        <Route path="/feedback/:token" element={<FeedbackPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route element={<ProtectedRoute roles={["ADMIN"]} />}>
              <Route path="/register" element={<RegisterPage />} />
            </Route>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route element={<ProtectedRoute roles={["ADMIN", "DOCTOR", "RECEPTIONIST"]} />}>
              <Route path="/patients" element={<PatientsPage />} />
              <Route path="/patients/:patientId" element={<PatientDetailPage />} />
              <Route path="/doctors" element={<DoctorsPage />} />
              <Route path="/appointments" element={<AppointmentsPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={["ADMIN", "DOCTOR"]} />}>
              <Route path="/medical-records" element={<MedicalRecordsPage />} />
              <Route path="/prescriptions" element={<PrescriptionsPage />} />
            </Route>
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </>
  );
}
