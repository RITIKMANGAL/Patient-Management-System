import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfirmDialog } from "./ConfirmDialog";

describe("ConfirmDialog", () => {
  it("focuses cancel by default and closes on Escape", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();

    render(
      <ConfirmDialog
        title="Delete patient"
        message="Delete this patient?"
        confirmLabel="Delete patient"
        isConfirming={false}
        onCancel={onCancel}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByRole("button", { name: /^cancel$/i })).toHaveFocus();
    await user.keyboard("{Escape}");

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("uses action-specific progress text while confirming", () => {
    render(
      <ConfirmDialog
        title="Cancel appointment"
        message="Cancel this appointment?"
        confirmLabel="Cancel appointment"
        confirmingLabel="Cancelling..."
        isConfirming
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByRole("button", { name: /cancelling/i })).toBeDisabled();
  });

  it("keeps keyboard focus within the dialog", async () => {
    const user = userEvent.setup();

    render(
      <ConfirmDialog
        title="Delete patient"
        message="Delete this patient?"
        confirmLabel="Delete patient"
        isConfirming={false}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />
    );

    await user.tab({ shift: true });

    expect(screen.getByRole("button", { name: /delete patient/i })).toHaveFocus();

    await user.tab();

    expect(screen.getByRole("button", { name: /^cancel$/i })).toHaveFocus();
  });
});
