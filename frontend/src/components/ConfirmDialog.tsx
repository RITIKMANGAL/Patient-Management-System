import { useEffect, useId, useRef } from "react";

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  confirmingLabel?: string;
  isConfirming: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  confirmingLabel = "Working...",
  isConfirming,
  onCancel,
  onConfirm
}: ConfirmDialogProps) {
  const titleId = useId();
  const messageId = useId();
  const dialogRef = useRef<HTMLElement>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cancelButtonRef.current?.focus();
  }, []);

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        aria-labelledby={titleId}
        aria-describedby={messageId}
        aria-modal="true"
        aria-busy={isConfirming}
        className="dialog-panel"
        ref={dialogRef}
        onKeyDown={(event) => {
          if (event.key === "Escape" && !isConfirming) {
            onCancel();
            return;
          }

          if (event.key !== "Tab") {
            return;
          }

          const focusableElements = dialogRef.current?.querySelectorAll<HTMLElement>(
            "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled])"
          );
          if (!focusableElements || focusableElements.length === 0) {
            return;
          }

          const first = focusableElements[0];
          const last = focusableElements[focusableElements.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
        }}
        role="dialog"
      >
        <div>
          <p className="eyebrow">Confirm action</p>
          <h2 id={titleId}>{title}</h2>
        </div>
        <p id={messageId} className="dialog-message">
          {message}
        </p>
        <div className="dialog-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={onCancel}
            disabled={isConfirming}
            ref={cancelButtonRef}
          >
            Cancel
          </button>
          <button type="button" className="danger-button" onClick={onConfirm} disabled={isConfirming}>
            {isConfirming ? confirmingLabel : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}
