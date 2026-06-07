import { MANAGEMENT_ROLES, WORKSHOP_ROLES } from "../constants/roles";

// Client-side session only; no expiry — logout clears this key.
const SESSION_KEY = "aso_session";

export function saveSession(session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function getSession() {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    try {
        return JSON.parse(raw);
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
