package com.sanproject.aso_service.email;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.EmailOutboxChannel;
import com.sanproject.aso_service.domain.EmailOutboxStatus;
import com.sanproject.aso_service.repository.EmailOutboxRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes PENDING outbox rows. Must run inside the same @Transactional boundary as the
 * domain change (booking claim, registration, …) so crash-after-commit still leaves the row.
 */
@Service
public class EmailOutboxService {

    private final EmailOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmailOutboxService(EmailOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmailOutbox enqueueBooking(Long bookingId, String event, BookingStatus previousStatus) {
        EmailOutbox row = baseRow(EmailOutboxChannel.BOOKING, event);
        row.setBookingId(bookingId);
        row.setPreviousStatus(previousStatus);
        return outboxRepository.save(row);
    }

    @Transactional
    public EmailOutbox enqueueCustomer(String event, Long customerId, CustomerEmailPayload payload) {
        EmailOutbox row = baseRow(EmailOutboxChannel.CUSTOMER, event);
        row.setCustomerId(customerId);
        row.setPayloadJson(toJson(payload));
        return outboxRepository.save(row);
    }

    private EmailOutbox baseRow(EmailOutboxChannel channel, String event) {
        EmailOutbox row = new EmailOutbox();
        row.setChannel(channel);
        row.setEvent(event);
        row.setStatus(EmailOutboxStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setNextAttemptAt(now);
        return row;
    }

    private String toJson(CustomerEmailPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize customer email payload", e);
        }
    }
}
