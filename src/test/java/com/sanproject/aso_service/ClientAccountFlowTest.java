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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers client register → verify email → login, plus forgot/reset password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientAccountFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAccountTokenRepository tokenRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AccountTokenService accountTokenService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    @BeforeEach
    void stubNotifications() {
        doNothing().when(customerNotificationService).notifyRegistered(any());
        doNothing().when(customerNotificationService).notifyRegistered(any(), anyString());
        doNothing().when(customerNotificationService).notifyEmailVerification(any(), anyString());
        doNothing().when(customerNotificationService).notifyPasswordReset(any(), anyString());
    }

    @Test
    void registerRequiresVerificationBeforeLogin() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Sam",
                                  "lastName":"Client",
                                  "email":"sam@example.com",
                                  "password":"secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        Customer customer = customerRepository.findByEmailIgnoreCase("sam@example.com").orElseThrow();
        assertThat(customer.isEmailVerified()).isFalse();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"sam@example.com","password":"secret123"}
                                """))
                .andExpect(status().isForbidden());

        String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.EMAIL_VERIFICATION);
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"sam@example.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void forgotPasswordResetsWithToken() throws Exception {
        Customer customer = new Customer();
        customer.setFirstName("Riley");
        customer.setLastName("Client");
        customer.setEmail("riley@example.com");
        customer.setPasswordHash(passwordService.hash("oldpass12"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"riley@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(customerNotificationService).notifyPasswordReset(any(), anyString());

        String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.PASSWORD_RESET);
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"newpass99"}
                                """.formatted(rawToken)))
                .andExpect(status().isOk());

        Customer refreshed = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(passwordService.matches("newpass99", refreshed.getPasswordHash())).isTrue();
        assertThat(tokenRepository.findAll()).allMatch(CustomerAccountToken::isUsed);
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
