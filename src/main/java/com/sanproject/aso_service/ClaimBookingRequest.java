package com.sanproject.aso_service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

// Worker claims an unclaimed booking and defines services plus optional estimated cost.
public class ClaimBookingRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;

    @DecimalMin(value = "0.01", message = "Estimated cost must be positive")
    private BigDecimal estimatedCost;

    @NotEmpty(message = "At least one service type is required")
    private List<@NotBlank(message = "Service type cannot be blank") String> serviceTypes;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

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
