package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Verifies the requester exists and holds a role that can manage workers.
@Service
public class EmployeeAuthorizationService {

    private final EmployeeRepository employeeRepository;

    public EmployeeAuthorizationService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee requireCanManageWorkers(Long requesterId) {
        Employee employee = employeeRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        if (employee.getRole() == null || !employee.getRole().canManageWorkers()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to manage workers");
        }

        return employee;
    }
}
