package com.patientmanagement.common;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class AfterCommitAction {
    private AfterCommitAction() {
    }

    // The action must invoke a separate REQUIRES_NEW service transaction.
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execute(action);
                }
            });
        } else {
            execute(action);
        }
    }

    private static void execute(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(AfterCommitAction.class)
                    .warn("Post-commit communication failed ({})", exception.getClass().getSimpleName());
        }
    }
}
