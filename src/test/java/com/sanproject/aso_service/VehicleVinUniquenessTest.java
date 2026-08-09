package com.sanproject.aso_service;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.security.JwtService;
import com.sanproject.aso_service.security.PasswordService;
import com.sanproject.aso_service.service.CustomerNotificationService;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VIN must be unique among active vehicles; live check endpoint powers Add vehicle feedback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VehicleVinUniquenessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer owner;
    private Customer other;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        owner = new Customer();
        owner.setFirstName("Owen");
        owner.setLastName("Owner");
        owner.setEmail("owen-vin@example.com");
        owner.setPasswordHash(passwordService.hash("secret"));
        owner.setEmailVerified(true);
        owner = customerRepository.save(owner);

        other = new Customer();
        other.setFirstName("Otis");
        other.setLastName("Other");
        other.setEmail("otis-vin@example.com");
        other.setPasswordHash(passwordService.hash("secret"));
        other.setEmailVerified(true);
        other = customerRepository.save(other);

        ownerToken = jwtService.createToken(owner.getId(), "CLIENT", owner.getEmail());
        otherToken = jwtService.createToken(other.getId(), "CLIENT", other.getEmail());

        Vehicle vehicle = new Vehicle();
        vehicle.setVin("SCFTESTVIN0000001");
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
        vehicle.setCustomer(owner);
        vehicleRepository.save(vehicle);
    }

    @Test
    void vinAvailableEndpointReportsTakenVin() throws Exception {
        mockMvc.perform(get("/vehicles/me/vin-available")
                        .param("vin", "scftestvin0000001")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.vin").value("SCFTESTVIN0000001"));
    }

    @Test
    void cannotAddVehicleWithDuplicateVin() throws Exception {
        String body = """
                {
                  "configurationId": "db12-coupe-4.0-tt-v8-8a",
                  "productionYear": 2024,
                  "vin": "SCFTESTVIN0000001"
                }
                """;

        mockMvc.perform(post("/vehicles/me")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This VIN is already registered to a vehicle"));
    }

    @Test
    void softRemovedVinCanBeReused() throws Exception {
        Vehicle existing = vehicleRepository.findByVinIgnoreCase("SCFTESTVIN0000001").orElseThrow();
        existing.setRemovedFromAccount(true);
        vehicleRepository.save(existing);

        mockMvc.perform(get("/vehicles/me/vin-available")
                        .param("vin", "SCFTESTVIN0000001")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
