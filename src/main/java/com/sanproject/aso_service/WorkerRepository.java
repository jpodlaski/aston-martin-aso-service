package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    // Primary lookup for employee login.
    Optional<Worker> findByLoginIgnoreCase(String login);
}
