import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { roleLabel } from "../constants/roles";
import { clearSession, getSession } from "../services/auth";
import {
    formatCustomerDateTime,
    hasCustomerAvailability,
    toDatetimeLocalValue,
} from "../utils/dateTime";
import "../components/DashboardLayout.css";

function formatStatus(status) {
    return status === "IN_PROGRESS" ? "IN PROGRESS" : status;
}

function CustomerAvailability({ booking }) {
    if (!hasCustomerAvailability(booking)) {
        return <p className="availability-muted">No preferred date provided by customer.</p>;
    }

    return (
        <div className="customer-availability">
            <p className="availability-title">Customer availability</p>
            {booking.estimatedDropOffTime && (
                <p>
                    <strong>Preferred drop-off:</strong>{" "}
                    {formatCustomerDateTime(booking.estimatedDropOffTime)}
                </p>
            )}
            {booking.availabilityNotes && (
                <p>
                    <strong>Notes:</strong> {booking.availabilityNotes}
                </p>
            )}
        </div>
    );
}

// Workshop view: claim available bookings, schedule, complete, reject, or cancel.
export default function EmployeeDashboard() {
    const navigate = useNavigate();
    const session = getSession();
    const workerId = session?.id;

    const [available, setAvailable] = useState([]);
    const [assigned, setAssigned] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [actionError, setActionError] = useState("");

    const [claimForms, setClaimForms] = useState({});
    const [rejectForms, setRejectForms] = useState({});
    const [scheduleForms, setScheduleForms] = useState({});
    const [completeForms, setCompleteForms] = useState({});
    const [cancelForms, setCancelForms] = useState({});

    const loadBookings = useCallback(async () => {
        if (!workerId) return;
        setLoading(true);
        setError("");
        try {
            const [availableRes, assignedRes] = await Promise.all([
                api.get("/bookings/available"),
                api.get(`/workers/${workerId}/bookings`),
            ]);
            setAvailable(availableRes.data);
            // Only in-progress bookings appear in the assigned list (completed/cancelled are hidden).
            setAssigned(assignedRes.data.filter((booking) => booking.status === "IN_PROGRESS"));
        } catch {
            setError("Could not load bookings.");
        } finally {
            setLoading(false);
        }
    }, [workerId]);

    useEffect(() => {
        loadBookings();
    }, [loadBookings]);

    // Pre-fill schedule form from customer drop-off preference; never overwrite user input.
    useEffect(() => {
        setScheduleForms((prev) => {
            const next = { ...prev };

            for (const booking of assigned) {
                if (
                    booking.status === "IN_PROGRESS"
                    && !booking.scheduledDateTime
                    && booking.estimatedDropOffTime
                    && !next[booking.id]?.scheduledDateTime
                ) {
                    next[booking.id] = {
                        ...next[booking.id],
                        scheduledDateTime: toDatetimeLocalValue(booking.estimatedDropOffTime),
                    };
                }
            }

            return next;
        });
    }, [assigned]);

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const updateMap = (setter, id, field, value) => {
        setter((prev) => ({
            ...prev,
            [id]: { ...prev[id], [field]: value },
        }));
    };

    const rejectBooking = async (bookingId) => {
        setActionError("");
        const reason = rejectForms[bookingId]?.reason?.trim();
        if (!reason) {
            setActionError("A reason is required to reject a booking.");
            return;
        }

        try {
            await api.post(`/bookings/${bookingId}/reject`, { workerId, reason });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not reject booking.");
        }
    };

    const cancelBooking = async (bookingId) => {
        setActionError("");
        const reason = cancelForms[bookingId]?.reason?.trim();
        if (!reason) {
            setActionError("A reason is required when cancelling a booking.");
            return;
        }

        try {
            await api.post(`/bookings/${bookingId}/cancel`, { workerId, reason });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not cancel booking.");
        }
    };

    const claimBooking = async (bookingId) => {
        setActionError("");
        const form = claimForms[bookingId] ?? {};
        const costValue = form.estimatedCost?.toString().trim();
        const estimatedCost = costValue ? Number(costValue) : null;
        try {
            await api.post(`/bookings/${bookingId}/claim`, {
                workerId,
                estimatedCost: Number.isFinite(estimatedCost) ? estimatedCost : null,
                serviceTypes: (form.serviceTypes ?? "")
                    .split(",")
                    .map((s) => s.trim())
                    .filter(Boolean),
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not claim booking.");
        }
    };

    const scheduleBooking = async (bookingId) => {
        setActionError("");
        const form = scheduleForms[bookingId] ?? {};
        try {
            await api.post(`/bookings/${bookingId}/schedule`, {
                workerId,
                scheduledDateTime: form.scheduledDateTime ? `${form.scheduledDateTime}:00` : undefined,
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not schedule booking.");
        }
    };

    const completeBooking = async (bookingId) => {
        setActionError("");
        const form = completeForms[bookingId] ?? {};
        try {
            await api.post(`/bookings/${bookingId}/complete`, {
                workerId,
                finalCost: Number(form.finalCost),
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not complete booking.");
        }
    };

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <div>
                    <h1>Employee dashboard</h1>
                    <p className="dashboard-user">
                        Signed in as {session?.name} ({roleLabel(session?.role)})
                    </p>
                </div>
                <button type="button" className="logout-btn" onClick={logout}>Sign out</button>
            </div>

            {loading && <p>Loading…</p>}
            {error && <p className="form-error">{error}</p>}
            {actionError && <p className="form-error">{actionError}</p>}

            <section className="section-block">
                <h2>Available bookings</h2>
                {available.length === 0 && !loading && <p>No unclaimed bookings.</p>}
                {available.map((booking) => (
                    <div key={booking.id} className="booking-card">
                        <h4>Booking #{booking.id}</h4>
                        <p>Customer: {booking.customerName}</p>
                        <p>Vehicle: {booking.carModel}</p>
                        <p>Issue: {booking.customerDescription}</p>
                        <CustomerAvailability booking={booking} />
                        <div className="inline-form">
                            <input
                                placeholder="Estimated cost (EUR, optional)"
                                type="number"
                                min="0"
                                step="0.01"
                                value={claimForms[booking.id]?.estimatedCost ?? ""}
                                onChange={(e) => updateMap(setClaimForms, booking.id, "estimatedCost", e.target.value)}
                            />
                            <input
                                placeholder="Services (comma-separated)"
                                value={claimForms[booking.id]?.serviceTypes ?? ""}
                                onChange={(e) => updateMap(setClaimForms, booking.id, "serviceTypes", e.target.value)}
                            />
                            <button type="button" className="primary-btn" onClick={() => claimBooking(booking.id)}>
                                Claim
                            </button>
                        </div>
                        <div className="inline-form reject-form">
                            <input
                                placeholder="Reason for rejection (required)"
                                value={rejectForms[booking.id]?.reason ?? ""}
                                onChange={(e) => updateMap(setRejectForms, booking.id, "reason", e.target.value)}
                            />
                            <button type="button" className="danger-btn" onClick={() => rejectBooking(booking.id)}>
                                Reject
                            </button>
                        </div>
                    </div>
                ))}
            </section>

            <section className="section-block">
                <h2>My assignments</h2>
                {assigned.length === 0 && !loading && <p>No assigned bookings.</p>}
                {assigned.map((booking) => (
                    <div key={booking.id} className="booking-card">
                        <h4>Booking #{booking.id} — {formatStatus(booking.status)}</h4>
                        <p>Customer: {booking.customerName}</p>
                        <p>Vehicle: {booking.carModel}</p>
                        <p>Issue: {booking.customerDescription}</p>
                        {booking.serviceTypes?.length > 0 && (
                            <p>Services: {booking.serviceTypes.join(", ")}</p>
                        )}
                        {booking.estimatedCost != null && <p>Estimated cost: {booking.estimatedCost} EUR</p>}
                        <CustomerAvailability booking={booking} />
                        {booking.scheduledDateTime && (
                            <p><strong>Confirmed appointment:</strong> {formatCustomerDateTime(booking.scheduledDateTime)}</p>
                        )}

                        {booking.status === "IN_PROGRESS" && !booking.scheduledDateTime && (
                            <div className="schedule-block">
                                <p className="schedule-hint">
                                    Confirm appointment after calling the customer. The date below is pre-filled from their preferred drop-off.
                                </p>
                                <div className="inline-form">
                                    <input
                                        type="datetime-local"
                                        value={scheduleForms[booking.id]?.scheduledDateTime ?? ""}
                                        onChange={(e) => updateMap(setScheduleForms, booking.id, "scheduledDateTime", e.target.value)}
                                    />
                                    <button type="button" className="primary-btn" onClick={() => scheduleBooking(booking.id)}>
                                        Confirm appointment
                                    </button>
                                </div>
                            </div>
                        )}

                        {booking.status === "IN_PROGRESS" && (
                            <div className="inline-form">
                                <input
                                    placeholder="Final cost (EUR)"
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    value={completeForms[booking.id]?.finalCost ?? ""}
                                    onChange={(e) => updateMap(setCompleteForms, booking.id, "finalCost", e.target.value)}
                                />
                                <button type="button" className="primary-btn" onClick={() => completeBooking(booking.id)}>
                                    Complete
                                </button>
                            </div>
                        )}

                        {booking.status === "IN_PROGRESS" && (
                            <div className="inline-form reject-form">
                                <input
                                    placeholder="Reason for cancellation (required)"
                                    value={cancelForms[booking.id]?.reason ?? ""}
                                    onChange={(e) => updateMap(setCancelForms, booking.id, "reason", e.target.value)}
                                />
                                <button type="button" className="danger-btn" onClick={() => cancelBooking(booking.id)}>
                                    Cancel booking
                                </button>
                            </div>
                        )}
                    </div>
                ))}
            </section>
        </div>
    );
}
