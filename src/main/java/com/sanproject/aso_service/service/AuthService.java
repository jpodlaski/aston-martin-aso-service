package com.sanproject.aso_service.service;

import com.sanproject.aso_service.domain.AccountTokenPurpose;
import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.Employee;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.dto.AuthResponse;
import com.sanproject.aso_service.dto.ChangePasswordRequest;
import com.sanproject.aso_service.dto.ConfirmAccountDeletionRequest;
import com.sanproject.aso_service.dto.ForgotPasswordRequest;
import com.sanproject.aso_service.dto.LoginRequest;
import com.sanproject.aso_service.dto.MessageResponse;
import com.sanproject.aso_service.dto.RegisterRequest;
import com.sanproject.aso_service.dto.ResendVerificationRequest;
import com.sanproject.aso_service.dto.ResetPasswordRequest;
import com.sanproject.aso_service.dto.VerifyEmailRequest;
import com.sanproject.aso_service.repository.AdminRepository;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.EmailOutboxRepository;
import com.sanproject.aso_service.repository.EmployeeRepository;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.repository.WorkerRepository;
import com.sanproject.aso_service.security.AuthSupport;
import com.sanproject.aso_service.security.AuthUser;
import com.sanproject.aso_service.security.JwtService;
import com.sanproject.aso_service.security.PasswordService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Authentication use-cases: client login (email), employee login (login name), register,
 * change password, forgot/reset password, and email verification.
 * On success we return a JWT — the frontend stores it and sends it as Authorization: Bearer on later calls.
 * Passwords are never stored in plain text; PasswordService uses BCrypt (one-way hash).
 */
@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceBookingRepository bookingRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final PasswordService passwordService;
    private final CustomerNotificationService customerNotificationService;
    private final AccountTokenService accountTokenService;
    private final JwtService jwtService;
    private final AuthSupport authSupport;
    private final String frontendBaseUrl;

    public AuthService(
            CustomerRepository customerRepository,
            WorkerRepository workerRepository,
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository,
            VehicleRepository vehicleRepository,
            ServiceBookingRepository bookingRepository,
            EmailOutboxRepository emailOutboxRepository,
            PasswordService passwordService,
            CustomerNotificationService customerNotificationService,
            AccountTokenService accountTokenService,
            JwtService jwtService,
            AuthSupport authSupport,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendBaseUrl) {
        this.customerRepository = customerRepository;
        this.workerRepository = workerRepository;
        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.passwordService = passwordService;
        this.customerNotificationService = customerNotificationService;
        this.accountTokenService = accountTokenService;
        this.jwtService = jwtService;
        this.authSupport = authSupport;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    public AuthResponse loginClient(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.getLogin().trim())
                .orElseThrow(() -> unauthorized("Invalid email or password"));

        if (!passwordService.matches(request.getPassword(), customer.getPasswordHash())) {
            throw unauthorized("Invalid email or password");
        }

        if (!customer.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email is not verified. Check your inbox for the verification link.");
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

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (customerRepository.findByEmailIgnoreCase(request.getEmail().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());
        customer.setEmail(request.getEmail().trim());
        customer.setPasswordHash(passwordService.hash(request.getPassword()));
        customer.setEmailVerified(false);

        Customer saved = customerRepository.save(customer);

        // One welcome email that includes the verification link (also kept as a dedicated resend event).
        String rawToken = accountTokenService.issue(saved, AccountTokenPurpose.EMAIL_VERIFICATION);
        String actionUrl = frontendBaseUrl + "/verify-email?token=" + rawToken;
        customerNotificationService.notifyRegistered(saved, actionUrl);

        return new MessageResponse(
                "Account created. Check your email to verify your address before signing in.");
    }

    /** Always returns the same message so callers cannot probe which emails exist. */
    @Transactional
    public MessageResponse requestPasswordReset(ForgotPasswordRequest request) {
        String genericMessage = "If that email is registered, you will receive a password reset link shortly.";

        customerRepository.findByEmailIgnoreCase(request.getEmail().trim()).ifPresent(customer -> {
            String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.PASSWORD_RESET);
            String actionUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;
            customerNotificationService.notifyPasswordReset(customer, actionUrl);
        });

        return new MessageResponse(genericMessage);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Customer customer = accountTokenService.consume(request.getToken().trim(), AccountTokenPurpose.PASSWORD_RESET);
        customer.setPasswordHash(passwordService.hash(request.getNewPassword()));
        customerRepository.save(customer);
        customerNotificationService.notifyPasswordChanged(customer);
        return new MessageResponse("Password updated. You can sign in with your new password.");
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        Customer customer = accountTokenService.consume(
                request.getToken().trim(),
                AccountTokenPurpose.EMAIL_VERIFICATION);
        customer.setEmailVerified(true);
        customerRepository.save(customer);
        return new MessageResponse("Email verified. You can sign in now.");
    }

    /** Always returns the same message so callers cannot probe which emails exist. */
    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        String genericMessage = "If that email is registered and unverified, you will receive a verification link shortly.";

        customerRepository.findByEmailIgnoreCase(request.getEmail().trim()).ifPresent(customer -> {
            if (!customer.isEmailVerified()) {
                sendVerificationEmail(customer);
            }
        });

        return new MessageResponse(genericMessage);
    }

    @Transactional
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
            customerNotificationService.notifyPasswordChanged(customer);
            return;
        }

        Employee employee = employeeRepository.findById(user.getId())
                .orElseThrow(() -> unauthorized("Not authenticated"));
        if (!passwordService.matches(currentPassword, employee.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        employee.setPasswordHash(passwordService.hash(newPassword));
        employeeRepository.save(employee);
        customerNotificationService.notifyPasswordChanged(
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getEmail());
    }

    private void sendVerificationEmail(Customer customer) {
        String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.EMAIL_VERIFICATION);
        String actionUrl = frontendBaseUrl + "/verify-email?token=" + rawToken;
        customerNotificationService.notifyEmailVerification(customer, actionUrl);
    }

    /**
     * Authenticated client requests deletion; a confirmation link is emailed (same pattern as verify-email).
     * Open SCHEDULED / IN_PROGRESS bookings must be resolved first.
     */
    @Transactional
    public MessageResponse requestAccountDeletion() {
        AuthUser user = authSupport.requireClient();
        Customer customer = customerRepository.findById(user.getId())
                .orElseThrow(() -> unauthorized("Not authenticated"));

        assertNoOpenBookings(customer.getId());

        String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.ACCOUNT_DELETION);
        String actionUrl = frontendBaseUrl + "/confirm-account-deletion?token=" + rawToken;
        customerNotificationService.notifyAccountDeletionRequested(customer, actionUrl);

        return new MessageResponse(
                "Check your email to confirm account deletion. The link expires after 1 hour.");
    }

    @Transactional
    public MessageResponse confirmAccountDeletion(ConfirmAccountDeletionRequest request) {
        Customer customer = accountTokenService.consume(
                request.getToken().trim(),
                AccountTokenPurpose.ACCOUNT_DELETION);

        assertNoOpenBookings(customer.getId());

        String name = customer.getFirstName() + " " + customer.getLastName();
        String email = customer.getEmail();
        Long customerId = customer.getId();

        emailOutboxRepository.clearCustomerId(customerId);
        accountTokenService.deleteAllForCustomer(customerId);
        vehicleRepository.detachAllForCustomer(customerId);
        customerRepository.delete(customer);

        customerNotificationService.notifyAccountDeleted(name, email);

        return new MessageResponse("Your account has been deleted.");
    }

    private void assertNoOpenBookings(Long customerId) {
        boolean hasOpen = bookingRepository.existsByVehicleCustomerIdAndStatusIn(
                customerId,
                List.of(BookingStatus.SCHEDULED, BookingStatus.READY_FOR_WORK, BookingStatus.IN_PROGRESS));
        if (hasOpen) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancel or complete open service bookings before deleting your account.");
        }
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

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:5173";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
