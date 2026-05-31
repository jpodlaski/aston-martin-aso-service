package com.sanproject.aso_service;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

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

    @NotBlank(message = "Service type is required")
    private String serviceType;

    @Enumerated
    private BookingStatus status = BookingStatus.SCHEDULED;

    private BigDecimal estimatedCost;

    public ServiceBooking() {
    }

    public ServiceBooking(Long id, String customerName, String carModel, String serviceType, BookingStatus status, Vehicle vehicle) {
        this.id = id;
        this.customerName = customerName;
        this.carModel = carModel;
        this.serviceType = serviceType;
        this.status = status;
        this.vehicle = vehicle;
    }

    public Vehicle getVehicle(){
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle){
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

    public String getServiceType() {
        return serviceType;
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

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
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
}
