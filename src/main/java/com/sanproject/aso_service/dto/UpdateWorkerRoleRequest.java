package com.sanproject.aso_service.dto;

import com.sanproject.aso_service.domain.EmployeeRole;

import jakarta.validation.constraints.NotNull;

// Role change; ADMIN role cannot be assigned to worker accounts. Caller from JWT.
public class UpdateWorkerRoleRequest {

    @NotNull(message = "Role is required")
    private EmployeeRole role;

    public EmployeeRole getRole() {
        return role;
    }

    public void setRole(EmployeeRole role) {
        this.role = role;
    }
}
