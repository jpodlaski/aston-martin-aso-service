import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChangePasswordForm from "../components/ChangePasswordForm";
import { ASSIGNABLE_ROLES, roleLabel } from "../constants/roles";
import { api } from "../services/api";
import { clearSession, getSession } from "../services/auth";
import { formatCustomerDateTime } from "../utils/dateTime";
import "../components/DashboardLayout.css";

function formatBookingStatus(status) {
    return status === "IN_PROGRESS" ? "IN PROGRESS" : status;
}

function formatCancelledBy(value) {
    if (!value) return null;
    const labels = {
        CUSTOMER: "Customer",
        WORKER: "Worker",
        WORKSHOP: "Workshop",
    };
    return labels[value] ?? value;
}

// Worker management and booking history; authorization comes from the JWT.
export default function AdminDashboard() {
    const navigate = useNavigate();
    const session = getSession();

    const [workers, setWorkers] = useState([]);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [bookingsLoading, setBookingsLoading] = useState(true);
    const [error, setError] = useState("");
    const [bookingsError, setBookingsError] = useState("");
    const [formError, setFormError] = useState("");
    const [formSuccess, setFormSuccess] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [updatingRoleId, setUpdatingRoleId] = useState(null);

    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState("MECHANIC");

    const loadWorkers = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            const res = await api.get("/admins/workers");
            setWorkers(res.data);
        } catch {
            setError("Could not load workers.");
        } finally {
            setLoading(false);
        }
    }, []);

    const loadBookings = useCallback(async () => {
        setBookingsLoading(true);
        setBookingsError("");
        try {
            const res = await api.get("/admins/bookings");
            setBookings(res.data);
        } catch {
            setBookingsError("Could not load booking history.");
        } finally {
            setBookingsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadWorkers();
        loadBookings();
    }, [loadWorkers, loadBookings]);

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const createWorker = async (e) => {
        e.preventDefault();
        setFormError("");
        setFormSuccess("");
        setSubmitting(true);

        try {
            await api.post("/admins/workers", {
                firstName,
                lastName,
                email,
                login,
                password,
                role,
            });
            setFormSuccess("Worker account created.");
            setFirstName("");
            setLastName("");
            setEmail("");
            setLogin("");
            setPassword("");
            setRole("MECHANIC");
            loadWorkers();
        } catch (err) {
            setFormError(err.response?.data?.message ?? "Could not create worker.");
        } finally {
            setSubmitting(false);
        }
    };

    const deleteWorker = async (worker) => {
        const confirmed = window.confirm(
            `Delete ${worker.firstName} ${worker.lastName} (${roleLabel(worker.role)})?`
        );
        if (!confirmed) return;

        setDeletingId(worker.id);
        setError("");

        try {
            await api.delete(`/admins/workers/${worker.id}`);
            loadWorkers();
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not delete worker.");
        } finally {
            setDeletingId(null);
        }
    };

    const changeWorkerRole = async (worker, newRole) => {
        if (newRole === worker.role) return;

        setUpdatingRoleId(worker.id);
        setError("");

        try {
            await api.patch(`/admins/workers/${worker.id}/role`, {
                role: newRole,
            });
            loadWorkers();
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not update role.");
        } finally {
            setUpdatingRoleId(null);
        }
    };

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <div>
                    <h1>Management dashboard</h1>
                    <p className="dashboard-user">
                        Signed in as {session?.name} ({roleLabel(session?.role)})
                    </p>
                </div>
                <button type="button" className="logout-btn" onClick={logout}>Sign out</button>
            </div>

            <section className="section-block">
                <h2>Add worker</h2>
                <p>Create a workshop account and assign a role.</p>

                <form className="auth-form" style={{ maxWidth: "100%" }} onSubmit={createWorker}>
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
                        type="email"
                        placeholder="Email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
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
                    <select value={role} onChange={(e) => setRole(e.target.value)} required>
                        {ASSIGNABLE_ROLES.map((option) => (
                            <option key={option} value={option}>
                                {roleLabel(option)}
                            </option>
                        ))}
                    </select>
                    <button type="submit" className="primary-btn" disabled={submitting}>
                        {submitting ? "Creating…" : "Create worker"}
                    </button>
                    {formError && <p className="form-error">{formError}</p>}
                    {formSuccess && <p className="form-success">{formSuccess}</p>}
                </form>
            </section>

            <section className="section-block">
                <h2>Workers</h2>
                {loading && <p>Loading…</p>}
                {error && <p className="form-error">{error}</p>}
                {!loading && workers.length === 0 && <p>No workers yet.</p>}
                {workers.map((worker) => (
                    <div key={worker.id} className="booking-card">
                        <h4>{worker.firstName} {worker.lastName}</h4>
                        <p>Role: {roleLabel(worker.role)}</p>
                        <p>Login: {worker.login}</p>
                        <p>Email: {worker.email}</p>
                        <div className="worker-actions">
                            <select
                                value={worker.role ?? "MECHANIC"}
                                disabled={updatingRoleId === worker.id}
                                onChange={(e) => changeWorkerRole(worker, e.target.value)}
                            >
                                {ASSIGNABLE_ROLES.map((option) => (
                                    <option key={option} value={option}>
                                        {roleLabel(option)}
                                    </option>
                                ))}
                            </select>
                            <button
                                type="button"
                                className="danger-btn"
                                disabled={deletingId === worker.id}
                                onClick={() => deleteWorker(worker)}
                            >
                                {deletingId === worker.id ? "Deleting…" : "Delete"}
                            </button>
                        </div>
                    </div>
                ))}
            </section>

            <section className="section-block">
                <h2>Booking history</h2>
                {bookingsLoading && <p>Loading…</p>}
                {bookingsError && <p className="form-error">{bookingsError}</p>}
                {!bookingsLoading && bookings.length === 0 && <p>No bookings yet.</p>}
                {bookings.map((booking) => (
                    <div key={booking.id} className="booking-card admin-booking-card">
                        <h4>Booking #{booking.id} — {formatBookingStatus(booking.status)}</h4>
                        <p><strong>Customer:</strong> {booking.customerName}</p>
                        {booking.customerEmail && <p><strong>Email:</strong> {booking.customerEmail}</p>}
                        <p><strong>Issue:</strong> {booking.customerDescription}</p>
                        <p><strong>Vehicle:</strong> {booking.modelLine ?? booking.carModel}</p>
                        {booking.vin && <p><strong>VIN:</strong> {booking.vin}</p>}
                        {booking.serviceTypes?.length > 0 && (
                            <p><strong>Services:</strong> {booking.serviceTypes.join(", ")}</p>
                        )}
                        {booking.estimatedDropOffTime && (
                            <p><strong>Preferred drop-off:</strong> {formatCustomerDateTime(booking.estimatedDropOffTime)}</p>
                        )}
                        {booking.availabilityNotes && (
                            <p><strong>Availability:</strong> {booking.availabilityNotes}</p>
                        )}
                        {booking.scheduledDateTime && (
                            <p><strong>Appointment:</strong> {formatCustomerDateTime(booking.scheduledDateTime)}</p>
                        )}
                        {booking.assignedWorkerName && (
                            <p>
                                <strong>Assigned worker:</strong> {booking.assignedWorkerName}
                                {booking.assignedWorkerRole && ` (${roleLabel(booking.assignedWorkerRole)})`}
                            </p>
                        )}
                        {booking.estimatedCost != null && (
                            <p><strong>Estimated cost:</strong> {booking.estimatedCost} EUR</p>
                        )}
                        {booking.finalCost != null && (
                            <p><strong>Final cost:</strong> {booking.finalCost} EUR</p>
                        )}
                        {booking.cancelledBy && (
                            <p><strong>Cancelled by:</strong> {formatCancelledBy(booking.cancelledBy)}</p>
                        )}
                        {booking.cancellationReason && (
                            <p><strong>Cancellation reason:</strong> {booking.cancellationReason}</p>
                        )}
                    </div>
                ))}
            </section>

            <ChangePasswordForm />
        </div>
    );
}
