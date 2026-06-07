package com.sanproject.aso_service;

// Provide customerId OR workerId (not both); reason is required for worker cancellations.
public class CancelBookingRequest {

    private Long customerId;

    private Long workerId;

    private String reason;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

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
