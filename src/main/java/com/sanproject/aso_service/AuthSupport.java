package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// Reads the JWT principal from SecurityContext; used instead of client-supplied actor ids.
@Component
public class AuthSupport {

    private final WorkerRepository workerRepository;
    private final EmployeeRepository employeeRepository;

    public AuthSupport(WorkerRepository workerRepository, EmployeeRepository employeeRepository) {
        this.workerRepository = workerRepository;
        this.employeeRepository = employeeRepository;
    }

    public AuthUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return user;
    }

    public AuthUser requireClient() {
        AuthUser user = requireUser();
        if (!"CLIENT".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client access required");
        }
        return user;
    }

    public Worker requireWorker() {
        AuthUser user = requireUser();
        EmployeeRole role = parseRole(user.getRole());
        if (!role.isWorkshopStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workshop staff access required");
        }
        return workerRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Worker account required"));
    }

    public Employee requireCanManageWorkers() {
        AuthUser user = requireUser();
        EmployeeRole role = parseRole(user.getRole());
        if (!role.canManageWorkers()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to manage workers");
        }
        return employeeRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee account required"));
    }

    public boolean isManagement() {
        try {
            return parseRole(requireUser().getRole()).canManageWorkers();
        } catch (ResponseStatusException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isWorkshopStaff() {
        try {
            return parseRole(requireUser().getRole()).isWorkshopStaff();
        } catch (ResponseStatusException | IllegalArgumentException ex) {
            return false;
        }
    }

    private EmployeeRole parseRole(String roleName) {
        try {
            return EmployeeRole.valueOf(roleName);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid role");
        }
    }
}
