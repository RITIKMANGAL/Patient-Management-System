import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import * as prescriptionApi from "../api/prescriptionApi";

type AccessState =
  | { status: "loading"; url: null; error: null }
  | { status: "loaded"; url: string; error: null }
  | { status: "error"; url: null; error: string };

export function PrescriptionAccessPage() {
  const { token } = useParams();
  const [state, setState] = useState<AccessState>({ status: "loading", url: null, error: null });

  useEffect(() => {
    if (!token) {
      setState({ status: "error", url: null, error: "Prescription access link is invalid or expired" });
      return undefined;
    }

    let isCurrent = true;
    let objectUrl: string | null = null;

    setState({ status: "loading", url: null, error: null });
    prescriptionApi.downloadPrescriptionAccessPdf(token)
      .then((pdf) => {
        if (!isCurrent) {
          return;
        }
        objectUrl = URL.createObjectURL(pdf);
        setState({ status: "loaded", url: objectUrl, error: null });
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setState({ status: "error", url: null, error: toAccessMessage(error) });
        }
      });

    return () => {
      isCurrent = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [token]);

  return (
    <main className="center-page">
      <section className="message-panel patient-access-panel" aria-labelledby="prescription-access-heading">
        <p className="eyebrow">Clinora secure access</p>
        <h1 id="prescription-access-heading">Prescription PDF</h1>

        {state.status === "loading" && (
          <div className="resource-state loading-state" aria-live="polite">
            <span className="loading-dot" aria-hidden="true" />
            Loading prescription...
          </div>
        )}

        {state.status === "error" && (
          <p className="form-error" role="alert">
            {state.error}
          </p>
        )}

        {state.status === "loaded" && (
          <>
            <p className="page-description">Your prescription is ready to view or download securely.</p>
            <div className="form-actions">
              <a className="primary-link" href={state.url} target="_blank" rel="noreferrer">
                Open prescription PDF
              </a>
              <a className="secondary-button" href={state.url} download="prescription.pdf">
                Download PDF
              </a>
            </div>
            <iframe className="patient-access-frame" src={state.url} title="Prescription PDF preview" />
          </>
        )}
      </section>
    </main>
  );
}

function toAccessMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return "Prescription access link is invalid or expired";
  }
  return "Prescription access link is invalid or expired";
}
