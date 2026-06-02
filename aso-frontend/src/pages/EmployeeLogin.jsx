import { useState } from "react";
import { api } from "../services/api";

export default function EmployeeLogin() {
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");

    const loginEmployee = async () => {
        const res = await api.post("/auth/employee-login", {
            login,
            password,
        });

        if (res.data.role === "EMPLOYEE") {
            window.location.href = "/employee";
        }
    };

    return (
        <div>
            <h1>Login pracownika</h1>

            <input onChange={(e) => setLogin(e.target.value)} />
            <input type="password" onChange={(e) => setPassword(e.target.value)} />

            <button onClick={loginEmployee}>Wejdź</button>
        </div>
    );
}