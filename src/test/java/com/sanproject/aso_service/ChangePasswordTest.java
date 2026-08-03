package com.sanproject.aso_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies authenticated password change for client, worker, and admin.
 * Requires current password; rejects reuse of the same password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChangePasswordTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer customer;
    private Worker worker;
    private Admin admin;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setFirstName("Pat");
        customer.setLastName("Client");
        customer.setEmail("pat@example.com");
        customer.setPasswordHash(passwordService.hash("oldpass12"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        worker = new Worker();
        worker.setFirstName("Wes");
        worker.setLastName("Worker");
        worker.setEmail("wes@aso.local");
        worker.setLogin("wes");
        worker.setPasswordHash(passwordService.hash("oldpass12"));
        worker.setRole(EmployeeRole.MECHANIC);
        worker = workerRepository.save(worker);

        admin = adminRepository.findByLoginIgnoreCase("admin").orElseGet(() -> {
            Admin seeded = new Admin();
            seeded.setFirstName("Admin");
            seeded.setLastName("User");
            seeded.setEmail("admin@aso.local");
            seeded.setLogin("admin");
            seeded.setPasswordHash(passwordService.hash("oldpass12"));
            seeded.setRole(EmployeeRole.ADMIN);
            return adminRepository.save(seeded);
        });
        admin.setPasswordHash(passwordService.hash("oldpass12"));
        admin = adminRepository.save(admin);
    }

    @Test
    void unauthenticatedChangePasswordIsRejected() throws Exception {
        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"newpass12"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCanChangePasswordAndLoginWithNewOne() throws Exception {
        String token = jwtService.createToken(customer.getId(), "CLIENT", "Pat Client");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"newpass12"}
                                """))
                .andExpect(status().isNoContent());

        Customer refreshed = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(passwordService.matches("newpass12", refreshed.getPasswordHash())).isTrue();
        assertThat(passwordService.matches("oldpass12", refreshed.getPasswordHash())).isFalse();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"pat@example.com","password":"newpass12"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void workerCanChangePassword() throws Exception {
        String token = jwtService.createToken(worker.getId(), "MECHANIC", "Wes Worker");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"workerpass"}
                                """))
                .andExpect(status().isNoContent());

        Worker refreshed = workerRepository.findById(worker.getId()).orElseThrow();
        assertThat(passwordService.matches("workerpass", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void adminCanChangePassword() throws Exception {
        String token = jwtService.createToken(admin.getId(), "ADMIN", "Admin User");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"adminpass"}
                                """))
                .andExpect(status().isNoContent());

        Admin refreshed = adminRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordService.matches("adminpass", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        String token = jwtService.createToken(customer.getId(), "CLIENT", "Pat Client");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrongpass","newPassword":"newpass12"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void samePasswordIsRejected() throws Exception {
        String token = jwtService.createToken(customer.getId(), "CLIENT", "Pat Client");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"oldpass12"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        String token = jwtService.createToken(customer.getId(), "CLIENT", "Pat Client");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldpass12","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
