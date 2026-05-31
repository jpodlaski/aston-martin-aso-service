package com.sanproject.aso_service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

}
