package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    // Fallback lookup when employee login does not match a worker.
    Optional<Admin> findByLoginIgnoreCase(String login);
}
