package com.sanproject.aso_service.dto;

// Reason required for all cancellations; actor (customer vs staff) comes from the JWT.
public class CancelBookingRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
