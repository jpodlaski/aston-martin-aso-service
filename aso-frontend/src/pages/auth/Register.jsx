import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../services/api";
import "../../components/DashboardLayout.css";
import "./AuthShell.css";

// Client self-registration; account must be email-verified before login.
export default function Register() {
    const navigate = useNavigate();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    const register = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        setMessage("");

        try {
            const res = await api.post("/auth/register", {
                firstName,
                lastName,
                email,
                password,
            });
            setMessage(res.data?.message ?? "Account created. Check your email to verify.");
            setTimeout(() => navigate("/login"), 2500);
        } catch (err) {
            setError(err.response?.data?.message ?? "Registration failed.");
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
                <h1>Create account</h1>

                <form className="auth-form" onSubmit={register}>
                    <input
                        placeholder="First name"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        required
                    />
                    <input
                        placeholder="Last name"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        required
                    />
                    <input
                        placeholder="Email"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
                    <input
                        type="password"
                        placeholder="Password (min. 8 characters)"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        minLength={8}
                    />
                    <button type="submit" disabled={loading}>
                        {loading ? "Creating account…" : "Register"}
                    </button>
                    {message && <p className="form-success">{message}</p>}
                    {error && <p className="form-error">{error}</p>}
                </form>

                <div className="auth-links">
                    <Link to="/login">Already have an account? Sign in</Link>
                    <Link to="/resend-verification">Resend verification email</Link>
                    <Link to="/">Back to home</Link>
                </div>
            </div>
        </div>
    );
}
