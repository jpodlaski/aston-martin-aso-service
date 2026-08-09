package com.sanproject.aso_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// configurationId comes from the catalog; VIN and production year are user-supplied.
public class CreateVehicleRequest {

    @NotBlank(message = "VIN is required")
    private String vin;

    @NotBlank(message = "Configuration is required")
    private String configurationId;

    @NotNull(message = "Production year is required")
    private Integer productionYear;

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(String configurationId) {
        this.configurationId = configurationId;
    }

    public Integer getProductionYear() {
        return productionYear;
    }

    public void setProductionYear(Integer productionYear) {
        this.productionYear = productionYear;
    }
}
