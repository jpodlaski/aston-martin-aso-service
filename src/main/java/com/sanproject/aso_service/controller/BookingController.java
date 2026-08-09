package com.sanproject.aso_service.controller;

import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Worker;
import com.sanproject.aso_service.dto.CancelBookingRequest;
import com.sanproject.aso_service.dto.ClaimBookingRequest;
import com.sanproject.aso_service.dto.CompleteBookingRequest;
import com.sanproject.aso_service.dto.CreateBookingRequest;
import com.sanproject.aso_service.dto.RejectBookingRequest;
import com.sanproject.aso_service.dto.ScheduleBookingRequest;
import com.sanproject.aso_service.dto.UpdateWorkPlanRequest;
import com.sanproject.aso_service.security.AuthSupport;
import com.sanproject.aso_service.security.AuthUser;
import com.sanproject.aso_service.service.BookingService;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        Worker worker = authSupport.requireWorker();
        return bookingService.getAvailableBookings(worker);
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

    /** Same invoice PDF as the completion email attachment. */
    @GetMapping("/bookings/{id}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id) {
        AuthUser client = authSupport.requireClient();
        byte[] pdf = bookingService.getInvoicePdfForCustomer(id, client.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-booking-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
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

    @PostMapping("/bookings/{id}/accept")
    public ResponseEntity<ServiceBooking> acceptBooking(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleBookingRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking accepted = bookingService.acceptBooking(id, request, worker);

        if (accepted == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(accepted);
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

    @PostMapping("/bookings/{id}/work-plan")
    public ResponseEntity<ServiceBooking> updateWorkPlan(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkPlanRequest request) {
        Worker worker = authSupport.requireWorker();
        ServiceBooking updated = bookingService.updateWorkPlan(id, request, worker);

        if (updated == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(updated);
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
