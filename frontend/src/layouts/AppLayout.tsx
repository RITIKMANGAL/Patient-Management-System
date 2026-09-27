import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import {
  CalendarDays,
  ClipboardList,
  FileText,
  LayoutDashboard,
  Stethoscope,
  UsersRound,
  UserPlus
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { DemoEnvironmentBanner } from "../components/DemoEnvironmentBanner";
import { demoConfiguration } from "../config/demo";
import type { RoleName } from "../types/auth";
import type { LucideIcon } from "lucide-react";

interface NavigationItem {
  label: string;
  path: string;
  roles: RoleName[];
  icon: LucideIcon;
}

const navigationItems: NavigationItem[] = [
  { label: "Patients", path: "/patients", roles: ["ADMIN", "DOCTOR", "RECEPTIONIST"], icon: UsersRound },
  { label: "Doctors", path: "/doctors", roles: ["ADMIN", "DOCTOR", "RECEPTIONIST"], icon: Stethoscope },
  { label: "Appointments", path: "/appointments", roles: ["ADMIN", "DOCTOR", "RECEPTIONIST"], icon: CalendarDays },
  { label: "Medical Records", path: "/medical-records", roles: ["ADMIN", "DOCTOR"], icon: ClipboardList },
  { label: "Prescriptions", path: "/prescriptions", roles: ["ADMIN", "DOCTOR"], icon: FileText },
  { label: "Staff accounts", path: "/register", roles: ["ADMIN"], icon: UserPlus }
];

export function AppLayout() {
  const auth = useAuth();
  const location = useLocation();
  const visibleItems = navigationItems.filter((item) => auth.hasAnyRole(item.roles));

  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/dashboard" className="brand-link" aria-label="Clinora dashboard">
          <span className="brand-mark" aria-hidden="true">C</span>
          <span className="brand-copy">
            <span className="brand-text">Clinora</span>
            <span className="brand-descriptor">Modern Clinic Management Platform</span>
          </span>
        </Link>
        <div className="header-actions">
          <div className="user-summary" aria-label="Signed-in user">
            <span className="user-pill">{auth.user?.username}</span>
            <span className="user-role">Role: {auth.user?.roles.join(", ")}</span>
          </div>
          <button type="button" className="secondary-button" onClick={() => void auth.logout()}>
            Logout
          </button>
        </div>
      </header>
      {demoConfiguration.enabled && <DemoEnvironmentBanner />}

      <div className="app-body">
        <aside className="sidebar" aria-label="Application navigation">
          <p className="sidebar-label">Workspace</p>
          <nav>
            <NavLink to="/dashboard" className={() => isExactNavigationActive("/dashboard", location.pathname, location.hash) ? "active" : undefined}>
              <LayoutDashboard className="nav-icon" aria-hidden="true" />
              <span>Dashboard</span>
            </NavLink>
            {visibleItems.map((item) => (
              <NavLink
                className={() => isExactNavigationActive(item.path, location.pathname, location.hash) ? "active" : undefined}
                key={item.label}
                to={item.path}
              >
                <item.icon className="nav-icon" aria-hidden="true" />
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>
        </aside>
        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function isExactNavigationActive(path: string, pathname: string, hash: string): boolean {
  const [itemPathname, itemHash = ""] = path.split("#");
  return pathname === itemPathname && hash === (itemHash ? `#${itemHash}` : "");
}
