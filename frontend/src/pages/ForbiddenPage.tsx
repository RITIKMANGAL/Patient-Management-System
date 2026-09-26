import { Link } from "react-router-dom";

export function ForbiddenPage() {
  return (
    <main className="center-page">
      <section className="message-panel" aria-labelledby="forbidden-heading">
        <p className="eyebrow">403</p>
        <h1 id="forbidden-heading">Access denied</h1>
        <p>Your account is signed in, but this area is not available for your role.</p>
        <Link className="primary-link" to="/dashboard">Return to dashboard</Link>
      </section>
    </main>
  );
}
