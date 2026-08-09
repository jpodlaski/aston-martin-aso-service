import { useEffect } from "react";
import { getSession, subscribeSessionCleared } from "../services/auth";

/**
 * When another tab clears the session (e.g. account deletion confirmed),
 * leave protected UI immediately so a deleted user cannot keep using a stale tab.
 */
export default function SessionSync() {
    useEffect(() => {
        return subscribeSessionCleared(() => {
            if (!getSession()) {
                const path = window.location.pathname;
                if (
                    path.startsWith("/client")
                    || path.startsWith("/employee")
                    || path.startsWith("/admin")
                ) {
                    window.location.assign("/");
                }
            }
        });
    }, []);

    return null;
}
