package com.sanproject.aso_service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// REST surface for the booking lifecycle: create, claim, schedule, complete, reject, cancel.
@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings")
    public List<ServiceBooking> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/bookings/available")
    public List<ServiceBooking> getAvailableBookings() {
        return bookingService.getAvailableBookings();
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<ServiceBooking> getBookingById(@PathVariable Long id) {
        ServiceBooking booking = bookingService.getBookingById(id);

        if (booking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(booking);
    }

    @PostMapping("/bookings")
    public ResponseEntity<ServiceBooking> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        ServiceBooking createdBooking = bookingService.createBooking(request);

        if (createdBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdBooking);
    }


    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> deleteBooking(@PathVariable Long id){
        boolean deleted = bookingService.deleteBooking(id);

        if (!deleted){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/bookings/{id}")
    public ResponseEntity<ServiceBooking> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody ServiceBooking booking) {

        ServiceBooking updatedBooking = bookingService.updateBooking(id, booking);

        if (updatedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(updatedBooking);
    }

    @GetMapping("/vehicles/{vehicleId}/bookings")
    public List<ServiceBooking> getBookingsByVehicleId(@PathVariable Long vehicleId) {
        return bookingService.getBookingsByVehicleId(vehicleId);
    }

    @GetMapping("/customers/{customerId}/bookings")
    public List<ServiceBooking> getBookingsByCustomerId(@PathVariable Long customerId) {
        return bookingService.getBookingsByCustomerId(customerId);
    }

    @PatchMapping("/bookings/{id}/status")
    public ResponseEntity<ServiceBooking> updateBookingStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingStatusRequest request) {

        ServiceBooking updatedBooking = bookingService.updateStatus(id, request);

        if (updatedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(updatedBooking);
    }

    @PostMapping("/bookings/{id}/claim")
    public ResponseEntity<ServiceBooking> claimBooking(
            @PathVariable Long id,
            @Valid @RequestBody ClaimBookingRequest request) {

        ServiceBooking claimedBooking = bookingService.claimBooking(id, request);

        if (claimedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(claimedBooking);
    }

    @PostMapping("/bookings/{id}/schedule")
    public ResponseEntity<ServiceBooking> scheduleBooking(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleBookingRequest request) {

        ServiceBooking scheduledBooking = bookingService.scheduleBooking(id, request);

        if (scheduledBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(scheduledBooking);
    }

    @PostMapping("/bookings/{id}/complete")
    public ResponseEntity<ServiceBooking> completeBooking(
            @PathVariable Long id,
            @Valid @RequestBody CompleteBookingRequest request) {

        ServiceBooking completedBooking = bookingService.completeBooking(id, request);

        if (completedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(completedBooking);
    }

    @PostMapping("/bookings/{id}/reject")
    public ResponseEntity<ServiceBooking> rejectBooking(
            @PathVariable Long id,
            @Valid @RequestBody RejectBookingRequest request) {

        ServiceBooking rejectedBooking = bookingService.rejectBooking(id, request);

        if (rejectedBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(rejectedBooking);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<ServiceBooking> cancelBooking(
            @PathVariable Long id,
            @RequestBody CancelBookingRequest request) {

        ServiceBooking cancelledBooking = bookingService.cancelBooking(id, request);

        if (cancelledBooking == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(cancelledBooking);
    }

}
