package com.sanproject.aso_service.controller;

import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Worker;
import com.sanproject.aso_service.dto.WorkshopCapacityResponse;
import com.sanproject.aso_service.security.AuthSupport;
import com.sanproject.aso_service.security.AuthUser;
import com.sanproject.aso_service.service.BookingService;
import com.sanproject.aso_service.service.WorkerService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

// Worker reads; assigned bookings for the authenticated workshop staff member.
@RestController
@RequestMapping("/workers")
public class WorkerController {

    private final WorkerService workerService;
    private final BookingService bookingService;
    private final AuthSupport authSupport;

    public WorkerController(
            WorkerService workerService,
            BookingService bookingService,
            AuthSupport authSupport) {
        this.workerService = workerService;
        this.bookingService = bookingService;
        this.authSupport = authSupport;
    }

    @GetMapping
    public List<Worker> getAllWorkers() {
        authSupport.requireCanManageWorkers();
        return workerService.getAllWorkers();
    }

    @GetMapping("/me/bookings")
    public List<ServiceBooking> getMyBookings() {
        Worker worker = authSupport.requireWorker();
        return bookingService.getBookingsByWorkerId(worker.getId());
    }

    @GetMapping("/me/awaiting-workshop")
    public List<ServiceBooking> getAwaitingWorkshopBookings() {
        Worker worker = authSupport.requireWorker();
        return bookingService.getAwaitingWorkshopBookings(worker);
    }

    @GetMapping("/me/consultant-archive")
    public List<ServiceBooking> getConsultantArchiveBookings() {
        Worker worker = authSupport.requireWorker();
        return bookingService.getConsultantArchiveBookings(worker);
    }

    @GetMapping("/me/workshop-capacity")
    public WorkshopCapacityResponse getWorkshopCapacity(
            @RequestParam("at") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        Worker worker = authSupport.requireWorker();
        return bookingService.getWorkshopCapacity(at, worker);
    }

    @GetMapping("/{id}/bookings")
    public List<ServiceBooking> getWorkerBookings(@PathVariable Long id) {
        AuthUser user = authSupport.requireUser();
        if (user.getId().equals(id)) {
            authSupport.requireWorker();
            return bookingService.getBookingsByWorkerId(id);
        }
        authSupport.requireCanManageWorkers();
        return bookingService.getBookingsByWorkerId(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorkerById(@PathVariable Long id) {
        AuthUser user = authSupport.requireUser();
        if (!user.getId().equals(id) && !authSupport.isManagement()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another worker");
        }

        Worker worker = workerService.getWorkerById(id);

        if (worker == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(worker);
    }
}
