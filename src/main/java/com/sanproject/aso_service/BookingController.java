package com.sanproject.aso_service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for the booking lifecycle.
 * Pattern used everywhere: AuthSupport resolves the actor from the JWT → BookingService applies rules.
 * Prefer "/me" style routes (e.g. /customers/me/bookings) so the client never picks another user's id.
 */
@RestController
public class BookingController {

    private final BookingService bookingService;
    private final AuthSupport authSupport;

    public BookingController(BookingService bookingService, AuthSupport authSupport) {
        this.bookingService = bookingService;
        this.authSupport = authSupport;
    }

    @GetMapping("/bookings")
    public List<ServiceBooking> getAllBookings() {
        authSupport.requireCanManageWorkers();
        return bookingService.getAllBookings();
    }

    @GetMapping("/bookings/available")
    public List<ServiceBooking> getAvailableBookings() {
        authSupport.requireWorker();
        return bookingService.getAvailableBookings();
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<ServiceBooking> getBookingById(@PathVariable Long id) {
        AuthUser user = authSupport.requireUser();
        ServiceBooking booking = bookingService.getBookingById(id);

        if (booking == null) {
            return ResponseEntity.notFound().build();
        }

        bookingService.assertCanView(user, booking);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/bookings")
    public ResponseEntity<ServiceBooking> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        AuthUser client = authSupport.requireClient();
        ServiceBooking createdBooking = bookingService.createBooking(request, client.getId());

        if (createdBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdBooking);
    }

    @GetMapping("/vehicles/{vehicleId}/bookings")
    public List<ServiceBooking> getBookingsByVehicleId(@PathVariable Long vehicleId) {
        authSupport.requireCanManageWorkers();
        return bookingService.getBookingsByVehicleId(vehicleId);
    }

    @GetMapping("/customers/me/bookings")
    public List<ServiceBooking> getMyBookings() {
        AuthUser client = authSupport.requireClient();
        return bookingService.getBookingsByCustomerId(client.getId());
    }

    @GetMapping("/customers/{customerId}/bookings")
    public List<ServiceBooking> getBookingsByCustomerId(@PathVariable Long customerId) {
        AuthUser user = authSupport.requireUser();
        if ("CLIENT".equals(user.getRole())) {
            if (!user.getId().equals(customerId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Cannot view another customer's bookings");
            }
            return bookingService.getBookingsByCustomerId(customerId);
        }
        authSupport.requireCanManageWorkers();
        return bookingService.getBookingsByCustomerId(customerId);
    }

    @PostMapping("/bookings/{id}/claim")
    public ResponseEntity<ServiceBooking> claimBooking(
            @PathVariable Long id,
            @Valid @RequestBody ClaimBookingRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking claimedBooking = bookingService.claimBooking(id, request, worker);

        if (claimedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(claimedBooking);
    }

    @PostMapping("/bookings/{id}/schedule")
    public ResponseEntity<ServiceBooking> scheduleBooking(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleBookingRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking scheduledBooking = bookingService.scheduleBooking(id, request, worker);

        if (scheduledBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(scheduledBooking);
    }

    @PostMapping("/bookings/{id}/complete")
    public ResponseEntity<ServiceBooking> completeBooking(
            @PathVariable Long id,
            @Valid @RequestBody CompleteBookingRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking completedBooking = bookingService.completeBooking(id, request, worker);

        if (completedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(completedBooking);
    }

    @PostMapping("/bookings/{id}/reject")
    public ResponseEntity<ServiceBooking> rejectBooking(
            @PathVariable Long id,
            @Valid @RequestBody RejectBookingRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking rejectedBooking = bookingService.rejectBooking(id, request, worker);

        if (rejectedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(rejectedBooking);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<ServiceBooking> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request) {
        AuthUser actor = authSupport.requireUser();
        CancelBookingRequest body = request != null ? request : new CancelBookingRequest();
        ServiceBooking cancelledBooking = bookingService.cancelBooking(id, body, actor);

        if (cancelledBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(cancelledBooking);
    }
}
