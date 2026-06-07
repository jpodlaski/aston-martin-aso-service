// Must stay in sync with EmployeeRole.canManageWorkers() on the backend.
export const MANAGEMENT_ROLES = ["ADMIN", "CEO", "COO"];

// Roles that use the employee dashboard to claim and manage bookings.
export const WORKSHOP_ROLES = [
    "CLIENT_SERVICE_CONSULTANT",
    "MECHANIC",
    "APPRENTICE_MECHANIC",
];

// Roles offered when creating a worker; excludes ADMIN (back-office only).
export const ASSIGNABLE_ROLES = [
    "CEO",
    "COO",
    "CLIENT_SERVICE_CONSULTANT",
    "MECHANIC",
    "APPRENTICE_MECHANIC",
];

export const ROLE_LABELS = {
    ADMIN: "Admin",
    CEO: "CEO",
    COO: "COO",
    CLIENT_SERVICE_CONSULTANT: "Client service consultant",
    MECHANIC: "Mechanic",
    APPRENTICE_MECHANIC: "Apprentice mechanic",
};

export function roleLabel(role) {
    return ROLE_LABELS[role] ?? role;
}
