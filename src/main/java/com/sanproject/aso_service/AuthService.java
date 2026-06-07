package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Client and employee login; registration creates a customer and sends a welcome email.
@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final CustomerNotificationService customerNotificationService;

    public AuthService(
            CustomerRepository customerRepository,
            WorkerRepository workerRepository,
            AdminRepository adminRepository,
            PasswordService passwordService,
            CustomerNotificationService customerNotificationService) {
        this.customerRepository = customerRepository;
        this.workerRepository = workerRepository;
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.customerNotificationService = customerNotificationService;
    }

    public AuthResponse loginClient(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.getLogin().trim())
                .orElseThrow(() -> unauthorized("Invalid email or password"));

        if (!passwordService.matches(request.getPassword(), customer.getPasswordHash())) {
            throw unauthorized("Invalid email or password");
        }

        return new AuthResponse(
                "CLIENT",
                customer.getId(),
                customer.getFirstName() + " " + customer.getLastName());
    }

    public AuthResponse loginEmployee(LoginRequest request) {
        String login = request.getLogin().trim();

        // Workers are checked first; admins share the same login form as a fallback.
        return workerRepository.findByLoginIgnoreCase(login)
                .map(worker -> authenticateEmployee(
                        worker.getPasswordHash(),
                        request.getPassword(),
                        resolveRoleName(worker.getRole()),
                        worker.getId(),
                        worker.getFirstName() + " " + worker.getLastName()))
                .orElseGet(() -> adminRepository.findByLoginIgnoreCase(login)
                        .map(admin -> authenticateEmployee(
                                admin.getPasswordHash(),
                                request.getPassword(),
                                resolveRoleName(admin.getRole() != null ? admin.getRole() : EmployeeRole.ADMIN),
                                admin.getId(),
                                admin.getFirstName() + " " + admin.getLastName()))
                        .orElseThrow(() -> unauthorized("Invalid login or password")));
    }

    private AuthResponse authenticateEmployee(
            String passwordHash,
            String rawPassword,
            String role,
            Long id,
            String name) {
        if (!passwordService.matches(rawPassword, passwordHash)) {
            throw unauthorized("Invalid login or password");
        }
        return new AuthResponse(role, id, name);
    }

    public AuthResponse register(RegisterRequest request) {
        if (customerRepository.findByEmailIgnoreCase(request.getEmail().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());
        customer.setEmail(request.getEmail().trim());
        customer.setPasswordHash(passwordService.hash(request.getPassword()));

        Customer saved = customerRepository.save(customer);

        customerNotificationService.notifyRegistered(saved);

        return new AuthResponse(
                "CLIENT",
                saved.getId(),
                saved.getFirstName() + " " + saved.getLastName());
    }

    // Legacy workers without a stored role are treated as mechanics in API responses.
    private String resolveRoleName(EmployeeRole role) {
        if (role == null) {
            return EmployeeRole.MECHANIC.name();
        }
        return role.name();
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
