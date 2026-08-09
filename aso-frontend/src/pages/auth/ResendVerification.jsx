import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";
import "../../components/DashboardLayout.css";
import "./AuthShell.css";

export default function ResendVerification() {
    const [email, setEmail] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        setMessage("");

        try {
            const res = await api.post("/auth/resend-verification", { email });
            setMessage(res.data?.message ?? "If that email needs verification, a link was sent.");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not resend verification.");
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
                <h1>Resend verification</h1>
                <p className="auth-shell-lede">
                    Enter your email if you still need to verify your account.
                </p>

                <form className="auth-form" onSubmit={submit}>
                    <input
                        placeholder="Email"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
                    <button type="submit" disabled={loading}>
                        {loading ? "Sending…" : "Resend link"}
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
