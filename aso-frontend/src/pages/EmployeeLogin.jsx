import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { getDashboardPathForRole, saveSession } from "../services/auth";
import "../components/DashboardLayout.css";

// Staff login; role in the response routes to /admin or /employee dashboard.
export default function EmployeeLogin() {
    const navigate = useNavigate();
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const loginEmployee = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");

        try {
            const res = await api.post("/auth/employee-login", { login, password });
            saveSession(res.data);
            navigate(getDashboardPathForRole(res.data.role));
        } catch (err) {
            setError(err.response?.data?.message ?? "Login failed.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="dashboard">
            <h1>Employee login</h1>

            <form className="auth-form" onSubmit={loginEmployee}>
                <input
                    placeholder="Login"
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
                <Link to="/">Client login</Link>
            </div>
        </div>
    );
}
