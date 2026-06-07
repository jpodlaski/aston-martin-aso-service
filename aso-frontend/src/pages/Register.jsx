import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { saveSession } from "../services/auth";
import "../components/DashboardLayout.css";

// Client self-registration; no VIN required — vehicles are added separately.
export default function Register() {
    const navigate = useNavigate();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const register = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");

        try {
            const res = await api.post("/auth/register", {
                firstName,
                lastName,
                email,
                password,
            });
            saveSession(res.data);
            navigate("/client");
        } catch (err) {
            setError(err.response?.data?.message ?? "Registration failed.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="dashboard">
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
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />
                <button type="submit" disabled={loading}>
                    {loading ? "Creating account…" : "Register"}
                </button>
                {error && <p className="form-error">{error}</p>}
            </form>

            <div className="auth-links">
                <Link to="/">Already have an account? Sign in</Link>
            </div>
        </div>
    );
}
