import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <main className="center-page">
      <section className="message-panel" aria-labelledby="not-found-heading">
        <p className="eyebrow">404</p>
        <h1 id="not-found-heading">Page not found</h1>
        <p>The page you requested is not available in Clinora.</p>
        <Link className="primary-link" to="/dashboard">Go to dashboard</Link>
      </section>
    </main>
  );
}
