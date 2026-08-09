import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import "../../components/DashboardLayout.css";
import "./AuthShell.css";

export default function ResetPassword() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        setError("");
        setMessage("");

        if (!token) {
            setError("Missing reset token. Open the link from your email.");
            return;
        }
        if (newPassword !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setLoading(true);
        try {
            const res = await api.post("/auth/reset-password", { token, newPassword });
            setMessage(res.data?.message ?? "Password updated.");
            setNewPassword("");
            setConfirmPassword("");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not reset password.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-shell">
            <div className="auth-shell-panel">
                <Link to="/" className="logo" aria-label="Aston Martin home">
                    <img src="/aston-martin-logo.png" alt="Aston Martin" />
                </Link>
                <h1>Reset password</h1>
                <p className="auth-shell-lede">Choose a new password (at least 8 characters).</p>

                <form className="auth-form" onSubmit={submit}>
                    <input
                        type="password"
                        placeholder="New password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        required
                        minLength={8}
                        autoComplete="new-password"
                    />
                    <input
                        type="password"
                        placeholder="Confirm new password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        required
                        minLength={8}
                        autoComplete="new-password"
                    />
                    <button type="submit" disabled={loading || !token}>
                        {loading ? "Updating…" : "Update password"}
                    </button>
                    {message && <p className="form-success">{message}</p>}
                    {error && <p className="form-error">{error}</p>}
                </form>

                <div className="auth-links">
                    <Link to="/login">Back to sign in</Link>
                </div>
            </div>
        </div>
    );
}
