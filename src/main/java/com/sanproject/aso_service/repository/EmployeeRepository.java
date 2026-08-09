package com.sanproject.aso_service.repository;

import com.sanproject.aso_service.domain.Admin;
import com.sanproject.aso_service.domain.Employee;
import com.sanproject.aso_service.domain.Worker;

import org.springframework.data.jpa.repository.JpaRepository;

// Polymorphic repository over Admin and Worker for authorization lookups.
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
