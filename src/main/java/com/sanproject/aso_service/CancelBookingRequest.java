package com.sanproject.aso_service;

// Optional reason for worker cancellations; actor (customer vs worker) comes from the JWT.
public class CancelBookingRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
