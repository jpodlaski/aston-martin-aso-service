package com.sanproject.aso_service.dto;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.service.BookingService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// estimatedDropOffTime is required; availabilityNotes is optional (enforced in BookingService).
public class CreateBookingRequest {

    private static final int MAX_DESCRIPTION_WORDS = 500;

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotBlank(message = "Customer description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
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

    /** True when the description exceeds the anti-spam word cap. */
    public boolean descriptionExceedsWordLimit() {
        return wordCount(customerDescription) > MAX_DESCRIPTION_WORDS;
    }

    static int wordCount(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }
}
