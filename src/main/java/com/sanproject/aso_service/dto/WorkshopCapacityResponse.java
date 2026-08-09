package com.sanproject.aso_service.dto;

import java.time.LocalDateTime;

/**
 * Mechanic capacity at a proposed appointment time.
 * Open jobs occupy a slot from their appointment until completed/cancelled.
 */
public class WorkshopCapacityResponse {

    private LocalDateTime at;
    private long technicianCount;
    private long bookedCount;
    private long remainingSlots;
    private boolean available;

    public WorkshopCapacityResponse() {
    }

    public WorkshopCapacityResponse(
            LocalDateTime at,
            long technicianCount,
            long bookedCount,
            long remainingSlots,
            boolean available) {
        this.at = at;
        this.technicianCount = technicianCount;
        this.bookedCount = bookedCount;
        this.remainingSlots = remainingSlots;
        this.available = available;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public void setAt(LocalDateTime at) {
        this.at = at;
    }

    public long getTechnicianCount() {
        return technicianCount;
    }

    public void setTechnicianCount(long technicianCount) {
        this.technicianCount = technicianCount;
    }

    public long getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(long bookedCount) {
        this.bookedCount = bookedCount;
    }

    public long getRemainingSlots() {
        return remainingSlots;
    }

    public void setRemainingSlots(long remainingSlots) {
        this.remainingSlots = remainingSlots;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
