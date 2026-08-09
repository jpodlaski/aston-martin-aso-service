package com.sanproject.aso_service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// Consultant records the agreed appointment when accepting a request; actor comes from the JWT.
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
