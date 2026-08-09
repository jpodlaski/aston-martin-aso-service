package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.EmailOutboxStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailOutbox e WHERE e.id = :id")
    Optional<EmailOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT e FROM EmailOutbox e
            WHERE e.status = com.sanproject.aso_service.domain.EmailOutboxStatus.PENDING
              AND e.nextAttemptAt <= :now
            ORDER BY e.id ASC
            """)
    List<EmailOutbox> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    List<EmailOutbox> findByBookingIdOrderByIdAsc(Long bookingId);

    List<EmailOutbox> findByEventAndCustomerIdOrderByIdAsc(String event, Long customerId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailOutbox e SET e.customerId = null WHERE e.customerId = :customerId")
    int clearCustomerId(@Param("customerId") Long customerId);
}
