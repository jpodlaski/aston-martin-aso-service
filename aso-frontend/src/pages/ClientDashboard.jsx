import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChangePasswordForm from "../components/ChangePasswordForm";
import { api } from "../services/api";
import { clearSession, getSession } from "../services/auth";
import { formatCustomerDateTime } from "../utils/dateTime";
import "../components/DashboardLayout.css";

function formatStatus(status) {
    return status === "IN_PROGRESS" ? "IN PROGRESS" : status;
}

function getBookingVehicleId(booking) {
    return booking.vehicle?.id ?? booking.vehicleId;
}

// Open bookings block vehicle removal on both client and server.
function isOpenBooking(status) {
    return status === "SCHEDULED" || status === "IN_PROGRESS";
}

// Client home: list own vehicles & bookings; navigate to add-vehicle / request-service.
// Vehicle removal is blocked while SCHEDULED/IN_PROGRESS bookings exist (server enforces too).
export default function ClientDashboard() {
    const navigate = useNavigate();
    const session = getSession();

    const [vehicles, setVehicles] = useState([]);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [bookingError, setBookingError] = useState("");
    const [vehicleError, setVehicleError] = useState("");
    const [removingVehicleId, setRemovingVehicleId] = useState(null);
    const [cancellingBookingId, setCancellingBookingId] = useState(null);

    const loadData = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            const [vehiclesRes, bookingsRes] = await Promise.all([
                api.get("/vehicles/me"),
                api.get("/customers/me/bookings"),
            ]);
            setVehicles(vehiclesRes.data);
            setBookings(bookingsRes.data);
        } catch {
            setError("Could not load your account data.");
        } finally {
            setLoading(false);
        }
    }, [session?.token]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Vehicles referenced by SCHEDULED or IN_PROGRESS bookings cannot be removed.
    const vehicleIdsWithOpenBookings = useMemo(() => {
        const ids = new Set();
        for (const booking of bookings) {
            if (!isOpenBooking(booking.status)) continue;
            const vehicleId = getBookingVehicleId(booking);
            if (vehicleId != null) ids.add(vehicleId);
        }
        return ids;
    }, [bookings]);

    const logout = () => {
        clearSession();
        navigate("/");
    };

    const removeVehicle = async (vehicle) => {
        if (vehicleIdsWithOpenBookings.has(vehicle.id)) {
            setVehicleError(
                "Cannot remove a vehicle while it has a pending or in-progress booking. " +
                "Cancel or complete the booking first."
            );
            return;
        }

        const confirmed = window.confirm(
            `Remove ${vehicle.modelLine ?? vehicle.model} (${vehicle.vin}) from your account? ` +
            "Use this if you sold the car. Your past service records are kept."
        );
        if (!confirmed) return;

        setVehicleError("");
        setRemovingVehicleId(vehicle.id);

        try {
            await api.delete(`/vehicles/${vehicle.id}`);
            loadData();
        } catch (err) {
            setVehicleError(err.response?.data?.message ?? "Could not remove vehicle.");
        } finally {
            setRemovingVehicleId(null);
        }
    };

    const cancelBooking = async (booking) => {
        const confirmed = window.confirm(`Cancel booking #${booking.id}?`);
        if (!confirmed) return;

        setBookingError("");
        setCancellingBookingId(booking.id);

        try {
            await api.post(`/bookings/${booking.id}/cancel`, {});
            loadData();
        } catch (err) {
            setBookingError(err.response?.data?.message ?? "Could not cancel booking.");
        } finally {
            setCancellingBookingId(null);
        }
    };

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <div>
                    <h1>Client dashboard</h1>
                    <p className="dashboard-user">Signed in as {session?.name}</p>
                </div>
                <button type="button" className="logout-btn" onClick={logout}>Sign out</button>
            </div>

            <section className="section-block">
                <h2>Your vehicles</h2>
                <button
                    type="button"
                    className="primary-btn section-action-btn"
                    onClick={() => navigate("/client/add-vehicle")}
                >
                    Add a vehicle
                </button>
                {loading && <p>Loading…</p>}
                {error && <p className="form-error">{error}</p>}
                {vehicleError && <p className="form-error">{vehicleError}</p>}
                {!loading && vehicles.length === 0 && <p>No vehicles yet.</p>}
                {vehicles.map((vehicle) => {
                    const hasOpenBooking = vehicleIdsWithOpenBookings.has(vehicle.id);
                    return (
                    <div key={vehicle.id} className="vehicle-card">
                        <h4>{vehicle.modelLine ?? vehicle.model}</h4>
                        <p>Model line: {vehicle.modelLine}</p>
                        <p>Production year: {vehicle.productionYear ?? "—"}</p>
                        <p>Body: {vehicle.bodyStyle}</p>
                        <p>Engine: {vehicle.engine} ({vehicle.power})</p>
                        <p>Transmission: {vehicle.transmission}</p>
                        <p>Drivetrain: {vehicle.drivetrain}</p>
                        <p>VIN: {vehicle.vin}</p>
                        <button
                            type="button"
                            className="danger-btn"
                            disabled={removingVehicleId === vehicle.id || hasOpenBooking}
                            onClick={() => removeVehicle(vehicle)}
                        >
                            {removingVehicleId === vehicle.id ? "Removing…" : "Remove from account"}
                        </button>
                        {hasOpenBooking && (
                            <p className="availability-muted vehicle-remove-hint">
                                Cannot remove while a booking is pending or in progress.
                            </p>
                        )}
                    </div>
                    );
                })}
            </section>

            <section className="section-block">
                <h2>Your bookings</h2>
                {vehicles.length === 0 ? (
                    <p className="section-action-btn">Add a vehicle before submitting a service request.</p>
                ) : (
                    <button
                        type="button"
                        className="primary-btn section-action-btn"
                        onClick={() => navigate("/client/request-service")}
                    >
                        Request service
                    </button>
                )}
                {bookingError && <p className="form-error">{bookingError}</p>}
                {bookings.length === 0 && <p>No bookings yet.</p>}
                {bookings.map((booking) => (
                    <div key={booking.id} className="booking-card">
                        <h4>Booking #{booking.id} — {formatStatus(booking.status)}</h4>
                        <p>Vehicle: {booking.carModel}</p>
                        <p>Issue: {booking.customerDescription}</p>
                        {booking.estimatedDropOffTime && (
                            <p>Estimated drop-off: {formatCustomerDateTime(booking.estimatedDropOffTime)}</p>
                        )}
                        {booking.availabilityNotes && <p>Availability: {booking.availabilityNotes}</p>}
                        {booking.scheduledDateTime && (
                            <p>Appointment: {formatCustomerDateTime(booking.scheduledDateTime)}</p>
                        )}
                        {booking.estimatedCost != null && <p>Estimated cost: {booking.estimatedCost} EUR</p>}
                        {booking.finalCost != null && <p>Final cost: {booking.finalCost} EUR</p>}
                        {booking.cancellationReason && (
                            <p>Reason: {booking.cancellationReason}</p>
                        )}
                        {(booking.status === "SCHEDULED" || booking.status === "IN_PROGRESS") && (
                            <button
                                type="button"
                                className="danger-btn"
                                disabled={cancellingBookingId === booking.id}
                                onClick={() => cancelBooking(booking)}
                            >
                                {cancellingBookingId === booking.id ? "Cancelling…" : "Cancel booking"}
                            </button>
                        )}
                    </div>
                ))}
            </section>

            <ChangePasswordForm />
        </div>
    );
}
