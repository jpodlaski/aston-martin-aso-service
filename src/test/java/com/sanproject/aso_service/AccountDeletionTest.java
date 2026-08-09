package com.sanproject.aso_service;

import com.sanproject.aso_service.domain.AccountTokenPurpose;
import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.repository.CustomerAccountTokenRepository;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.security.JwtService;
import com.sanproject.aso_service.security.PasswordService;
import com.sanproject.aso_service.service.AccountTokenService;
import com.sanproject.aso_service.service.BookingNotificationService;
import com.sanproject.aso_service.service.CustomerNotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Client account deletion requires an emailed confirmation token (same pattern as verify-email).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountDeletionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceBookingRepository bookingRepository;

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

    private Customer customer;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setFirstName("Pat");
        customer.setLastName("Client");
        customer.setEmail("pat.delete@example.com");
        customer.setPasswordHash(passwordService.hash("secret12"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        vehicle = new Vehicle();
        vehicle.setModel("DB12 Coupe");
        vehicle.setModelLine("DB12");
        vehicle.setVin("SCFRMDELTEST0001");
        vehicle.setCustomer(customer);
        vehicle.setRemovedFromAccount(false);
        vehicle = vehicleRepository.save(vehicle);
    }

    @Test
    void requestAccountDeletionSendsEmail() throws Exception {
        mockMvc.perform(post("/auth/request-account-deletion")
                        .header("Authorization", clientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(customerNotificationService).notifyAccountDeletionRequested(any(Customer.class), urlCaptor.capture());
        assertThat(urlCaptor.getValue()).contains("/confirm-account-deletion?token=");
    }

    @Test
    void requestAccountDeletionBlockedWhenOpenBookingExists() throws Exception {
        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setCustomerName("Pat Client");
        booking.setCarModel("DB12 Coupe");
        booking.setCustomerDescription("Noise");
        booking.setEstimatedDropOffTime(LocalDateTime.now().plusDays(3));
        booking.setStatus(BookingStatus.SCHEDULED);
        bookingRepository.save(booking);

        mockMvc.perform(post("/auth/request-account-deletion")
                        .header("Authorization", clientAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmAccountDeletionRemovesCustomerAndDetachesVehicles() throws Exception {
        String rawToken = accountTokenService.issue(customer, AccountTokenPurpose.ACCOUNT_DELETION);

        mockMvc.perform(post("/auth/confirm-account-deletion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deleted")));

        assertThat(customerRepository.findById(customer.getId())).isEmpty();
        Vehicle detached = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(detached.getCustomer()).isNull();
        assertThat(detached.isRemovedFromAccount()).isTrue();
        verify(customerNotificationService).notifyAccountDeleted(eq("Pat Client"), eq("pat.delete@example.com"));
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    private String clientAuth() {
        return "Bearer " + jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName());
    }
}
