import { useState } from "react";
import { api } from "../services/api";

export default function Login() {
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");

    const handleLogin = async () => {
        const res = await api.post("/auth/login", {
            login,
            password,
        });

        const role = res.data.role;

        if (role === "CLIENT") {
            window.location.href = "/client";
        } else {
            window.location.href = "/employee";
        }
    };

    return (
        <div>
            <h1>Login</h1>

            <input placeholder="Login" onChange={(e) => setLogin(e.target.value)} />
            <input type="password" onChange={(e) => setPassword(e.target.value)} />

            <button onClick={handleLogin}>Zaloguj</button>

            <a href="/register">Rejestracja klienta</a>
            <a href="/employee-login">Pracownik</a>
        </div>
    );
}