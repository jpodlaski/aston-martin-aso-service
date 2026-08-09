package com.sanproject.aso_service.dto;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.CancelledBy;
import com.sanproject.aso_service.domain.EmployeeRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Flattened booking view for admin history; includes customer email and assigned worker details.
public class AdminBookingResponse {

    private Long id;
    private String customerName;
    private String customerEmail;
    private String customerDescription;
    private String carModel;
    private String modelLine;
    private String vin;
    private BookingStatus status;
    private List<String> serviceTypes;
    private BigDecimal estimatedCost;
    private BigDecimal finalCost;
    private LocalDateTime estimatedDropOffTime;
    private String availabilityNotes;
    private LocalDateTime scheduledDateTime;
    private String cancellationReason;
    private CancelledBy cancelledBy;
    private Long assignedWorkerId;
    private String assignedWorkerName;
    private EmployeeRole assignedWorkerRole;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerDescription() {
        return customerDescription;
    }

    public void setCustomerDescription(String customerDescription) {
        this.customerDescription = customerDescription;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getModelLine() {
        return modelLine;
    }

    public void setModelLine(String modelLine) {
        this.modelLine = modelLine;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public List<String> getServiceTypes() {
        return serviceTypes;
    }

    public void setServiceTypes(List<String> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public BigDecimal getFinalCost() {
        return finalCost;
    }

    public void setFinalCost(BigDecimal finalCost) {
        this.finalCost = finalCost;
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

    public LocalDateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(CancelledBy cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Long getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(Long assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public String getAssignedWorkerName() {
        return assignedWorkerName;
    }

    public void setAssignedWorkerName(String assignedWorkerName) {
        this.assignedWorkerName = assignedWorkerName;
    }

    public EmployeeRole getAssignedWorkerRole() {
        return assignedWorkerRole;
    }

    public void setAssignedWorkerRole(EmployeeRole assignedWorkerRole) {
        this.assignedWorkerRole = assignedWorkerRole;
    }
}
