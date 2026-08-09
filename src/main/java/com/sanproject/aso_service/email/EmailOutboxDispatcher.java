package com.sanproject.aso_service.email;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Kick off delivery only after the DB transaction commits.
 * That way we never send for a change that later rolls back, and a crash mid-request
 * still leaves a PENDING outbox row for the scheduled poller to retry.
 */
@Component
public class EmailOutboxDispatcher {

    private final EmailOutboxAsyncRunner asyncRunner;

    public EmailOutboxDispatcher(EmailOutboxAsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    public void dispatchAfterCommit(Long outboxId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncRunner.process(outboxId);
                }
            });
            return;
        }
        asyncRunner.process(outboxId);
    }
}
