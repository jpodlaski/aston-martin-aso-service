package com.sanproject.aso_service;

import jakarta.validation.constraints.NotNull;

// Direct status override (legacy/admin); prefer lifecycle endpoints for normal flow.
public class UpdateBookingStatusRequest {

    @NotNull(message = "Status is required")
    private BookingStatus status;

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
