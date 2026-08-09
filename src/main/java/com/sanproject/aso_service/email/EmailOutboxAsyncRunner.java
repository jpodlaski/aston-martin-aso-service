package com.sanproject.aso_service.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Separate bean so @Async is applied via Spring's proxy (self-invocation would skip it).
 */
@Component
public class EmailOutboxAsyncRunner {

    private final EmailOutboxProcessor processor;

    public EmailOutboxAsyncRunner(EmailOutboxProcessor processor) {
        this.processor = processor;
    }

    @Async
    public void process(Long outboxId) {
        processor.process(outboxId);
    }
}
