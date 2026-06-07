import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { getSession } from "../services/auth";
import "../components/DashboardLayout.css";

// Creates a SCHEDULED booking; at least drop-off time or availability notes required by API.
export default function RequestService() {
    const navigate = useNavigate();
    const customerId = getSession()?.id;

    const [vehicles, setVehicles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [vehicleId, setVehicleId] = useState("");
    const [description, setDescription] = useState("");
    const [dropOffTime, setDropOffTime] = useState("");
    const [availabilityNotes, setAvailabilityNotes] = useState("");

    const loadVehicles = useCallback(async () => {
        if (!customerId) return;
        setLoading(true);
        setError("");
        try {
            const res = await api.get(`/vehicles/customer/${customerId}`);
            setVehicles(res.data);
            setVehicleId((current) => current || (res.data[0] ? String(res.data[0].id) : ""));
        } catch {
            setError("Could not load your vehicles.");
        } finally {
            setLoading(false);
        }
    }, [customerId]);

    useEffect(() => {
        loadVehicles();
    }, [loadVehicles]);

    const submitBooking = async (e) => {
        e.preventDefault();
        setError("");
        setSubmitting(true);

        try {
            await api.post("/bookings", {
                vehicleId: Number(vehicleId),
                customerDescription: description,
                // Append seconds so the value matches LocalDateTime ISO format expected by the API.
                estimatedDropOffTime: dropOffTime ? `${dropOffTime}:00` : undefined,
                availabilityNotes: availabilityNotes || undefined,
            });
            navigate("/client");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not create booking.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <div>
                    <h1>Request service</h1>
                </div>
                <button type="button" className="logout-btn" onClick={() => navigate("/client")}>
                    Back to dashboard
                </button>
            </div>

            {loading && <p>Loading…</p>}

            {!loading && vehicles.length === 0 && (
                <p>Add a vehicle to your account before submitting a service request.</p>
            )}

            {!loading && vehicles.length > 0 && (
                <form className="auth-form" style={{ maxWidth: "100%" }} onSubmit={submitBooking}>
                    <div className="form-field">
                        <select value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} required>
                            {vehicles.map((v) => (
                                <option key={v.id} value={v.id}>
                                    {v.modelLine ?? v.model} ({v.vin})
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="form-field">
                        <textarea
                            placeholder="Describe the issue"
                            rows={3}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            required
                        />
                    </div>
                    <div className="form-field">
                        <input
                            type="datetime-local"
                            value={dropOffTime}
                            onChange={(e) => setDropOffTime(e.target.value)}
                        />
                    </div>
                    <div className="form-field">
                        <input
                            placeholder="Availability notes (e.g. weekday mornings)"
                            value={availabilityNotes}
                            onChange={(e) => setAvailabilityNotes(e.target.value)}
                        />
                    </div>
                    <p><small>Provide estimated drop-off time and/or availability notes.</small></p>
                    <button type="submit" className="primary-btn" disabled={submitting}>
                        {submitting ? "Submitting…" : "Submit request"}
                    </button>
                    {error && <p className="form-error">{error}</p>}
                </form>
            )}
        </div>
    );
}
