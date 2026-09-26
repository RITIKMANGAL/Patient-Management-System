import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import * as feedbackApi from "../api/feedbackApi";
import type { FormEvent } from "react";
import type { FeedbackContextResponse } from "../types/feedback";
import { formatDateTime } from "../utils/formatters";

type FeedbackState =
  | { status: "loading"; context: null; error: null; success: null }
  | { status: "loaded"; context: FeedbackContextResponse; error: null; success: null }
  | { status: "submitted"; context: FeedbackContextResponse; error: null; success: string }
  | { status: "error"; context: null; error: string; success: null };

export function FeedbackPage() {
  const { token } = useParams();
  const [state, setState] = useState<FeedbackState>({ status: "loading", context: null, error: null, success: null });
  const [rating, setRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!token) {
      setState({ status: "error", context: null, error: "Feedback link is invalid or expired", success: null });
      return;
    }

    setState({ status: "loading", context: null, error: null, success: null });
    feedbackApi.getFeedbackContext(token)
      .then((context) => {
        if (context.submitted) {
          setState({
            status: "submitted",
            context,
            error: null,
            success: "Feedback has already been submitted."
          });
          return;
        }
        setState({ status: "loaded", context, error: null, success: null });
      })
      .catch((error: unknown) => {
        setState({ status: "error", context: null, error: feedbackAccessMessage(error), success: null });
      });
  }, [token]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!token || state.status !== "loaded") {
      return;
    }
    if (rating === null) {
      setFormError("Rating is required");
      return;
    }
    if (comment.length > 1000) {
      setFormError("Comment must be at most 1000 characters");
      return;
    }

    setIsSubmitting(true);
    try {
      await feedbackApi.submitFeedback(token, {
        rating,
        comment: comment.trim() === "" ? null : comment.trim()
      });
      setState({
        status: "submitted",
        context: state.context,
        error: null,
        success: "Thank you. Your feedback has been submitted."
      });
      setComment("");
    } catch (error: unknown) {
      if (error instanceof ApiError && error.status === 409) {
        setState({
          status: "submitted",
          context: state.context,
          error: null,
          success: "Feedback has already been submitted."
        });
      } else {
        setFormError(feedbackAccessMessage(error));
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="center-page">
      <section className="message-panel patient-access-panel" aria-labelledby="feedback-heading">
        <p className="eyebrow">Clinora feedback</p>
        <h1 id="feedback-heading">Rate Your Visit</h1>

        {state.status === "loading" && (
          <div className="resource-state loading-state" aria-live="polite">
            <span className="loading-dot" aria-hidden="true" />
            Loading feedback form...
          </div>
        )}

        {state.status === "error" && (
          <p className="form-error" role="alert">
            {state.error}
          </p>
        )}

        {(state.status === "loaded" || state.status === "submitted") && (
          <>
            <dl className="detail-grid">
              <div>
                <dt>Doctor</dt>
                <dd>{state.context.doctorName}</dd>
              </div>
              <div>
                <dt>Visit date</dt>
                <dd>{formatDateTime(state.context.appointmentDateTime)}</dd>
              </div>
            </dl>

            {state.status === "submitted" ? (
              <div className="resource-state empty-state" role="status">
                <strong>{state.success}</strong>
                <span>Your response has been recorded for this visit.</span>
              </div>
            ) : (
              <form onSubmit={(event) => void submit(event)}>
                <fieldset className="rating-fieldset">
                  <legend>Rating</legend>
                  <div className="rating-options">
                    {[1, 2, 3, 4, 5].map((value) => (
                      <label key={value} className={rating === value ? "rating-option selected" : "rating-option"}>
                        <input
                          type="radio"
                          name="rating"
                          value={value}
                          checked={rating === value}
                          onChange={() => setRating(value)}
                        />
                        <span>{value}</span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                <div className="field-group">
                  <label htmlFor="feedback-comment">Comment</label>
                  <textarea
                    id="feedback-comment"
                    value={comment}
                    maxLength={1000}
                    rows={5}
                    onChange={(event) => setComment(event.target.value)}
                  />
                </div>

                {formError && (
                  <p className="form-error" role="alert">
                    {formError}
                  </p>
                )}

                <div className="form-actions">
                  <button type="submit" className="primary-button" disabled={isSubmitting}>
                    {isSubmitting ? "Submitting..." : "Submit feedback"}
                  </button>
                </div>
              </form>
            )}
          </>
        )}
      </section>
    </main>
  );
}

function feedbackAccessMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) {
    return "Feedback has already been submitted.";
  }
  return "Feedback link is invalid or expired";
}
