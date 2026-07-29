package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Booking persistence; *WithDetails queries JOIN FETCH relations needed for JSON (open-in-view=false).
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

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

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE v.id = :vehicleId
            """)
    List<ServiceBooking> findByVehicleIdWithDetails(@Param("vehicleId") Long vehicleId);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE v.customer.id = :customerId
            """)
    List<ServiceBooking> findByCustomerIdWithDetails(@Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker IS NULL AND b.status = :status
            """)
    List<ServiceBooking> findAvailableWithDetails(@Param("status") BookingStatus status);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker.id = :workerId
            """)
    List<ServiceBooking> findByWorkerIdWithDetails(@Param("workerId") Long workerId);

    List<ServiceBooking> findByAssignedWorkerId(Long workerId);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);
}
