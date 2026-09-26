import { FormEvent, useEffect, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { ApiError } from "../api/apiClient";
import { listDoctors } from "../api/doctorApi";
import { useAuth } from "../auth/AuthContext";
import type { RoleName } from "../types/auth";
import type { Doctor } from "../types/doctor";
import type { PageResponse } from "../types/api";

export function RegisterPage() {
  const auth = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [role, setRole] = useState<RoleName>("RECEPTIONIST");
  const [doctorId, setDoctorId] = useState("");
  const [doctorPage, setDoctorPage] = useState(0);
  const [doctors, setDoctors] = useState<PageResponse<Doctor> | null>(null);
  const [doctorError, setDoctorError] = useState<string | null>(null);

  useEffect(() => {
    if (role !== "DOCTOR") return;
    let active = true;
    setDoctors(null);
    setDoctorError(null);
    void listDoctors(doctorPage).then((page) => {
      if (active) setDoctors(page);
    }).catch((error: unknown) => {
      if (active) setDoctorError(error instanceof ApiError ? error.message : "Unable to load doctors");
    });
    return () => { active = false; };
  }, [role, doctorPage]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validateRegistration(username, password, firstName, lastName);
    if (validationError) {
      setFormError(validationError);
      return;
    }
    if (role === "DOCTOR" && !doctorId) {
      setFormError("Select a doctor record");
      return;
    }

    setIsSubmitting(true);
    setFormError(null);
    setSuccessMessage(null);
    try {
      const user = await auth.register({
        username: username.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        role,
        doctorId: role === "DOCTOR" ? doctorId : undefined
      });
      setSuccessMessage(`${user.username} created as ${user.roles.join(", ")}`);
      setPassword("");
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "Registration failed");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-panel" aria-labelledby="register-heading">
        <div className="auth-brand" aria-label="Clinora, Modern Clinic Management Platform">
          <span className="brand-mark" aria-hidden="true">C</span>
          <div>
            <span className="brand-text">Clinora</span>
            <span className="brand-descriptor">Modern Clinic Management Platform</span>
          </div>
        </div>
        <h1 id="register-heading">Create staff account</h1>
        <form onSubmit={handleSubmit} noValidate>
          <div className="field-grid">
            <div className="field-group">
              <label htmlFor="firstName">First name</label>
              <input
                id="firstName"
                name="firstName"
                autoComplete="given-name"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
              />
            </div>
            <div className="field-group">
              <label htmlFor="lastName">Last name</label>
              <input
                id="lastName"
                name="lastName"
                autoComplete="family-name"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </div>
          </div>
          <div className="field-group">
            <label htmlFor="registerUsername">Email</label>
            <input
              id="registerUsername"
              name="username"
              type="email"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="registerPassword">Password</label>
            <input
              id="registerPassword"
              name="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          <div className="field-group">
            <label htmlFor="staffRole">Role</label>
            <select id="staffRole" value={role} onChange={(event) => {
              setRole(event.target.value as RoleName);
              setDoctorId("");
              setDoctorPage(0);
            }}>
              <option value="RECEPTIONIST">Receptionist</option>
              <option value="DOCTOR">Doctor</option>
              <option value="ADMIN">Administrator</option>
            </select>
          </div>
          {role === "DOCTOR" ? <div className="field-group">
            <label htmlFor="staffDoctor">Doctor record</label>
            <select id="staffDoctor" value={doctorId} disabled={!doctors} onChange={(event) => setDoctorId(event.target.value)}>
              <option value="">{doctors ? "Select doctor" : "Loading doctors..."}</option>
              {doctors?.content.map((doctor) => <option key={doctor.id} value={doctor.id}>
                {doctor.firstName} {doctor.lastName} ({doctor.licenseNumber})
              </option>)}
            </select>
            {doctorError ? <p role="alert" className="form-error">{doctorError}</p> : null}
            {doctors?.content.length === 0 ? <p>No doctors found.</p> : null}
            {doctors && doctors.totalPages > 1 ? <div className="header-actions">
              <button type="button" className="secondary-button" aria-label="Previous doctors" title="Previous doctors" disabled={doctorPage === 0} onClick={() => { setDoctorId(""); setDoctorPage(doctorPage - 1); }}><ChevronLeft size={18} /></button>
              <span>Page {doctorPage + 1} of {doctors.totalPages}</span>
              <button type="button" className="secondary-button" aria-label="Next doctors" title="Next doctors" disabled={doctorPage + 1 >= doctors.totalPages} onClick={() => { setDoctorId(""); setDoctorPage(doctorPage + 1); }}><ChevronRight size={18} /></button>
            </div> : null}
          </div> : null}
          {formError ? <p className="form-error" role="alert">{formError}</p> : null}
          {successMessage ? <p className="form-success" role="status">{successMessage}</p> : null}
          <button type="submit" className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? "Creating account..." : "Create account"}
          </button>
        </form>
      </section>
    </div>
  );
}

function validateRegistration(username: string, password: string, firstName: string, lastName: string): string | null {
  if (!firstName.trim() || !lastName.trim()) {
    return "First and last name are required";
  }
  if (!username.trim()) {
    return "Email is required";
  }
  if (password.length < 8) {
    return "Password must be at least 8 characters";
  }
  if (new TextEncoder().encode(password).length > 72) {
    return "Password must contain at most 72 UTF-8 bytes";
  }
  return null;
}
