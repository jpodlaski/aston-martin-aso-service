package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.ServiceBooking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Booking persistence. Methods named *WithDetails use JPQL JOIN FETCH.
 * Why: spring.jpa.open-in-view=false closes the Hibernate session before JSON serialization,
 * so lazy associations (vehicle, customer, worker, serviceTypes) would throw LazyInitializationException
 * unless we eagerly fetch them in the query.
 *
 * findByIdForUpdate uses SELECT … FOR UPDATE so concurrent claim/reject cannot both succeed:
 * the second transaction waits, then sees the updated row and fails the status check.
 */
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b WHERE b.id = :id")
    Optional<ServiceBooking> findByIdForUpdate(@Param("id") Long id);

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
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByCustomerIdWithDetails(@Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker IS NULL AND b.status = :status
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findAvailableWithDetails(@Param("status") BookingStatus status);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.status IN :statuses
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByStatusesWithDetails(
            @Param("statuses") java.util.Collection<BookingStatus> statuses);

    @Query("""
            SELECT COUNT(b) FROM ServiceBooking b
            WHERE b.status IN :statuses
              AND (
                    b.scheduledDateTime IS NULL
                    OR b.scheduledDateTime <= :at
                  )
            """)
    long countBusyAt(
            @Param("at") java.time.LocalDateTime at,
            @Param("statuses") java.util.Collection<BookingStatus> statuses);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker.id = :workerId
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByWorkerIdWithDetails(@Param("workerId") Long workerId);

    List<ServiceBooking> findByAssignedWorkerId(Long workerId);

    boolean existsByAssignedWorkerIdAndStatus(Long workerId, BookingStatus status);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);

    boolean existsByVehicleCustomerIdAndStatusIn(Long customerId, List<BookingStatus> statuses);
}
