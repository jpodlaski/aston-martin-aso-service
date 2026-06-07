package com.sanproject.aso_service;

import org.springframework.data.jpa.repository.JpaRepository;

// Polymorphic repository over Admin and Worker for authorization lookups.
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
