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
    private final EmployeeRepository employeeRepository;
    private final PasswordService passwordService;
    private final CustomerNotificationService customerNotificationService;
    private final JwtService jwtService;
    private final AuthSupport authSupport;

    public AuthService(
            CustomerRepository customerRepository,
            WorkerRepository workerRepository,
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository,
            PasswordService passwordService,
            CustomerNotificationService customerNotificationService,
            JwtService jwtService,
            AuthSupport authSupport) {
        this.customerRepository = customerRepository;
        this.workerRepository = workerRepository;
        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
        this.passwordService = passwordService;
        this.customerNotificationService = customerNotificationService;
        this.jwtService = jwtService;
        this.authSupport = authSupport;
    }

    public AuthResponse loginClient(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.getLogin().trim())
                .orElseThrow(() -> unauthorized("Invalid email or password"));

        if (!passwordService.matches(request.getPassword(), customer.getPasswordHash())) {
            throw unauthorized("Invalid email or password");
        }

        return issueToken(
                customer.getId(),
                "CLIENT",
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
        return issueToken(id, role, name);
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

        return issueToken(
                saved.getId(),
                "CLIENT",
                saved.getFirstName() + " " + saved.getLastName());
    }

    public void changePassword(ChangePasswordRequest request) {
        AuthUser user = authSupport.requireUser();
        String currentPassword = request.getCurrentPassword();
        String newPassword = request.getNewPassword();

        if (currentPassword.equals(newPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be different from the current password");
        }

        if ("CLIENT".equals(user.getRole())) {
            Customer customer = customerRepository.findById(user.getId())
                    .orElseThrow(() -> unauthorized("Not authenticated"));
            if (!passwordService.matches(currentPassword, customer.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
            }
            customer.setPasswordHash(passwordService.hash(newPassword));
            customerRepository.save(customer);
            return;
        }

        Employee employee = employeeRepository.findById(user.getId())
                .orElseThrow(() -> unauthorized("Not authenticated"));
        if (!passwordService.matches(currentPassword, employee.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        employee.setPasswordHash(passwordService.hash(newPassword));
        employeeRepository.save(employee);
    }

    private AuthResponse issueToken(Long id, String role, String name) {
        String token = jwtService.createToken(id, role, name);
        return new AuthResponse(token, role, id, name);
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
