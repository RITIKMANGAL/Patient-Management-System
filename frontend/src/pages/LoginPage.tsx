import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { useAuth } from "../auth/AuthContext";
import { DemoEnvironmentBanner } from "../components/DemoEnvironmentBanner";
import { demoConfiguration } from "../config/demo";

interface LocationState {
  from?: {
    pathname?: string;
  };
}

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as LocationState | null;
  const redirectTo = locationState?.from?.pathname ?? "/dashboard";

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validateLogin(username, password);
    if (validationError) {
      setFormError(validationError);
      return;
    }

    setIsSubmitting(true);
    setFormError(null);
    try {
      await auth.login({ username: username.trim(), password });
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "Login failed");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel" aria-labelledby="login-heading">
        <div className="auth-brand" aria-label="Clinora, Modern Clinic Management Platform">
          <span className="brand-mark" aria-hidden="true">C</span>
          <div>
            <span className="brand-text">Clinora</span>
            <span className="brand-descriptor">Modern Clinic Management Platform</span>
          </div>
        </div>
        <h1 id="login-heading">Welcome back</h1>
        <p className="auth-description">Sign in to your secure clinic workspace.</p>
        {demoConfiguration.enabled && (
          <div className="demo-login-notice">
            <DemoEnvironmentBanner compact />
            <p>Demo account</p>
            <dl>
              <div><dt>Username</dt><dd>{demoConfiguration.username}</dd></div>
              <div><dt>Password</dt><dd>{demoConfiguration.password || "Configured by the demo host"}</dd></div>
            </dl>
          </div>
        )}
        <form onSubmit={handleSubmit} noValidate>
          <div className="field-group">
            <label htmlFor="username">Email</label>
            <input
              id="username"
              name="username"
              type="email"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {formError ? <p className="form-error" role="alert">{formError}</p> : null}
          <button type="submit" className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
        <p className="auth-switch">
          Contact your clinic administrator for account access.
        </p>
      </section>
    </main>
  );
}

function validateLogin(username: string, password: string): string | null {
  if (!username.trim()) {
    return "Email is required";
  }
  if (!password) {
    return "Password is required";
  }
  return null;
}
