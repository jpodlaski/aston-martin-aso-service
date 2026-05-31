package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

    List<ServiceBooking> findByVehicleId(Long vehicleId);

    List<ServiceBooking> findByVehicleCustomerId(Long customerId);
}
