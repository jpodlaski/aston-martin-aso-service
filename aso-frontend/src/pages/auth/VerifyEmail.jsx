import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import "../../components/DashboardLayout.css";
import "./AuthShell.css";

export default function VerifyEmail() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(Boolean(token));

    useEffect(() => {
        if (!token) {
            setError("Missing verification token. Open the link from your email.");
            setLoading(false);
            return;
        }

        let cancelled = false;
        (async () => {
            try {
                const res = await api.post("/auth/verify-email", { token });
                if (!cancelled) {
                    setMessage(res.data?.message ?? "Email verified.");
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err.response?.data?.message ?? "Could not verify email.");
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
                <h1>Verify email</h1>
                {loading && <p className="auth-shell-lede">Verifying your email…</p>}
                {message && <p className="form-success">{message}</p>}
                {error && <p className="form-error">{error}</p>}
                <div className="auth-links">
                    <Link to="/login">Sign in</Link>
                    <Link to="/resend-verification">Resend verification</Link>
                </div>
            </div>
        </div>
    );
}
