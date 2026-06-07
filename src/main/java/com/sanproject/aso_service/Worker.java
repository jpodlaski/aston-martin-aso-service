package com.sanproject.aso_service;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// Workshop staff account; created and managed from the admin dashboard.
@Entity
@DiscriminatorValue("WORKER")
public class Worker extends Employee {

    public Worker() {
    }
}
