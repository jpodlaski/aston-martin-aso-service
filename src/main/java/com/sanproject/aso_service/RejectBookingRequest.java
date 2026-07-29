package com.sanproject.aso_service;

import jakarta.validation.constraints.NotBlank;

// Workshop declines an unclaimed booking; actor comes from the JWT.
public class RejectBookingRequest {

    @NotBlank(message = "Reason is required")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
