package com.sanproject.aso_service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Workshop declines an unclaimed booking; reason is included in the rejection email.
public class RejectBookingRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;

    @NotBlank(message = "Reason is required")
    private String reason;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
