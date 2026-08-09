package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.Customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Used for client login and duplicate-email check on registration.
    Optional<Customer> findByEmailIgnoreCase(String email);
}
