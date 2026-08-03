package com.sanproject.aso_service.catalog;

import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads aston-martin-catalog.json once at startup and indexes configurationId → full specs.
 * The catalog is the single source of truth: clients pick a config in the UI; the API rejects
 * unknown configurationIds so users cannot invent arbitrary models via the API.
 */
@Service
public class VehicleCatalogService {

    private final ObjectMapper objectMapper;
    private VehicleCatalog catalog;
    private Map<String, ResolvedVehicleConfiguration> configurationsById = Map.of();

    public VehicleCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Build an in-memory index of configurationId → resolved vehicle attributes.
    @PostConstruct
    void loadCatalog() throws IOException {
        try (InputStream input = new ClassPathResource("aston-martin-catalog.json").getInputStream()) {
            catalog = objectMapper.readValue(input, VehicleCatalog.class);
        }
        Map<String, ResolvedVehicleConfiguration> index = new HashMap<>();
        for (ModelLine modelLine : catalog.getModelLines()) {
            modelLine.setAvailableYears(ProductionYearParser.parseEra(modelLine.getEra()));
            for (BodyStyleOption bodyStyle : modelLine.getBodyStyles()) {
                for (ConfigurationOption configuration : bodyStyle.getConfigurations()) {
                    index.put(configuration.getId(), new ResolvedVehicleConfiguration(
                            configuration.getId(),
                            modelLine.getName(),
                            configuration.getDisplayName(),
                            modelLine.getEra(),
                            bodyStyle.getName(),
                            configuration.getEngine(),
                            configuration.getPower(),
                            configuration.getTransmission(),
                            configuration.getDrivetrain()));
                }
            }
        }
        configurationsById = Map.copyOf(index);
    }

    public VehicleCatalog getCatalog() {
        return catalog;
    }

    public ResolvedVehicleConfiguration requireConfiguration(String configurationId) {
        return findConfiguration(configurationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown vehicle configuration: " + configurationId));
    }

    public Optional<ResolvedVehicleConfiguration> findConfiguration(String configurationId) {
        if (configurationId == null || configurationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(configurationsById.get(configurationId));
    }

    public void validateProductionYear(String era, Integer productionYear) {
        if (productionYear == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Production year is required");
        }
        if (!ProductionYearParser.parseEra(era).contains(productionYear)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Production year " + productionYear + " is not valid for this model");
        }
    }
}
