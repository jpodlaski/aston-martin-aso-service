package com.sanproject.aso_service.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for one service request / workshop job.
 * Status is the state-machine field; customerName/carModel are denormalized snapshots so history
 * still reads well if the linked vehicle is later soft-removed from the account.
 */
@Entity
public class ServiceBooking {

    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Car model is required")
    private String carModel;

    @Column(length = 2000)
    private String customerDescription;

    // Populated when a worker claims the booking; lazy-loaded unless explicitly fetched.
    @ElementCollection
    @CollectionTable(name = "booking_service_types", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "service_type")
    private List<String> serviceTypes = new ArrayList<>();

    // New bookings start SCHEDULED until a consultant accepts them.
    @Enumerated
    private BookingStatus status = BookingStatus.SCHEDULED;

    @ManyToOne
    @JoinColumn(name = "assigned_worker_id")
    private Worker assignedWorker;

    private BigDecimal estimatedCost;

    private BigDecimal finalCost;

    private LocalDateTime estimatedDropOffTime;

    @Column(length = 500)
    private String availabilityNotes;

    private LocalDateTime scheduledDateTime;

    @Column(length = 500)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    private CancelledBy cancelledBy;

    public ServiceBooking() {
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCarModel() {
        return carModel;
    }

    public String getCustomerDescription() {
        return customerDescription;
    }

    public List<String> getServiceTypes() {
        return serviceTypes;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public void setCustomerDescription(String customerDescription) {
        this.customerDescription = customerDescription;
    }

    public void setServiceTypes(List<String> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public Worker getAssignedWorker() {
        return assignedWorker;
    }

    public void setAssignedWorker(Worker assignedWorker) {
        this.assignedWorker = assignedWorker;
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
}
