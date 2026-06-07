package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

// Entry point from BookingService; passes booking id so async code reloads a fresh entity.
@Service
public class BookingNotificationService {

    private final BookingNotificationExecutor executor;

    public BookingNotificationService(BookingNotificationExecutor executor) {
        this.executor = executor;
    }

    public void notifyCreated(ServiceBooking booking) {
        executor.send(booking.getId(), "created", null);
    }

    public void notifyStatusChanged(ServiceBooking booking, BookingStatus previousStatus) {
        executor.send(booking.getId(), "status_changed", previousStatus);
    }

    public void notifyCompleted(ServiceBooking booking, BookingStatus previousStatus) {
        executor.send(booking.getId(), "booking_completed", previousStatus);
    }

    public void notifyTechnicianAssigned(ServiceBooking booking) {
        executor.send(booking.getId(), "technician_assigned", null);
    }

    public void notifyAppointmentScheduled(ServiceBooking booking) {
        executor.send(booking.getId(), "appointment_scheduled", null);
    }

    public void notifyRejected(ServiceBooking booking) {
        executor.send(booking.getId(), "booking_rejected", null);
    }

    public void notifyCancelled(ServiceBooking booking) {
        executor.send(booking.getId(), "booking_cancelled", null);
    }
}
