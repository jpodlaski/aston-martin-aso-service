package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Vehicle persistence; customer queries exclude soft-deleted rows.
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Excludes soft-deleted vehicles from the client dashboard.
    List<Vehicle> findByCustomerIdAndRemovedFromAccountFalse(Long customerId);

    Optional<Vehicle> findByVinIgnoreCase(String vin);
}
