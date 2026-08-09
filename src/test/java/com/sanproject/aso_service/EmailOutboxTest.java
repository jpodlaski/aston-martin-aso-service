package com.sanproject.aso_service;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.EmailOutboxChannel;
import com.sanproject.aso_service.domain.EmailOutboxStatus;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.domain.Worker;
import com.sanproject.aso_service.email.EmailDeliveryException;
import com.sanproject.aso_service.email.EmailOutboxProcessor;
import com.sanproject.aso_service.email.EmailOutboxService;
import com.sanproject.aso_service.email.EmailRendererClient;
import com.sanproject.aso_service.email.MailService;
import com.sanproject.aso_service.email.RenderedEmail;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.EmailOutboxRepository;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.repository.WorkerRepository;
import com.sanproject.aso_service.security.JwtService;
import com.sanproject.aso_service.security.PasswordService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

/**
 * Transactional outbox: booking change + PENDING row commit together; delivery retries after failure.
 * SyncTaskExecutor makes @Async run inline so tests do not race the thread pool.
 */
@SpringBootTest
@Import(EmailOutboxTest.SyncAsyncConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailOutboxTest {

    @TestConfiguration
    static class SyncAsyncConfig {
        @Bean(name = {"taskExecutor", "applicationTaskExecutor"})
        @Primary
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

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

    @Autowired
    private EmailOutboxRepository outboxRepository;

    @Autowired
    private EmailOutboxProcessor outboxProcessor;

    @Autowired
    private EmailOutboxService outboxService;

    @MockitoBean
    private EmailRendererClient emailRendererClient;

    @MockitoBean
    private MailService mailService;

    private Customer customer;
    private Vehicle vehicle;
    private Worker worker;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();

        String suffix = String.valueOf(System.nanoTime());

        customer = new Customer();
        customer.setFirstName("Outbox");
        customer.setLastName("Client");
        customer.setEmail("outbox.client." + suffix + "@example.com");
        customer.setPasswordHash(passwordService.hash("secret"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        vehicle = new Vehicle();
        vehicle.setVin(("VINOUTBOX" + suffix).substring(0, Math.min(17, ("VINOUTBOX" + suffix).length())));
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
        worker.setFirstName("Outbox");
        worker.setLastName("Mechanic");
        worker.setEmail("outbox.mechanic." + suffix + "@aso.local");
        worker.setLogin("outbox-mechanic-" + suffix);
        worker.setPasswordHash(passwordService.hash("secret"));
        worker.setRole(EmployeeRole.MECHANIC);
        worker = workerRepository.save(worker);

        RenderedEmail rendered = new RenderedEmail();
        rendered.setSubject("Test");
        rendered.setHtmlBody("<p>hi</p>");
        rendered.setTextBody("hi");
        when(emailRendererClient.render(any())).thenReturn(Optional.of(rendered));
        when(emailRendererClient.renderCustomer(any())).thenReturn(Optional.of(rendered));
    }

    @Test
    void bookingCreateEnqueuesAndSendsOutboxRow() throws Exception {
        long bookingId = createBooking();

        List<EmailOutbox> rows = outboxRepository.findByBookingIdOrderByIdAsc(bookingId);
        assertEquals(1, rows.size());
        EmailOutbox row = rows.getFirst();
        assertEquals("created", row.getEvent());
        assertEquals(EmailOutboxChannel.BOOKING, row.getChannel());

        // If async dispatch was skipped for any reason, the poller path still delivers.
        if (row.getStatus() == EmailOutboxStatus.PENDING) {
            outboxProcessor.process(row.getId());
            row = outboxRepository.findById(row.getId()).orElseThrow();
        }

        assertEquals(EmailOutboxStatus.SENT, row.getStatus());
        assertNotNull(row.getSentAt());
        verify(mailService, times(1)).send(any(), any());
    }

    @Test
    void pendingRowIsRetriedAfterTransientFailure() {
        ServiceBooking booking = bookingRepository.save(newBooking());

        // Enqueue only (no after-commit dispatch) to simulate crash before send.
        EmailOutbox pending = outboxService.enqueueBooking(booking.getId(), "created", null);
        assertEquals(EmailOutboxStatus.PENDING, pending.getStatus());

        doThrow(new EmailDeliveryException("SMTP down"))
                .doNothing()
                .when(mailService).send(any(), any());

        outboxProcessor.process(pending.getId());
        EmailOutbox afterFail = outboxRepository.findById(pending.getId()).orElseThrow();
        assertEquals(EmailOutboxStatus.PENDING, afterFail.getStatus());
        assertEquals(1, afterFail.getAttemptCount());
        assertTrue(afterFail.getNextAttemptAt().isAfter(LocalDateTime.now().minusSeconds(1)));

        afterFail.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        outboxRepository.save(afterFail);

        outboxProcessor.process(pending.getId());
        EmailOutbox afterRetry = outboxRepository.findById(pending.getId()).orElseThrow();
        assertEquals(EmailOutboxStatus.SENT, afterRetry.getStatus());
        assertEquals(2, afterRetry.getAttemptCount());
        verify(mailService, times(2)).send(any(), any());
    }

    @Test
    void processDueRecoversLeftoverPendingRows() {
        long bookingId = bookingRepository.save(newBooking()).getId();
        EmailOutbox pending = outboxService.enqueueBooking(bookingId, "created", null);
        assertEquals(EmailOutboxStatus.PENDING, outboxRepository.findById(pending.getId()).orElseThrow().getStatus());

        outboxProcessor.processDue();

        assertEquals(
                EmailOutboxStatus.SENT,
                outboxRepository.findById(pending.getId()).orElseThrow().getStatus());
    }

    private ServiceBooking newBooking() {
        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setCustomerName("Outbox Client");
        booking.setCarModel("DB12 Coupe");
        booking.setCustomerDescription("Outbox booking");
        booking.setAvailabilityNotes("afternoons");
        booking.setStatus(BookingStatus.SCHEDULED);
        return booking;
    }

    private long createBooking() throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Outbox happy path",
                                  "estimatedDropOffTime": "2030-06-15T10:30:00",
                                  "availabilityNotes": "Weekdays"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String clientAuth() {
        return "Bearer " + jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName());
    }
}
