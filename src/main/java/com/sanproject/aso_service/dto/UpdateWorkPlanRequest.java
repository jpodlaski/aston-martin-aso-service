package com.sanproject.aso_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/** Assigned worker updates planned work after diagnosis (e.g. check → brake replace). */
public class UpdateWorkPlanRequest {

    @DecimalMin(value = "0.01", message = "Estimated cost must be positive")
    private BigDecimal estimatedCost;

    @NotEmpty(message = "At least one service type is required")
    private List<@NotBlank(message = "Service type cannot be blank") String> serviceTypes;

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public List<String> getServiceTypes() {
        return serviceTypes;
    }

    public void setServiceTypes(List<String> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }
}
