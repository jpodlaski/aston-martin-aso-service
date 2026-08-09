import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import DropOffPicker from "../../components/DropOffPicker";
import { api } from "../../services/api";
import { getSession } from "../../services/auth";
import "../../components/DashboardLayout.css";
import "./ClientDashboard.css";
import "./AddVehicle.css";
import "./RequestService.css";

const MAX_DESCRIPTION_WORDS = 500;

const DROPOFF_REQUIRED_MESSAGE =
    "Choose an estimated drop-off date and time so we know when to expect the car.";

const PARTIAL_DROPOFF_MESSAGE =
    "Choose both a drop-off date and a time.";

function wordCount(text) {
    const trimmed = text.trim();
    if (!trimmed) return 0;
    return trimmed.split(/\s+/).length;
}

function truncateToMaxWords(text, maxWords) {
    const trimmedEnd = text.replace(/\s+$/, "");
    if (wordCount(trimmedEnd) <= maxWords) return text;
    const words = trimmedEnd.trim().split(/\s+/).slice(0, maxWords);
    const endsWithWhitespace = /\s$/.test(text);
    return words.join(" ") + (endsWithWhitespace ? " " : "");
}

function friendlyBookingError(message) {
    if (!message) return "Could not create booking.";
    if (/estimatedDropOffTime/i.test(message)) {
        return DROPOFF_REQUIRED_MESSAGE;
    }
    return message;
}

// Creates a SCHEDULED booking; estimated drop-off date and time are required.
export default function RequestService() {
    const navigate = useNavigate();
    const session = getSession();

    const [vehicles, setVehicles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [scheduleError, setScheduleError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [vehicleId, setVehicleId] = useState("");
    const [description, setDescription] = useState("");
    const [dropOffDate, setDropOffDate] = useState("");
    const [dropOffClock, setDropOffClock] = useState("");
    const [availabilityNotes, setAvailabilityNotes] = useState("");

    const descriptionWords = useMemo(() => wordCount(description), [description]);

    const loadVehicles = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            const res = await api.get("/vehicles/me");
            setVehicles(res.data);
            setVehicleId((current) => current || (res.data[0] ? String(res.data[0].id) : ""));
        } catch {
            setError("Could not load your vehicles.");
        } finally {
            setLoading(false);
        }
    }, [session?.token]);

    useEffect(() => {
        loadVehicles();
    }, [loadVehicles]);

    const submitBooking = async (e) => {
        e.preventDefault();
        setError("");
        setScheduleError("");

        if (!dropOffDate && !dropOffClock) {
            setScheduleError(DROPOFF_REQUIRED_MESSAGE);
            return;
        }

        if (!dropOffDate || !dropOffClock) {
            setScheduleError(PARTIAL_DROPOFF_MESSAGE);
            return;
        }

        setSubmitting(true);

        try {
            await api.post("/bookings", {
                vehicleId: Number(vehicleId),
                customerDescription: description,
                estimatedDropOffTime: `${dropOffDate}T${dropOffClock}:00`,
                availabilityNotes: availabilityNotes.trim() || undefined,
            });
            navigate("/client");
        } catch (err) {
            const apiMessage = err.response?.data?.message;
            const message = friendlyBookingError(apiMessage);
            if (/drop-off/i.test(message) || /estimatedDropOffTime/i.test(apiMessage ?? "")) {
                setScheduleError(message);
            } else {
                setError(message);
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="client-dashboard-shell">
            <div
                className="client-dashboard-bg"
                aria-hidden="true"
            />
            <div className="client-dashboard-veil" aria-hidden="true" />

            <div className="dashboard client-dashboard add-vehicle-page request-service-page">
                <div className="dashboard-header">
                    <div>
                        <img className="logo" src="/aston-martin-logo.png" alt="Aston Martin" />
                        <h1>Request service</h1>
                    </div>
                    <button
                        type="button"
                        className="logout-btn"
                        onClick={() => navigate("/client")}
                    >
                        Back to dashboard
                    </button>
                </div>

                {loading && <p className="request-service-status">Loading…</p>}

                {!loading && vehicles.length === 0 && (
                    <div className="request-service-empty">
                        <p>Add a vehicle to your account before submitting a service request.</p>
                        <button
                            type="button"
                            className="submit-vehicle-btn"
                            onClick={() => navigate("/client/add-vehicle")}
                        >
                            Add a vehicle
                        </button>
                    </div>
                )}

                {!loading && vehicles.length > 0 && (
                    <form className="request-service-form" onSubmit={submitBooking} noValidate>
                        <label className="request-field">
                            <span className="request-label">Vehicle</span>
                            <select
                                value={vehicleId}
                                onChange={(e) => setVehicleId(e.target.value)}
                                required
                            >
                                {vehicles.map((v) => (
                                    <option key={v.id} value={v.id}>
                                        {v.modelLine ?? v.model}
                                        {v.productionYear ? ` · ${v.productionYear}` : ""}
                                        {" · "}
                                        {v.vin}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className="request-field">
                            <span className="request-label">Describe the issue</span>
                            <textarea
                                placeholder="What should the workshop look at?"
                                rows={4}
                                value={description}
                                maxLength={2000}
                                onChange={(e) => {
                                    setDescription(
                                        truncateToMaxWords(e.target.value, MAX_DESCRIPTION_WORDS)
                                    );
                                }}
                                required
                            />
                            <span className={`request-word-count${descriptionWords >= MAX_DESCRIPTION_WORDS ? " at-limit" : ""}`}>
                                {descriptionWords} / {MAX_DESCRIPTION_WORDS} words
                            </span>
                        </label>

                        <fieldset
                            className={`request-field request-dropoff${scheduleError ? " has-error" : ""}`}
                        >
                            <legend className="request-label">Estimated drop-off</legend>
                            <DropOffPicker
                                dateValue={dropOffDate}
                                timeValue={dropOffClock}
                                onDateChange={(next) => {
                                    setDropOffDate(next);
                                    setDropOffClock("");
                                    setScheduleError("");
                                }}
                                onTimeChange={(next) => {
                                    setDropOffClock(next);
                                    setScheduleError("");
                                }}
                                startHour={6}
                                endHour={20}
                            />
                        </fieldset>

                        <label className="request-field">
                            <span className="request-label">Availability notes</span>
                            <input
                                placeholder="Optional — e.g. call if running late"
                                value={availabilityNotes}
                                onChange={(e) => setAvailabilityNotes(e.target.value)}
                            />
                        </label>

                        <p className="request-hint">
                            A drop-off date and time are required. Notes are optional.
                        </p>

                        {scheduleError && (
                            <p className="request-schedule-error" role="alert">
                                {scheduleError}
                            </p>
                        )}

                        <div className="request-actions">
                            <button
                                type="submit"
                                className="submit-vehicle-btn"
                                disabled={submitting}
                            >
                                {submitting ? "Submitting…" : "Submit request"}
                            </button>
                        </div>

                        {error && <p className="config-error" role="alert">{error}</p>}
                    </form>
                )}

                {!session?.token && (
                    <p className="config-error">You must be signed in to request service.</p>
                )}
            </div>
        </div>
    );
}
