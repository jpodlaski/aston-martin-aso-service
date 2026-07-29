package com.sanproject.aso_service;

// Role names match the strings returned by /auth/employee-login and stored in the frontend session.
public enum EmployeeRole {
    ADMIN,
    CEO,
    COO,
    CLIENT_SERVICE_CONSULTANT,
    MECHANIC,
    APPRENTICE_MECHANIC;

    // Admin, CEO, and COO can add, update roles, and delete workers.
    public boolean canManageWorkers() {
        return this == ADMIN || this == CEO || this == COO;
    }

    // Roles that claim and manage workshop bookings on the employee dashboard.
    public boolean isWorkshopStaff() {
        return this == CLIENT_SERVICE_CONSULTANT
                || this == MECHANIC
                || this == APPRENTICE_MECHANIC;
    }

    // ADMIN is reserved for back-office accounts, not workshop staff records.
    public boolean isAssignableToWorker() {
        return this != ADMIN;
    }
}
