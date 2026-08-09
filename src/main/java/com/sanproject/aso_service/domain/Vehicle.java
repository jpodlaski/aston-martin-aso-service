package com.sanproject.aso_service.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Customer-owned Aston Martin vehicle.
 * Spec fields (engine, body style, …) are copied from the JSON catalog when the car is added
 * so later catalog edits do not rewrite historical bookings.
 * removedFromAccount is a soft delete: the row stays for booking history, but the client UI hides it.
 */
@Entity
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "VIN is required")
    private String vin;

    private String securityCode;

    private String configurationId;

    private String modelLine;

    private String productionEra;

    private Integer productionYear;

    private String bodyStyle;

    private String engine;

    private String power;

    private String transmission;

    private String drivetrain;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // True when the customer removes the car from their account (row is kept for booking history).
    private boolean removedFromAccount;

    public Vehicle() {
    }

    public Long getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public String getVin() {
        return vin;
    }

    public String getSecurityCode() {
        return securityCode;
    }

    public String getConfigurationId() {
        return configurationId;
    }

    public String getModelLine() {
        return modelLine;
    }

    public String getProductionEra() {
        return productionEra;
    }

    public Integer getProductionYear() {
        return productionYear;
    }

    public String getBodyStyle() {
        return bodyStyle;
    }

    public String getEngine() {
        return engine;
    }

    public String getPower() {
        return power;
    }

    public String getTransmission() {
        return transmission;
    }

    public String getDrivetrain() {
        return drivetrain;
    }

    public Customer getCustomer() {
        return customer;
    }

    public boolean isRemovedFromAccount() {
        return removedFromAccount;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public void setSecurityCode(String securityCode) {
        this.securityCode = securityCode;
    }

    public void setConfigurationId(String configurationId) {
        this.configurationId = configurationId;
    }

    public void setModelLine(String modelLine) {
        this.modelLine = modelLine;
    }

    public void setProductionEra(String productionEra) {
        this.productionEra = productionEra;
    }

    public void setProductionYear(Integer productionYear) {
        this.productionYear = productionYear;
    }

    public void setBodyStyle(String bodyStyle) {
        this.bodyStyle = bodyStyle;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public void setPower(String power) {
        this.power = power;
    }

    public void setTransmission(String transmission) {
        this.transmission = transmission;
    }

    public void setDrivetrain(String drivetrain) {
        this.drivetrain = drivetrain;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setRemovedFromAccount(boolean removedFromAccount) {
        this.removedFromAccount = removedFromAccount;
    }
}
