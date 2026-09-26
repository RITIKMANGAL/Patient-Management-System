import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { RoleName } from "../types/auth";
import { ForbiddenPage } from "../pages/ForbiddenPage";

interface ProtectedRouteProps {
  roles?: RoleName[];
}

export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.status === "loading") {
    return <div className="page-status">Restoring session...</div>;
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (roles && !auth.hasAnyRole(roles)) {
    return <ForbiddenPage />;
  }

  return <Outlet />;
}
