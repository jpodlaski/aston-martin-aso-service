package com.sanproject.aso_service.service;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.email.EmailOutboxDispatcher;
import com.sanproject.aso_service.email.EmailOutboxService;

import org.springframework.stereotype.Service;

/**
 * Facade used by BookingService after each state change.
 * Enqueues a PENDING outbox row (same DB transaction as the booking update), then
 * schedules async delivery after commit. Crash-safe: PENDING rows are retried by the poller.
 */
@Service
public class BookingNotificationService {

    private final EmailOutboxService outboxService;
    private final EmailOutboxDispatcher dispatcher;

    public BookingNotificationService(EmailOutboxService outboxService, EmailOutboxDispatcher dispatcher) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
    }

    public void notifyCreated(ServiceBooking booking) {
        enqueue(booking.getId(), "created", null);
    }

    public void notifyCompleted(ServiceBooking booking, BookingStatus previousStatus) {
        enqueue(booking.getId(), "booking_completed", previousStatus);
    }

    public void notifyTechnicianAssigned(ServiceBooking booking) {
        enqueue(booking.getId(), "technician_assigned", null);
    }

    public void notifyAppointmentScheduled(ServiceBooking booking) {
        enqueue(booking.getId(), "appointment_scheduled", null);
    }

    public void notifyWorkPlanUpdated(ServiceBooking booking) {
        enqueue(booking.getId(), "work_plan_updated", null);
    }

    public void notifyRejected(ServiceBooking booking) {
        enqueue(booking.getId(), "booking_rejected", null);
    }

    public void notifyCancelled(ServiceBooking booking) {
        enqueue(booking.getId(), "booking_cancelled", null);
    }

    private void enqueue(Long bookingId, String event, BookingStatus previousStatus) {
        EmailOutbox row = outboxService.enqueueBooking(bookingId, event, previousStatus);
        dispatcher.dispatchAfterCommit(row.getId());
    }
}
