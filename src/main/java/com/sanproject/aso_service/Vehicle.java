package com.sanproject.aso_service;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "VIN is required")
    private String vin;

    private Integer productionYear;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    public Vehicle() {
    }

    public Vehicle(Long id, String model, String vin, Integer productionYear, Customer customer) {
        this.id = id;
        this.model = model;
        this.vin = vin;
        this.productionYear = productionYear;
        this.customer = customer;
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

    public Integer getProductionYear() {
        return productionYear;
    }

    public Customer getCustomer() {
        return customer;
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

    public void setProductionYear(Integer productionYear) {
        this.productionYear = productionYear;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}
