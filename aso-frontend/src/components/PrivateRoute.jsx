import { Navigate } from "react-router-dom";
import { getDashboardPathForRole, getSession } from "../services/auth";

// Redirects unauthenticated users to the correct login page; enforces role on protected routes.
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
