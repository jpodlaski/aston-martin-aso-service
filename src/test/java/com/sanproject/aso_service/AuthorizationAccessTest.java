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

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for authorization / IDOR-style access control.
 * Verifies that a JWT for role A cannot call endpoints meant for role B
 * (e.g. client cannot manage workers; worker cannot see another customer's bookings).
 * Notifications are @MockitoBean'd so tests focus on HTTP + security, not SMTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationAccessTest {

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
    private WorkerRepository workerRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer customerA;
    private Customer customerB;
    private Worker workerA;
    private Worker workerB;
    private Admin admin;
    private ServiceBooking unclaimedBooking;
    private ServiceBooking assignedToWorkerA;

    @BeforeEach
    void setUp() {
        customerA = new Customer();
        customerA.setFirstName("Alice");
        customerA.setLastName("Client");
        customerA.setEmail("alice@example.com");
        customerA.setPasswordHash(passwordService.hash("secret"));
        customerA.setEmailVerified(true);
        customerA = customerRepository.save(customerA);

        customerB = new Customer();
        customerB.setFirstName("Bob");
        customerB.setLastName("Client");
        customerB.setEmail("bob@example.com");
        customerB.setPasswordHash(passwordService.hash("secret"));
        customerB.setEmailVerified(true);
        customerB = customerRepository.save(customerB);

        Vehicle vehicleA = new Vehicle();
        vehicleA.setVin("VINALICE000000001");
        vehicleA.setConfigurationId("db12-coupe-4.0-tt-v8-8a");
        vehicleA.setModelLine("DB12");
        vehicleA.setModel("DB12 Coupe");
        vehicleA.setProductionEra("2023–present");
        vehicleA.setProductionYear(2024);
        vehicleA.setBodyStyle("Coupe");
        vehicleA.setEngine("4.0L Twin-Turbo V8");
        vehicleA.setPower("680 PS");
        vehicleA.setTransmission("8-speed Automatic");
        vehicleA.setDrivetrain("RWD");
        vehicleA.setCustomer(customerA);
        vehicleA = vehicleRepository.save(vehicleA);

        workerA = new Worker();
        workerA.setFirstName("Wendy");
        workerA.setLastName("Worker");
        workerA.setEmail("wendy@aso.local");
        workerA.setLogin("wendy");
        workerA.setPasswordHash(passwordService.hash("secret"));
        workerA.setRole(EmployeeRole.MECHANIC);
        workerA = workerRepository.save(workerA);

        workerB = new Worker();
        workerB.setFirstName("Will");
        workerB.setLastName("Worker");
        workerB.setEmail("will@aso.local");
        workerB.setLogin("will");
        workerB.setPasswordHash(passwordService.hash("secret"));
        workerB.setRole(EmployeeRole.MECHANIC);
        workerB = workerRepository.save(workerB);

        admin = adminRepository.findByLoginIgnoreCase("admin").orElseGet(() -> {
            Admin seeded = new Admin();
            seeded.setFirstName("Admin");
            seeded.setLastName("User");
            seeded.setEmail("admin@aso.local");
            seeded.setLogin("admin");
            seeded.setPasswordHash(passwordService.hash("admin"));
            seeded.setRole(EmployeeRole.ADMIN);
            return adminRepository.save(seeded);
        });

        unclaimedBooking = new ServiceBooking();
        unclaimedBooking.setVehicle(vehicleA);
        unclaimedBooking.setCustomerName("Alice Client");
        unclaimedBooking.setCarModel("DB12 Coupe");
        unclaimedBooking.setCustomerDescription("Noise");
        unclaimedBooking.setAvailabilityNotes("mornings");
        unclaimedBooking.setStatus(BookingStatus.SCHEDULED);
        unclaimedBooking = bookingRepository.save(unclaimedBooking);

        assignedToWorkerA = new ServiceBooking();
        assignedToWorkerA.setVehicle(vehicleA);
        assignedToWorkerA.setCustomerName("Alice Client");
        assignedToWorkerA.setCarModel("DB12 Coupe");
        assignedToWorkerA.setCustomerDescription("Brakes");
        assignedToWorkerA.setAvailabilityNotes("afternoons");
        assignedToWorkerA.setStatus(BookingStatus.IN_PROGRESS);
        assignedToWorkerA.setAssignedWorker(workerA);
        assignedToWorkerA.setServiceTypes(List.of("Brake inspection"));
        assignedToWorkerA.setEstimatedCost(new BigDecimal("100.00"));
        assignedToWorkerA = bookingRepository.save(assignedToWorkerA);
    }

    @Test
    void anonymousCannotViewBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", unclaimedBooking.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCanViewOwnBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", unclaimedBooking.getId())
                        .header("Authorization", bearer(clientToken(customerA))))
                .andExpect(status().isOk());
    }

    @Test
    void clientCannotViewAnotherClientsBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", unclaimedBooking.getId())
                        .header("Authorization", bearer(clientToken(customerB))))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerCanViewUnclaimedBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", unclaimedBooking.getId())
                        .header("Authorization", bearer(workerToken(workerB))))
                .andExpect(status().isOk());
    }

    @Test
    void workerCannotViewBookingAssignedToSomeoneElse() throws Exception {
        mockMvc.perform(get("/bookings/{id}", assignedToWorkerA.getId())
                        .header("Authorization", bearer(workerToken(workerB))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedWorkerCanViewOwnBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", assignedToWorkerA.getId())
                        .header("Authorization", bearer(workerToken(workerA))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewAnyBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", assignedToWorkerA.getId())
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void clientCannotListAllBookingsOrAvailableQueue() throws Exception {
        String token = bearer(clientToken(customerA));
        mockMvc.perform(get("/bookings").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/available").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyBookingMutatorsAreGone() throws Exception {
        String token = bearer(adminToken());
        mockMvc.perform(put("/bookings/{id}", unclaimedBooking.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/bookings/{id}", unclaimedBooking.getId())
                        .header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/bookings/{id}/status", unclaimedBooking.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
    }

    private String clientToken(Customer customer) {
        return jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName());
    }

    private String workerToken(Worker worker) {
        return jwtService.createToken(
                worker.getId(),
                worker.getRole().name(),
                worker.getFirstName() + " " + worker.getLastName());
    }

    private String adminToken() {
        return jwtService.createToken(
                admin.getId(),
                admin.getRole().name(),
                admin.getFirstName() + " " + admin.getLastName());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
