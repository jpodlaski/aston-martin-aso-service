package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.AccountTokenPurpose;
import com.sanproject.aso_service.domain.CustomerAccountToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerAccountTokenRepository extends JpaRepository<CustomerAccountToken, Long> {

    Optional<CustomerAccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    @Modifying(clearAutomatically = true)
    @Query("""
            delete from CustomerAccountToken t
            where t.customer.id = :customerId
              and t.purpose = :purpose
              and t.usedAt is null
            """)
    void deleteUnusedForCustomerAndPurpose(
            @Param("customerId") Long customerId,
            @Param("purpose") AccountTokenPurpose purpose);

    @Modifying(clearAutomatically = true)
    @Query("delete from CustomerAccountToken t where t.customer.id = :customerId")
    void deleteAllForCustomer(@Param("customerId") Long customerId);
}
