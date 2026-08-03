import { Navigate } from "react-router-dom";
import { getDashboardPathForRole, getSession } from "../services/auth";

/**
 * Client-side route guard (UI only — the API still enforces auth/roles on every call).
 * No session → login page; wrong role → redirect to that role's own dashboard.
 */
export default function PrivateRoute({ children, roles }) {
    const session = getSession();

    if (!session) {
        // Staff routes (any non-CLIENT role) redirect to employee login, not client login.
        const loginPath = roles?.some((role) => role !== "CLIENT")
            ? "/employee-login"
            : "/";
        return <Navigate to={loginPath} replace />;
    }

    if (roles && !roles.includes(session.role)) {
        return <Navigate to={getDashboardPathForRole(session.role)} replace />;
    }

    return children;
}
