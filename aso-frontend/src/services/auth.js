import { MANAGEMENT_ROLES, WORKSHOP_ROLES } from "../constants/roles";

/**
 * Browser session helpers. We store { token, role, id, name } in localStorage after login.
 * Logout only clears this key — JWTs are not revoked server-side (stateless auth trade-off).
 * UI routing uses role; real authorization always happens again on the API.
 */
const SESSION_KEY = "aso_session";

export function saveSession(session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function getSession() {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    try {
        const session = JSON.parse(raw);
        // Sessions from before JWT auth have no token and cannot call the API.
        if (!session?.token) return null;
        return session;
    } catch {
        return null;
    }
}

export function clearSession() {
    localStorage.removeItem(SESSION_KEY);
}

export function isClient() {
    return getSession()?.role === "CLIENT";
}

export function canManageWorkers() {
    return MANAGEMENT_ROLES.includes(getSession()?.role);
}

// Maps API role string to the default dashboard route after login.
export function getDashboardPathForRole(role) {
    if (role === "CLIENT") return "/client";
    if (MANAGEMENT_ROLES.includes(role)) return "/admin";
    if (WORKSHOP_ROLES.includes(role)) return "/employee";
    return "/employee-login"; // unknown roles fall back to staff login
}
