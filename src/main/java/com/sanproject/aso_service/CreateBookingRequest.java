package com.sanproject.aso_service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// At least one of estimatedDropOffTime or availabilityNotes is required (enforced in BookingService).
public class CreateBookingRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotBlank(message = "Customer description is required")
    private String customerDescription;

    private LocalDateTime estimatedDropOffTime;

    private String availabilityNotes;

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getCustomerDescription() {
        return customerDescription;
    }

    public void setCustomerDescription(String customerDescription) {
        this.customerDescription = customerDescription;
    }

    public LocalDateTime getEstimatedDropOffTime() {
        return estimatedDropOffTime;
    }

    public void setEstimatedDropOffTime(LocalDateTime estimatedDropOffTime) {
        this.estimatedDropOffTime = estimatedDropOffTime;
    }

    public String getAvailabilityNotes() {
        return availabilityNotes;
    }

    public void setAvailabilityNotes(String availabilityNotes) {
        this.availabilityNotes = availabilityNotes;
    }
}
