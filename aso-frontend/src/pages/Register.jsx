import { useState } from "react";
import { api } from "../services/api";

export default function Register() {
    const [vin, setVin] = useState("");
    const [code, setCode] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const register = async () => {
        await api.post("/auth/register", {
            vin,
            securityCode: code,
            email,
            password,
        });

        alert("Zarejestrowano!");
        window.location.href = "/";
    };

    return (
        <div>
            <h1>Rejestracja klienta</h1>

            <input placeholder="VIN" onChange={(e) => setVin(e.target.value)} />
            <input placeholder="Kod bezpieczeństwa" onChange={(e) => setCode(e.target.value)} />
            <input placeholder="Email" onChange={(e) => setEmail(e.target.value)} />
            <input type="password" placeholder="Hasło" onChange={(e) => setPassword(e.target.value)} />

            <button onClick={register}>Zarejestruj</button>
        </div>
    );
}