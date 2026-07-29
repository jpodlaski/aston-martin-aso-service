package com.sanproject.aso_service;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// Worker records the agreed appointment; actor comes from the JWT.
public class ScheduleBookingRequest {

    @NotNull(message = "Scheduled date and time is required")
    private LocalDateTime scheduledDateTime;

    public LocalDateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
}
