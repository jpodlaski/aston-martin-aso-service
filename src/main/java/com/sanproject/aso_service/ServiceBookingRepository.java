package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Booking persistence; find*WithDetails queries support admin history and async emails.
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

    // JOIN FETCH loads related data in one query for admin history and async emails.
    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findAllWithDetails();

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.id = :id
            """)
    Optional<ServiceBooking> findByIdWithDetails(@Param("id") Long id);

    List<ServiceBooking> findByVehicleId(Long vehicleId);

    List<ServiceBooking> findByVehicleCustomerId(Long customerId);

    List<ServiceBooking> findByAssignedWorkerIsNullAndStatus(BookingStatus status);

    List<ServiceBooking> findByAssignedWorkerId(Long workerId);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);
}
