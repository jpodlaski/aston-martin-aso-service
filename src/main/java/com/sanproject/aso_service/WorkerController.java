package com.sanproject.aso_service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only worker listing; assigned bookings power the employee dashboard.
@RestController
@RequestMapping("/workers")
public class WorkerController {

    private final WorkerService workerService;
    private final BookingService bookingService;

    public WorkerController(WorkerService workerService, BookingService bookingService) {
        this.workerService = workerService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<Worker> getAllWorkers() {
        return workerService.getAllWorkers();
    }

    @GetMapping("/{id}/bookings")
    public List<ServiceBooking> getWorkerBookings(@PathVariable Long id) {
        return bookingService.getBookingsByWorkerId(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorkerById(@PathVariable Long id) {
        Worker worker = workerService.getWorkerById(id);

        if (worker == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(worker);
    }
}
