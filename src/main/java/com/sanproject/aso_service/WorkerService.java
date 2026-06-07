package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Workshop staff CRUD; mutating calls require an authorized requester (admin, CEO, or COO).
@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final AdminRepository adminRepository;
    private final ServiceBookingRepository bookingRepository;
    private final PasswordService passwordService;
    private final EmployeeAuthorizationService authorizationService;

    public WorkerService(
            WorkerRepository workerRepository,
            AdminRepository adminRepository,
            ServiceBookingRepository bookingRepository,
            PasswordService passwordService,
            EmployeeAuthorizationService authorizationService) {
        this.workerRepository = workerRepository;
        this.adminRepository = adminRepository;
        this.bookingRepository = bookingRepository;
        this.passwordService = passwordService;
        this.authorizationService = authorizationService;
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }

    public Worker getWorkerById(Long id) {
        return workerRepository.findById(id).orElse(null);
    }

    public Worker createWorker(CreateWorkerRequest request) {
        authorizationService.requireCanManageWorkers(request.getRequesterId());
        validateAssignableRole(request.getRole());

        String login = request.getLogin().trim();

        // Logins must be unique across both worker and admin tables.
        if (workerRepository.findByLoginIgnoreCase(login).isPresent()
                || adminRepository.findByLoginIgnoreCase(login).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login already in use");
        }

        Worker worker = new Worker();
        worker.setFirstName(request.getFirstName().trim());
        worker.setLastName(request.getLastName().trim());
        worker.setEmail(request.getEmail().trim());
        worker.setLogin(login);
        worker.setPasswordHash(passwordService.hash(request.getPassword()));
        worker.setRole(request.getRole());

        return workerRepository.save(worker);
    }

    public Worker updateWorkerRole(Long id, UpdateWorkerRoleRequest request) {
        authorizationService.requireCanManageWorkers(request.getRequesterId());
        validateAssignableRole(request.getRole());

        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found"));

        worker.setRole(request.getRole());
        return workerRepository.save(worker);
    }

    public void deleteWorker(Long id, Long requesterId) {
        authorizationService.requireCanManageWorkers(requesterId);

        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found"));

        // Block delete while bookings still reference this worker.
        if (!bookingRepository.findByAssignedWorkerId(id).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot delete worker with assigned bookings");
        }

        workerRepository.delete(worker);
    }

    private void validateAssignableRole(EmployeeRole role) {
        if (role == null || !role.isAssignableToWorker()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role for worker account");
        }
    }
}
