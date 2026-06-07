package com.sanproject.aso_service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Management API: workers CRUD and booking history; requesterId authorizes each call.
@RestController
@RequestMapping("/admins")
public class AdminController {

    private final AdminService adminService;
    private final WorkerService workerService;
    private final BookingService bookingService;
    private final EmployeeAuthorizationService authorizationService;

    public AdminController(
            AdminService adminService,
            WorkerService workerService,
            BookingService bookingService,
            EmployeeAuthorizationService authorizationService) {
        this.adminService = adminService;
        this.workerService = workerService;
        this.bookingService = bookingService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<Admin> getAllAdmins() {
        return adminService.getAllAdmins();
    }

    @GetMapping("/workers")
    public List<Worker> getWorkers() {
        return workerService.getAllWorkers();
    }

    // Full booking history with customer email, worker, and cost details.
    @GetMapping("/bookings")
    public List<AdminBookingResponse> getBookings(@RequestParam Long requesterId) {
        authorizationService.requireCanManageWorkers(requesterId);
        return bookingService.getAdminBookings();
    }

    @PostMapping("/workers")
    public ResponseEntity<Worker> createWorker(@Valid @RequestBody CreateWorkerRequest request) {
        Worker createdWorker = workerService.createWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWorker);
    }

    @PatchMapping("/workers/{id}/role")
    public ResponseEntity<Worker> updateWorkerRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkerRoleRequest request) {
        Worker updatedWorker = workerService.updateWorkerRole(id, request);
        return ResponseEntity.ok(updatedWorker);
    }

    @DeleteMapping("/workers/{id}")
    public ResponseEntity<Void> deleteWorker(
            @PathVariable Long id,
            @RequestParam Long requesterId) {
        workerService.deleteWorker(id, requesterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Admin> getAdminById(@PathVariable Long id) {
        Admin admin = adminService.getAdminById(id);

        if (admin == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(admin);
    }

    @PostMapping
    public ResponseEntity<Admin> createAdmin(@Valid @RequestBody Admin admin) {
        Admin createdAdmin = adminService.createAdmin(admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAdmin);
    }
}
