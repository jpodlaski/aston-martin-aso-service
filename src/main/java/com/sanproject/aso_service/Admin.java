package com.sanproject.aso_service;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// Back-office account; uses the shared Employee login table with discriminator ADMIN.
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Employee {

    public Admin() {
    }
}
