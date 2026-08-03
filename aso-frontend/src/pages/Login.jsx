import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { saveSession } from "../services/auth";
import "../components/DashboardLayout.css";

// Client login; email is used as the login field.
export default function Login() {
    const navigate = useNavigate();
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleLogin = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");

        try {
            const res = await api.post("/auth/login", { login, password });
            saveSession(res.data);
            navigate("/client");
        } catch (err) {
            setError(err.response?.data?.message ?? "Login failed.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="dashboard">
            <h1>Client login</h1>
            <p>Sign in with your email and password.</p>

            <form className="auth-form" onSubmit={handleLogin}>
                <input
                    placeholder="Email"
                    type="email"
                    value={login}
                    onChange={(e) => setLogin(e.target.value)}
                    required
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />
                <button type="submit" disabled={loading}>
                    {loading ? "Signing in…" : "Sign in"}
                </button>
                {error && <p className="form-error">{error}</p>}
            </form>

            <div className="auth-links">
                <Link to="/forgot-password">Forgot password?</Link>
                <Link to="/register">Create account</Link>
                <Link to="/employee-login">Employee login</Link>
            </div>
        </div>
    );
}
