package com.sanproject.aso_service;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// Worker records the agreed appointment after coordinating with the customer by phone.
public class ScheduleBookingRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;

    @NotNull(message = "Scheduled date and time is required")
    private LocalDateTime scheduledDateTime;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public LocalDateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
}
