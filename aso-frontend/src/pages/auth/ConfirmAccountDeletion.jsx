import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { clearSession } from "../../services/auth";
import "../../components/DashboardLayout.css";
import "./AuthShell.css";

export default function ConfirmAccountDeletion() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(Boolean(token));

    useEffect(() => {
        if (!token) {
            setError("Missing confirmation token. Open the link from your email.");
            setLoading(false);
            return;
        }

        let cancelled = false;
        (async () => {
            try {
                const res = await api.post(
                    "/auth/confirm-account-deletion",
                    { token },
                    { skipAuthRedirect: true }
                );
                if (!cancelled) {
                    clearSession();
                    setMessage(res.data?.message ?? "Your account has been deleted.");
                }
            } catch (err) {
                if (!cancelled) {
                    setError(
                        err.response?.data?.message ?? "Could not delete account."
                    );
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [token]);

    return (
        <div className="auth-shell">
            <div className="auth-shell-panel">
                <Link to="/" className="logo" aria-label="Aston Martin home">
                    <img src="/aston-martin-logo.png" alt="Aston Martin" />
                </Link>
                <h1>Delete account</h1>
                {loading && (
                    <p className="auth-shell-lede">Confirming account deletion…</p>
                )}
                {message && <p className="form-success">{message}</p>}
                {error && <p className="form-error">{error}</p>}
                <div className="auth-links">
                    <Link to="/">Back to home</Link>
                    {!message && <Link to="/login">Sign in</Link>}
                </div>
            </div>
        </div>
    );
}
