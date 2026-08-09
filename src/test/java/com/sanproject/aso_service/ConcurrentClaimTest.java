package com.sanproject.aso_service;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.domain.Worker;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.repository.WorkerRepository;
import com.sanproject.aso_service.security.JwtService;
import com.sanproject.aso_service.security.PasswordService;
import com.sanproject.aso_service.service.BookingNotificationService;
import com.sanproject.aso_service.service.CustomerNotificationService;

import org.junit.jupiter.api.AfterEach;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

/**
 * Verifies claim is race-safe: two workers claiming the same booking at once → exactly one wins.
 * Intentionally not @Transactional — each HTTP request must commit so the row lock is visible
 * across threads (a single test transaction would hide the concurrency bug).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentClaimTest {

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
    private ServiceBookingRepository bookingRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer customer;
    private Vehicle vehicle;
    private Worker workerA;
    private Worker workerB;
    private Worker consultant;
    private Long bookingId;

    @BeforeEach
    void setUp() throws Exception {
        customer = new Customer();
        customer.setFirstName("Casey");
        customer.setLastName("Concurrent");
        customer.setEmail("casey.concurrent@example.com");
        customer.setPasswordHash(passwordService.hash("secret"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        vehicle = new Vehicle();
        vehicle.setVin("VINCONCURRENT00001");
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

        Worker consultantWorker = new Worker();
        consultantWorker.setFirstName("Chris");
        consultantWorker.setLastName("Consultant");
        consultantWorker.setEmail("chris.concurrent@aso.local");
        consultantWorker.setLogin("chris.concurrent");
        consultantWorker.setPasswordHash(passwordService.hash("secret"));
        consultantWorker.setRole(EmployeeRole.CLIENT_SERVICE_CONSULTANT);
        consultant = workerRepository.save(consultantWorker);

        workerA = saveWorker("morgan.concurrent", "morgan.concurrent@aso.local");
        workerB = saveWorker("riley.concurrent", "riley.concurrent@aso.local");

        MvcResult created = mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Race condition check",
                                  "estimatedDropOffTime": "2030-06-15T10:30:00",
                                  "availabilityNotes": "Any morning"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/bookings/{id}/accept", bookingId)
                        .header("Authorization", workerAuth(consultant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"2030-06-16T10:30:00\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        if (bookingId != null) {
            bookingRepository.findById(bookingId).ifPresent(bookingRepository::delete);
        }
        if (vehicle != null && vehicle.getId() != null) {
            vehicleRepository.deleteById(vehicle.getId());
        }
        if (customer != null && customer.getId() != null) {
            customerRepository.deleteById(customer.getId());
        }
        if (workerA != null && workerA.getId() != null) {
            workerRepository.deleteById(workerA.getId());
        }
        if (workerB != null && workerB.getId() != null) {
            workerRepository.deleteById(workerB.getId());
        }
        if (consultant != null && consultant.getId() != null) {
            workerRepository.deleteById(consultant.getId());
        }
    }

    @Test
    void onlyOneWorkerWinsWhenClaimingConcurrently() throws Exception {
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger notFoundCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> claimA = pool.submit(claimTask(workerA, ready, start, okCount, notFoundCount));
            Future<?> claimB = pool.submit(claimTask(workerB, ready, start, okCount, notFoundCount));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();

            claimA.get(10, TimeUnit.SECONDS);
            claimB.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, okCount.get(), "exactly one claim should succeed");
        assertEquals(1, notFoundCount.get(), "the other claim should see the booking already taken");

        ServiceBooking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
        assertNotNull(booking);
        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        assertNotNull(booking.getAssignedWorker());
        Long winnerId = booking.getAssignedWorker().getId();
        assertTrue(
                winnerId.equals(workerA.getId()) || winnerId.equals(workerB.getId()),
                "winner must be one of the two claimants");
    }

    private Runnable claimTask(
            Worker worker,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger okCount,
            AtomicInteger notFoundCount) {
        return () -> {
            try {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                MvcResult result = mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                                .header("Authorization", workerAuth(worker))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"serviceTypes\":[\"Inspection\"],\"estimatedCost\":100}"))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    okCount.incrementAndGet();
                } else if (status == 404) {
                    notFoundCount.incrementAndGet();
                } else {
                    throw new AssertionError("Unexpected claim status: " + status
                            + " body=" + result.getResponse().getContentAsString());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private Worker saveWorker(String login, String email) {
        Worker worker = new Worker();
        worker.setFirstName(login);
        worker.setLastName("Mechanic");
        worker.setEmail(email);
        worker.setLogin(login);
        worker.setPasswordHash(passwordService.hash("secret"));
        worker.setRole(EmployeeRole.MECHANIC);
        return workerRepository.save(worker);
    }

    private String clientAuth() {
        return "Bearer " + jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName());
    }

    private String workerAuth(Worker worker) {
        return "Bearer " + jwtService.createToken(
                worker.getId(),
                worker.getRole().name(),
                worker.getFirstName() + " " + worker.getLastName());
    }
}
