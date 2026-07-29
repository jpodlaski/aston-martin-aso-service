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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingLifecycleTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer customer;
    private Vehicle vehicle;
    private Worker worker;
    private Worker otherWorker;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setFirstName("Casey");
        customer.setLastName("Client");
        customer.setEmail("casey@example.com");
        customer.setPasswordHash(passwordService.hash("secret"));
        customer = customerRepository.save(customer);

        vehicle = new Vehicle();
        vehicle.setVin("VINCASEY000000001");
        vehicle.setConfigurationId("db12-coupe-4.0-tt-v8-8a");
        vehicle.setModelLine("DB12");
        vehicle.setModel("DB12 Coupe");
        vehicle.setProductionEra("2023–present");
        vehicle.setProductionYear(2024);
        vehicle.setBodyStyle("Coupe");
        vehicle.setEngine("4.0L Twin-Turbo V8");
        vehicle.setPower("680 PS");
        vehicle.setTransmission("8-speed Automatic");
        vehicle.setDrivetrain("RWD");
        vehicle.setCustomer(customer);
        vehicle = vehicleRepository.save(vehicle);

        worker = new Worker();
        worker.setFirstName("Morgan");
        worker.setLastName("Mechanic");
        worker.setEmail("morgan@aso.local");
        worker.setLogin("morgan");
        worker.setPasswordHash(passwordService.hash("secret"));
        worker.setRole(EmployeeRole.MECHANIC);
        worker = workerRepository.save(worker);

        otherWorker = new Worker();
        otherWorker.setFirstName("Riley");
        otherWorker.setLastName("Mechanic");
        otherWorker.setEmail("riley@aso.local");
        otherWorker.setLogin("riley");
        otherWorker.setPasswordHash(passwordService.hash("secret"));
        otherWorker.setRole(EmployeeRole.MECHANIC);
        otherWorker = workerRepository.save(otherWorker);
    }

    @Test
    void happyPath_createClaimScheduleComplete() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(get("/bookings/available").header("Authorization", workerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bookingId)));

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estimatedCost": 350.00,
                                  "serviceTypes": ["Brake inspection", "Oil change"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedWorker.id").value(worker.getId().intValue()))
                .andExpect(jsonPath("$.serviceTypes", hasSize(2)))
                .andExpect(jsonPath("$.estimatedCost").value(350.00));

        String appointment = LocalDateTime.now().plusDays(2).withNano(0).format(ISO);
        mockMvc.perform(post("/bookings/{id}/schedule", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + appointment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(appointment))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/bookings/{id}/complete", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\": 375.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalCost").value(375.50));

        mockMvc.perform(get("/customers/me/bookings").header("Authorization", clientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bookingId + ")].status").value(hasItem("COMPLETED")));
    }

    @Test
    void workerCanRejectUnclaimedBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/reject", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cannot service this model at this site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("WORKSHOP"))
                .andExpect(jsonPath("$.cancellationReason").value("Cannot service this model at this site"));

        mockMvc.perform(get("/bookings/available").header("Authorization", workerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bookingId + ")]").isEmpty());
    }

    @Test
    void customerCanCancelOwnOpenBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId)
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"));
    }

    @Test
    void assignedWorkerCanCancelWithReason() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Diagnostics\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Parts unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("WORKER"))
                .andExpect(jsonPath("$.cancellationReason").value("Parts unavailable"));
    }

    @Test
    void otherWorkerCannotScheduleOrCompleteClaimedBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Alignment\"]}"))
                .andExpect(status().isOk());

        String appointment = LocalDateTime.now().plusDays(1).withNano(0).format(ISO);
        mockMvc.perform(post("/bookings/{id}/schedule", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + appointment + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/bookings/{id}/complete", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\": 100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotClaimAlreadyClaimedBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotRejectClaimedBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/reject", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotClaimBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBookingRequiresAvailability() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Noise only"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isBadRequest());
    }

    private long createBookingAsClient() throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Strange noise when braking",
                                  "availabilityNotes": "Weekday mornings"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.assignedWorker").value(nullValue()))
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String clientAuth() {
        return bearer(jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName()));
    }

    private String workerAuth() {
        return bearer(jwtService.createToken(
                worker.getId(),
                worker.getRole().name(),
                worker.getFirstName() + " " + worker.getLastName()));
    }

    private String otherWorkerAuth() {
        return bearer(jwtService.createToken(
                otherWorker.getId(),
                otherWorker.getRole().name(),
                otherWorker.getFirstName() + " " + otherWorker.getLastName()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
