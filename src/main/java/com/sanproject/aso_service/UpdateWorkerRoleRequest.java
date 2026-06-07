package com.sanproject.aso_service;

import jakarta.validation.constraints.NotNull;

// Role change; ADMIN role cannot be assigned to worker accounts.
public class UpdateWorkerRoleRequest {

    @NotNull(message = "Requester ID is required")
    private Long requesterId;

    @NotNull(message = "Role is required")
    private EmployeeRole role;

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public void setRole(EmployeeRole role) {
        this.role = role;
    }
}
