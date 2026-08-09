package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.Vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Vehicle persistence; customer queries exclude soft-deleted rows.
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Excludes soft-deleted vehicles from the client dashboard.
    List<Vehicle> findByCustomerIdAndRemovedFromAccountFalse(Long customerId);

    // Former vehicles kept for service history after the client removes them.
    List<Vehicle> findByCustomerIdAndRemovedFromAccountTrue(Long customerId);

    Optional<Vehicle> findByVinIgnoreCase(String vin);

    // Active (not soft-removed) vehicles only — VIN may be reused after removal.
    boolean existsByVinIgnoreCaseAndRemovedFromAccountFalse(String vin);

    // Detach vehicles before hard-deleting a customer; rows stay for booking history.
    @Modifying(clearAutomatically = true)
    @Query("""
            update Vehicle v
            set v.customer = null, v.removedFromAccount = true
            where v.customer.id = :customerId
            """)
    int detachAllForCustomer(@Param("customerId") Long customerId);
}
