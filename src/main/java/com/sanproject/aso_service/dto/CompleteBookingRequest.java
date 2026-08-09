package com.sanproject.aso_service.dto;

import com.sanproject.aso_service.domain.Worker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Worker marks work done; actor comes from the JWT.
public class CompleteBookingRequest {

    @NotNull(message = "Final cost is required")
    @DecimalMin(value = "0.01", message = "Final cost must be positive")
    private BigDecimal finalCost;

    public BigDecimal getFinalCost() {
        return finalCost;
    }

    public void setFinalCost(BigDecimal finalCost) {
        this.finalCost = finalCost;
    }
}
