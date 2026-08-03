package com.sanproject.aso_service;

/**
 * Staff roles used for authorization. Names are stored as strings in JWT claims and localStorage,
 * so they must stay in sync with the frontend constants/roles.js.
 * Management (ADMIN/CEO/COO) ≠ workshop staff (mechanics / apprentice / consultant).
 */
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
