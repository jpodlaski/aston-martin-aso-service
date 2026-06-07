package com.sanproject.aso_service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Worker marks work done and sets the final cost; triggers invoice email with PDF.
public class CompleteBookingRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;

    @NotNull(message = "Final cost is required")
    @DecimalMin(value = "0.01", message = "Final cost must be positive")
    private BigDecimal finalCost;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public BigDecimal getFinalCost() {
        return finalCost;
    }

    public void setFinalCost(BigDecimal finalCost) {
        this.finalCost = finalCost;
    }
}
