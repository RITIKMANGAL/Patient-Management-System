import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function PublicRoute() {
  const auth = useAuth();

  if (auth.status === "loading") {
    return <div className="page-status">Restoring session...</div>;
  }

  if (auth.isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
