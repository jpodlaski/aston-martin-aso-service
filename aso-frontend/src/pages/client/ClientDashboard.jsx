import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChangePasswordForm from "../../components/ChangePasswordForm";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import { formatCustomerDateTime } from "../../utils/dateTime";
import "../../components/DashboardLayout.css";
import "./ClientDashboard.css";

const SECTIONS = {
    vehicles: "Vehicles",
    bookings: "Bookings",
    archive: "Archive",
    account: "Account management",
};

function formatStatus(status) {
    if (!status) return "";
    return String(status).replaceAll("_", " ");
}

function isWorkshopCancellation(booking) {
    if (booking?.cancelledBy === "WORKER" || booking?.cancelledBy === "WORKSHOP") {
        return true;
    }
    // Older rows may only have a reason from workshop reject/cancel.
    return Boolean(booking?.cancellationReason?.trim()) && booking?.cancelledBy !== "CUSTOMER";
}

function cancellationSourceLabel(cancelledBy) {
    if (cancelledBy === "CUSTOMER") return "You";
    if (cancelledBy === "WORKER" || cancelledBy === "WORKSHOP") return "Workshop";
    return null;
}

function getBookingVehicleId(booking) {
    return booking.vehicle?.id ?? booking.vehicleId;
}

function formatVehicleOption(vehicle) {
    return [
        vehicle.modelLine ?? vehicle.model,
        vehicle.productionYear ? String(vehicle.productionYear) : null,
        vehicle.vin,
    ].filter(Boolean).join(" · ");
}

function VehicleDetails({ vehicle }) {
    return (
        <div className="booking-detail-rows">
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Model</span>
                    <p>{vehicle.modelLine ?? vehicle.model ?? "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Production year</span>
                    <p>{vehicle.productionYear ?? "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Body</span>
                    <p>{vehicle.bodyStyle ?? "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">VIN</span>
                    <p>{vehicle.vin ?? "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Engine</span>
                    <p>
                        {vehicle.engine
                            ? `${vehicle.engine}${vehicle.power ? ` (${vehicle.power})` : ""}`
                            : "—"}
                    </p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Transmission</span>
                    <p>
                        {vehicle.transmission
                            ? `${vehicle.transmission}${vehicle.drivetrain ? ` · ${vehicle.drivetrain}` : ""}`
                            : "—"}
                    </p>
                </div>
            </div>
        </div>
    );
}

function getBookingVin(booking, vehicles = []) {
    if (booking.vehicle?.vin) return booking.vehicle.vin;
    if (booking.vin) return booking.vin;
    const vehicleId = getBookingVehicleId(booking);
    if (vehicleId == null) return null;
    return vehicles.find((vehicle) => vehicle.id === vehicleId)?.vin ?? null;
}

// Open bookings block vehicle removal on both client and server.
function isOpenBooking(status) {
    return status === "SCHEDULED"
        || status === "READY_FOR_WORK"
        || status === "IN_PROGRESS";
}

function isArchiveStatus(status) {
    return status === "COMPLETED" || status === "CANCELLED";
}

function clientArchiveMatchesQuery(booking, query, vehicles) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const vin = getBookingVin(booking, vehicles);
    const cancelledBy = cancellationSourceLabel(booking.cancelledBy);
    const haystack = [
        booking.carModel,
        vin,
        booking.status,
        formatStatus(booking.status),
        booking.cancellationReason,
        cancelledBy,
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
    return haystack.includes(q);
}

// Client home: one section at a time via header menu (Vehicles / Bookings / Account).
export default function ClientDashboard() {
    const navigate = useNavigate();
    const session = getSession();
    const menuRef = useRef(null);

    const [section, setSection] = useState("vehicles");
    const [menuOpen, setMenuOpen] = useState(false);
    const [showPasswordForm, setShowPasswordForm] = useState(false);
    const [showDeleteAccount, setShowDeleteAccount] = useState(false);
    const [deleteAccountMessage, setDeleteAccountMessage] = useState("");
    const [deleteAccountError, setDeleteAccountError] = useState("");
    const [deleteAccountLoading, setDeleteAccountLoading] = useState(false);

    const [vehicles, setVehicles] = useState([]);
    const [vehicleHistory, setVehicleHistory] = useState([]);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [bookingError, setBookingError] = useState("");
    const [vehicleError, setVehicleError] = useState("");
    const [removingVehicleId, setRemovingVehicleId] = useState(null);
    const [cancellingBookingId, setCancellingBookingId] = useState(null);
    const [downloadingInvoiceId, setDownloadingInvoiceId] = useState(null);
    const [bookingVehicleFilter, setBookingVehicleFilter] = useState("");
    const [archiveQuery, setArchiveQuery] = useState("");
    const [showVehicleHistory, setShowVehicleHistory] = useState(false);
    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [expandedVehicleId, setExpandedVehicleId] = useState(null);
    const [cancelForms, setCancelForms] = useState({});

    const loadData = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            const [vehiclesRes, historyRes, bookingsRes] = await Promise.all([
                api.get("/vehicles/me"),
                api.get("/vehicles/me/history"),
                api.get("/customers/me/bookings"),
            ]);
            setVehicles(vehiclesRes.data);
            setVehicleHistory(historyRes.data);
            setBookings(
                [...bookingsRes.data].sort((a, b) => Number(b.id) - Number(a.id))
            );
        } catch {
            setError("Could not load your account data.");
        } finally {
            setLoading(false);
        }
    }, [session?.token]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    useEffect(() => {
        if (!menuOpen) return undefined;

        const onPointerDown = (event) => {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                setMenuOpen(false);
            }
        };
        const onKeyDown = (event) => {
            if (event.key === "Escape") setMenuOpen(false);
        };

        document.addEventListener("pointerdown", onPointerDown);
        document.addEventListener("keydown", onKeyDown);
        return () => {
            document.removeEventListener("pointerdown", onPointerDown);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, [menuOpen]);

    const vehicleIdsWithOpenBookings = useMemo(() => {
        const ids = new Set();
        for (const booking of bookings) {
            if (!isOpenBooking(booking.status)) continue;
            const vehicleId = getBookingVehicleId(booking);
            if (vehicleId != null) ids.add(vehicleId);
        }
        return ids;
    }, [bookings]);

    const filteredBookings = useMemo(() => {
        if (!bookingVehicleFilter) return bookings;
        const selectedId = Number(bookingVehicleFilter);
        return bookings.filter((booking) => getBookingVehicleId(booking) === selectedId);
    }, [bookings, bookingVehicleFilter]);

    const activeBookings = useMemo(
        () => filteredBookings.filter((booking) => isOpenBooking(booking.status)),
        [filteredBookings]
    );

    const allKnownVehicles = useMemo(
        () => [...vehicles, ...vehicleHistory],
        [vehicles, vehicleHistory]
    );

    const archiveBookings = useMemo(
        () => bookings
            .filter((booking) => isArchiveStatus(booking.status))
            .filter((booking) => clientArchiveMatchesQuery(booking, archiveQuery, allKnownVehicles)),
        [bookings, archiveQuery, allKnownVehicles]
    );

    useEffect(() => {
        if (!bookingVehicleFilter) return;
        const selectedId = Number(bookingVehicleFilter);
        if (!vehicles.some((vehicle) => vehicle.id === selectedId)) {
            setBookingVehicleFilter("");
        }
    }, [vehicles, bookingVehicleFilter]);

    const logout = () => {
        clearSession();
        navigate("/");
    };

    const selectSection = (next) => {
        setSection(next);
        setMenuOpen(false);
        if (next !== "archive") setArchiveQuery("");
        if (next !== "account") {
            setShowPasswordForm(false);
            setShowDeleteAccount(false);
            setDeleteAccountMessage("");
            setDeleteAccountError("");
        }
    };

    const requestAccountDeletion = async () => {
        setDeleteAccountError("");
        setDeleteAccountMessage("");
        setDeleteAccountLoading(true);
        try {
            const res = await api.post(
                "/auth/request-account-deletion",
                {},
                { skipAuthRedirect: true }
            );
            setDeleteAccountMessage(
                res.data?.message
                ?? "Check your email to confirm account deletion."
            );
        } catch (err) {
            setDeleteAccountError(
                err.response?.data?.message ?? "Could not start account deletion."
            );
        } finally {
            setDeleteAccountLoading(false);
        }
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
            "Your past service records are kept."
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
        const reason = cancelForms[booking.id]?.reason?.trim();
        if (!reason) {
            setExpandedBookingId(booking.id);
            setBookingError("A reason is required to cancel a booking.");
            return;
        }

        setBookingError("");
        setCancellingBookingId(booking.id);

        try {
            await api.post(`/bookings/${booking.id}/cancel`, { reason });
            setCancelForms((prev) => {
                const next = { ...prev };
                delete next[booking.id];
                return next;
            });
            loadData();
        } catch (err) {
            setBookingError(err.response?.data?.message ?? "Could not cancel booking.");
        } finally {
            setCancellingBookingId(null);
        }
    };

    const downloadInvoice = async (booking) => {
        setBookingError("");
        setDownloadingInvoiceId(booking.id);
        try {
            const res = await api.get(`/bookings/${booking.id}/invoice`, {
                responseType: "blob",
            });
            const url = window.URL.createObjectURL(res.data);
            const link = document.createElement("a");
            link.href = url;
            link.download = `invoice-booking-${booking.id}.pdf`;
            link.click();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            let message = "Could not download invoice.";
            const data = err.response?.data;
            if (data instanceof Blob) {
                try {
                    const text = await data.text();
                    const parsed = JSON.parse(text);
                    if (parsed?.message) message = parsed.message;
                } catch {
                    // keep default message
                }
            } else if (err.response?.data?.message) {
                message = err.response.data.message;
            }
            setBookingError(message);
        } finally {
            setDownloadingInvoiceId(null);
        }
    };

    const renderBookingCard = (booking, { archive = false } = {}) => {
        const vin = getBookingVin(booking, allKnownVehicles);
        const expandedKey = archive ? `archive-${booking.id}` : booking.id;
        const expanded = expandedBookingId === expandedKey;
        const canCancel = isOpenBooking(booking.status);
        const statusClass = `booking-status booking-status-${String(booking.status || "")
            .toLowerCase()
            .replaceAll("_", "-")}`;
        const showCollapsedBadge = !archive && (
            booking.status === "IN_PROGRESS" || booking.status === "READY_FOR_WORK"
        );
        const rowClass = booking.status === "IN_PROGRESS" && !archive
            ? "booking-row booking-row-active"
            : "booking-row";

        return (
            <div key={booking.id} className={rowClass}>
                <div className="booking-row-top">
                    <button
                        type="button"
                        className="booking-row-toggle"
                        aria-expanded={expanded}
                        onClick={() =>
                            setExpandedBookingId((current) =>
                                current === expandedKey ? null : expandedKey
                            )
                        }
                    >
                        <div className="booking-row-heading">
                            <p className="booking-row-title">
                                {booking.carModel}
                            </p>
                            {showCollapsedBadge && (
                                <span className={statusClass}>
                                    {formatStatus(booking.status)}
                                </span>
                            )}
                            <span className="booking-row-chevron" aria-hidden="true">
                                {expanded ? "▾" : "▸"}
                            </span>
                        </div>
                        <p className="booking-row-meta">
                            {vin ? `VIN ${vin}` : "VIN —"}
                        </p>
                    </button>
                </div>

                {expanded && (
                    <div className="booking-row-details">
                        <div className="booking-detail-rows">
                            <div className="booking-detail-row">
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Booking
                                    </span>
                                    <p>#{booking.id}</p>
                                </div>
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Status
                                    </span>
                                    <p>
                                        <span className={statusClass}>
                                            {formatStatus(booking.status)}
                                        </span>
                                    </p>
                                </div>
                            </div>

                            <div className="booking-detail-row">
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Estimated drop-off
                                    </span>
                                    <p>
                                        {booking.estimatedDropOffTime
                                            ? formatCustomerDateTime(booking.estimatedDropOffTime)
                                            : "—"}
                                    </p>
                                </div>
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Appointment
                                    </span>
                                    <p>
                                        {booking.scheduledDateTime
                                            ? formatCustomerDateTime(booking.scheduledDateTime)
                                            : "—"}
                                    </p>
                                </div>
                            </div>

                            <div className="booking-detail-row">
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Issue
                                    </span>
                                    <p>{booking.customerDescription || "—"}</p>
                                </div>
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Work planned
                                    </span>
                                    <p>
                                        {booking.serviceTypes?.length
                                            ? booking.serviceTypes.join(", ")
                                            : "—"}
                                    </p>
                                </div>
                            </div>

                            <div className="booking-detail-row">
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Estimated cost
                                    </span>
                                    <p>
                                        {booking.estimatedCost != null
                                            ? `${booking.estimatedCost} EUR`
                                            : "—"}
                                    </p>
                                </div>
                                <div className="booking-detail-item">
                                    <span className="booking-detail-label">
                                        Final cost
                                    </span>
                                    <p>
                                        {booking.finalCost != null
                                            ? `${booking.finalCost} EUR`
                                            : "—"}
                                    </p>
                                </div>
                            </div>

                            {booking.availabilityNotes && (
                                <div className="booking-detail-row">
                                    <div className="booking-detail-item">
                                        <span className="booking-detail-label">
                                            Availability notes
                                        </span>
                                        <p>{booking.availabilityNotes}</p>
                                    </div>
                                </div>
                            )}

                            {booking.status === "CANCELLED" && (
                                <div className="booking-detail-row">
                                    <div className="booking-detail-item">
                                        <span className="booking-detail-label">
                                            Cancelled by
                                        </span>
                                        <p>
                                            {cancellationSourceLabel(booking.cancelledBy)
                                                ?? (isWorkshopCancellation(booking)
                                                    ? "Workshop"
                                                    : "—")}
                                        </p>
                                    </div>
                                    <div className="booking-detail-item">
                                        <span className="booking-detail-label">
                                            Cancellation reason
                                        </span>
                                        <p>
                                            {booking.cancellationReason?.trim()
                                                || "—"}
                                        </p>
                                    </div>
                                </div>
                            )}
                        </div>

                        {booking.status === "COMPLETED" && (
                            <div className="booking-expanded-actions">
                                <button
                                    type="button"
                                    className="logout-btn invoice-download-btn"
                                    disabled={downloadingInvoiceId === booking.id}
                                    onClick={() => downloadInvoice(booking)}
                                >
                                    {downloadingInvoiceId === booking.id
                                        ? "Downloading…"
                                        : "Invoice"}
                                </button>
                            </div>
                        )}

                        {canCancel && (
                            <div className="booking-cancel-panel">
                                <label className="booking-cancel-field">
                                    <span className="booking-detail-label">
                                        Cancellation reason
                                    </span>
                                    <input
                                        placeholder="Required to cancel"
                                        value={
                                            cancelForms[booking.id]
                                                ?.reason ?? ""
                                        }
                                        onChange={(e) =>
                                            setCancelForms((prev) => ({
                                                ...prev,
                                                [booking.id]: {
                                                    ...prev[booking.id],
                                                    reason: e.target.value,
                                                },
                                            }))
                                        }
                                    />
                                </label>
                                <button
                                    type="button"
                                    className="danger-btn"
                                    disabled={cancellingBookingId === booking.id}
                                    onClick={() => cancelBooking(booking)}
                                >
                                    {cancellingBookingId === booking.id
                                        ? "Cancelling…"
                                        : "Cancel booking"}
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        );
    };

    return (
        <div className="client-dashboard-shell">
            <div
                className="client-dashboard-bg"
                aria-hidden="true"
            />
            <div className="client-dashboard-veil" aria-hidden="true" />

            <div className="dashboard client-dashboard">
                <div className="dashboard-header">
                    <div>
                        <img className="logo" src="/aston-martin-logo.png" alt="Aston Martin" />
                        <p className="dashboard-user">Signed in as {session?.name}</p>
                    </div>

                    <div className="dashboard-header-actions">
                        <div className="menu" ref={menuRef}>
                            <button
                                type="button"
                                className="menu-btn"
                                aria-label="Open menu"
                                aria-expanded={menuOpen}
                                aria-haspopup="menu"
                                onClick={() => setMenuOpen((open) => !open)}
                            >
                                <span />
                                <span />
                                <span />
                            </button>

                            {menuOpen && (
                                <div className="menu-panel" role="menu">
                                    {Object.entries(SECTIONS).map(([id, label]) => (
                                        <button
                                            key={id}
                                            type="button"
                                            role="menuitem"
                                            className={
                                                section === id
                                                    ? "menu-item menu-item-active"
                                                    : "menu-item"
                                            }
                                            onClick={() => selectSection(id)}
                                        >
                                            {label}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <button type="button" className="logout-btn" onClick={logout}>
                            Sign out
                        </button>
                    </div>
                </div>

                {section === "vehicles" && (
                    <section className="section-block dashboard-panel">
                        <h2>Vehicles</h2>
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
                        <div className="vehicle-list">
                            {vehicles.map((vehicle) => {
                                const hasOpenBooking = vehicleIdsWithOpenBookings.has(vehicle.id);
                                const expanded = expandedVehicleId === vehicle.id;
                                return (
                                    <div key={vehicle.id} className="vehicle-row">
                                        <div className="vehicle-row-top">
                                            <button
                                                type="button"
                                                className="vehicle-row-toggle"
                                                aria-expanded={expanded}
                                                onClick={() =>
                                                    setExpandedVehicleId((current) =>
                                                        current === vehicle.id ? null : vehicle.id
                                                    )
                                                }
                                            >
                                                <div className="vehicle-row-heading">
                                                    <p className="vehicle-row-title">
                                                        {vehicle.modelLine ?? vehicle.model}
                                                    </p>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="vehicle-row-meta">VIN {vehicle.vin}</p>
                                            </button>
                                        </div>
                                        {expanded && (
                                            <div className="vehicle-row-details">
                                                <VehicleDetails vehicle={vehicle} />
                                                {hasOpenBooking && (
                                                    <p className="vehicle-remove-note">
                                                        Cannot remove while a booking is pending or in progress.
                                                    </p>
                                                )}
                                                <div className="vehicle-row-expanded-actions">
                                                    <button
                                                        type="button"
                                                        className="danger-btn vehicle-row-action"
                                                        disabled={
                                                            removingVehicleId === vehicle.id
                                                            || hasOpenBooking
                                                        }
                                                        onClick={() => removeVehicle(vehicle)}
                                                    >
                                                        {removingVehicleId === vehicle.id
                                                            ? "Removing…"
                                                            : "Remove"}
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>

                        {!loading && vehicleHistory.length > 0 && (
                            <div className="vehicle-history-block">
                                <button
                                    type="button"
                                    className="vehicle-history-toggle"
                                    aria-expanded={showVehicleHistory}
                                    onClick={() => setShowVehicleHistory((open) => !open)}
                                >
                                    <span>
                                        Former vehicles
                                        <span className="vehicle-history-count">
                                            {vehicleHistory.length}
                                        </span>
                                    </span>
                                    <span className="vehicle-history-chevron" aria-hidden="true">
                                        {showVehicleHistory ? "▾" : "▸"}
                                    </span>
                                </button>
                                {showVehicleHistory && (
                                    <div className="vehicle-list vehicle-list-history">
                                        {vehicleHistory.map((vehicle) => {
                                            const expanded = expandedVehicleId === `history-${vehicle.id}`;
                                            return (
                                                <div
                                                    key={vehicle.id}
                                                    className="vehicle-row vehicle-row-history"
                                                >
                                                    <div className="vehicle-row-top">
                                                        <button
                                                            type="button"
                                                            className="vehicle-row-toggle"
                                                            aria-expanded={expanded}
                                                            onClick={() =>
                                                                setExpandedVehicleId((current) =>
                                                                    current === `history-${vehicle.id}`
                                                                        ? null
                                                                        : `history-${vehicle.id}`
                                                                )
                                                            }
                                                        >
                                                            <div className="vehicle-row-heading">
                                                                <p className="vehicle-row-title">
                                                                    {vehicle.modelLine ?? vehicle.model}
                                                                </p>
                                                                <span className="booking-row-chevron" aria-hidden="true">
                                                                    {expanded ? "▾" : "▸"}
                                                                </span>
                                                            </div>
                                                            <p className="vehicle-row-meta">VIN {vehicle.vin}</p>
                                                        </button>
                                                        <span className="vehicle-history-badge">Former</span>
                                                    </div>
                                                    {expanded && (
                                                        <div className="vehicle-row-details">
                                                            <VehicleDetails vehicle={vehicle} />
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        )}
                    </section>
                )}

                {section === "bookings" && (
                    <section className="section-block dashboard-panel">
                        <h2>Bookings</h2>
                        {vehicles.length > 0 && (
                            <button
                                type="button"
                                className="primary-btn section-action-btn"
                                onClick={() => navigate("/client/request-service")}
                            >
                                Request service
                            </button>
                        )}
                        {loading && <p>Loading…</p>}
                        {error && <p className="form-error">{error}</p>}
                        {bookingError && <p className="form-error">{bookingError}</p>}
                        {!loading && (activeBookings.length > 0 || vehicles.length > 0) && (
                            <label className="booking-vehicle-filter">
                                <span className="booking-vehicle-filter-label">Vehicle</span>
                                <select
                                    value={bookingVehicleFilter}
                                    onChange={(e) => setBookingVehicleFilter(e.target.value)}
                                >
                                    <option value="">All vehicles</option>
                                    {vehicles.map((vehicle) => (
                                        <option key={vehicle.id} value={vehicle.id}>
                                            {formatVehicleOption(vehicle)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        )}
                        <div className="booking-list">
                            {activeBookings.map((booking) => renderBookingCard(booking))}
                        </div>
                    </section>
                )}

                {section === "archive" && (
                    <section className="section-block dashboard-panel">
                        <h2>Archive</h2>
                        {loading && <p>Loading…</p>}
                        {error && <p className="form-error">{error}</p>}
                        {bookingError && <p className="form-error">{bookingError}</p>}
                        {!loading && bookings.some((b) => isArchiveStatus(b.status)) && (
                            <label className="archive-find">
                                <span className="archive-find-label">Find</span>
                                <input
                                    type="search"
                                    placeholder="Search by model, VIN, status, reason…"
                                    value={archiveQuery}
                                    onChange={(e) => setArchiveQuery(e.target.value)}
                                />
                            </label>
                        )}
                        <div className="booking-list">
                            {archiveBookings.map((booking) => renderBookingCard(booking, { archive: true }))}
                        </div>
                    </section>
                )}

                {section === "account" && (
                    <section className="section-block dashboard-panel">
                        <h2>Account management</h2>

                        {!showPasswordForm ? (
                            <button
                                type="button"
                                className="primary-btn section-action-btn"
                                onClick={() => {
                                    setShowDeleteAccount(false);
                                    setShowPasswordForm(true);
                                }}
                            >
                                Change password
                            </button>
                        ) : (
                            <div className="account-password-panel">
                                <div className="account-password-toolbar">
                                    <h3>Change password</h3>
                                    <button
                                        type="button"
                                        className="logout-btn"
                                        onClick={() => setShowPasswordForm(false)}
                                    >
                                        Cancel
                                    </button>
                                </div>
                                <ChangePasswordForm embedded />
                            </div>
                        )}

                        <div className="account-danger-zone">
                            <h3>Delete account</h3>
                            <p>
                                Open service bookings must be cancelled or completed first.
                                We will email you a confirmation link before anything is deleted.
                            </p>

                            {!showDeleteAccount ? (
                                <button
                                    type="button"
                                    className="danger-btn"
                                    onClick={() => {
                                        setShowPasswordForm(false);
                                        setDeleteAccountMessage("");
                                        setDeleteAccountError("");
                                        setShowDeleteAccount(true);
                                    }}
                                >
                                    Delete account
                                </button>
                            ) : (
                                <div className="account-delete-panel">
                                    <p>
                                        We will send a confirmation link to your email.
                                        Your account stays active until you open that link.
                                    </p>
                                    <div className="account-delete-actions">
                                        <button
                                            type="button"
                                            className="danger-btn"
                                            disabled={deleteAccountLoading || Boolean(deleteAccountMessage)}
                                            onClick={requestAccountDeletion}
                                        >
                                            {deleteAccountLoading
                                                ? "Sending…"
                                                : "Send confirmation email"}
                                        </button>
                                        <button
                                            type="button"
                                            className="logout-btn"
                                            disabled={deleteAccountLoading}
                                            onClick={() => {
                                                setShowDeleteAccount(false);
                                                setDeleteAccountMessage("");
                                                setDeleteAccountError("");
                                            }}
                                        >
                                            Cancel
                                        </button>
                                    </div>
                                    {deleteAccountError && (
                                        <p className="form-error" role="alert">
                                            {deleteAccountError}
                                        </p>
                                    )}
                                    {deleteAccountMessage && (
                                        <p className="form-success" role="status">
                                            {deleteAccountMessage}
                                        </p>
                                    )}
                                </div>
                            )}
                        </div>
                    </section>
                )}
            </div>
        </div>
    );
}
