package com.sanproject.aso_service.email;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.EmailOutboxChannel;
import com.sanproject.aso_service.domain.EmailOutboxStatus;
import com.sanproject.aso_service.repository.EmailOutboxRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends PENDING outbox rows and retries after transient failures / process crashes.
 * Immediate path: EmailOutboxDispatcher afterCommit → process(id).
 * Crash recovery: @Scheduled processDue() picks up leftover PENDING rows.
 */
@Service
public class EmailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxProcessor.class);

    private final EmailOutboxRepository outboxRepository;
    private final BookingNotificationDelivery bookingDelivery;
    private final CustomerEmailDelivery customerDelivery;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public EmailOutboxProcessor(
            EmailOutboxRepository outboxRepository,
            BookingNotificationDelivery bookingDelivery,
            CustomerEmailDelivery customerDelivery,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.email-outbox.batch-size:20}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.bookingDelivery = bookingDelivery;
        this.customerDelivery = customerDelivery;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.email-outbox.poll-interval-ms:5000}")
    public void processDue() {
        List<EmailOutbox> due = outboxRepository.findDue(LocalDateTime.now(), PageRequest.of(0, batchSize));
        for (EmailOutbox row : due) {
            process(row.getId());
        }
    }

    public void process(Long outboxId) {
        // Explicit TX so both the async runner and processDue get a session for FOR UPDATE.
        transactionTemplate.executeWithoutResult(status -> processInTransaction(outboxId));
    }

    private void processInTransaction(Long outboxId) {
        EmailOutbox row = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getStatus() != EmailOutboxStatus.PENDING) {
            return;
        }
        if (row.getNextAttemptAt() != null && row.getNextAttemptAt().isAfter(LocalDateTime.now())) {
            return;
        }

        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setUpdatedAt(LocalDateTime.now());

        try {
            deliver(row);
            row.setStatus(EmailOutboxStatus.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setLastError(null);
            outboxRepository.save(row);
            log.info("Outbox {} sent ({} / {})", row.getId(), row.getEvent(), row.getChannel());
        } catch (Exception ex) {
            handleFailure(row, ex);
        }
    }

    private void deliver(EmailOutbox row) {
        if (row.getChannel() == EmailOutboxChannel.BOOKING) {
            if (row.getBookingId() == null) {
                throw new EmailDeliveryException("Booking outbox row missing bookingId");
            }
            bookingDelivery.deliver(row.getBookingId(), row.getEvent(), row.getPreviousStatus());
            return;
        }

        if (row.getPayloadJson() == null || row.getPayloadJson().isBlank()) {
            throw new EmailDeliveryException("Customer outbox row missing payload_json");
        }
        customerDelivery.deliver(readCustomerPayload(row.getPayloadJson()));
    }

    private void handleFailure(EmailOutbox row, Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        row.setLastError(message);
        row.setUpdatedAt(LocalDateTime.now());

        if (row.getAttemptCount() >= row.getMaxAttempts()) {
            row.setStatus(EmailOutboxStatus.FAILED);
            log.error("Outbox {} permanently failed after {} attempts: {}",
                    row.getId(), row.getAttemptCount(), message);
        } else {
            // Exponential-ish backoff: 10s, 20s, 40s, …
            long delaySeconds = 10L * (1L << Math.min(row.getAttemptCount() - 1, 4));
            row.setStatus(EmailOutboxStatus.PENDING);
            row.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("Outbox {} attempt {} failed; retry at {}: {}",
                    row.getId(), row.getAttemptCount(), row.getNextAttemptAt(), message);
        }
        outboxRepository.save(row);
    }

    private CustomerEmailPayload readCustomerPayload(String json) {
        try {
            return objectMapper.readValue(json, CustomerEmailPayload.class);
        } catch (JsonProcessingException e) {
            throw new EmailDeliveryException("Invalid customer email payload JSON", e);
        }
    }
}
