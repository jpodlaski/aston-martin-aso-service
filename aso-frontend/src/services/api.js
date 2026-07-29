import axios from "axios";
import { clearSession, getSession } from "./auth";

// Shared Axios instance for all frontend API calls.
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
                url.includes("/auth/register");

            if (!isAuthEndpoint) {
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
