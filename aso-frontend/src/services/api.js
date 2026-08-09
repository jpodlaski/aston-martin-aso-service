import axios from "axios";
import { clearSession, getSession } from "./auth";

/**
 * Shared Axios client for the Spring API.
 * Request interceptor: attach JWT Bearer token from localStorage.
 * Response interceptor: on 401 (expired/invalid token), clear session and bounce to login.
 * Backend remains the source of truth — this only improves UX.
 */
export const api = axios.create({
    baseURL: "http://localhost:8080",
});

api.interceptors.request.use((config) => {
    const token = getSession()?.token;
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            const url = error.config?.url ?? "";
            const isAuthEndpoint =
                url.includes("/auth/login") ||
                url.includes("/auth/employee-login") ||
                url.includes("/auth/register") ||
                url.includes("/auth/request-account-deletion") ||
                url.includes("/auth/confirm-account-deletion") ||
                url.includes("/auth/verify-email") ||
                url.includes("/auth/reset-password") ||
                url.includes("/auth/forgot-password") ||
                url.includes("/auth/resend-verification");
            // Soft probes (e.g. live VIN check) must not wipe the session mid-flow.
            const skipRedirect = error.config?.skipAuthRedirect === true;

            if (!isAuthEndpoint && !skipRedirect) {
                clearSession();
                const path = window.location.pathname;
                const staffArea =
                    path.startsWith("/employee") || path.startsWith("/admin");
                window.location.assign(staffArea ? "/employee-login" : "/");
            }
        }
        return Promise.reject(error);
    }
);
