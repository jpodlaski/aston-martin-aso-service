import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChangePasswordForm from "../../components/ChangePasswordForm";
import { ASSIGNABLE_ROLES, roleLabel } from "../../constants/roles";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import { formatCustomerDateTime } from "../../utils/dateTime";
import "../../components/DashboardLayout.css";
import "../client/ClientDashboard.css";
import "./AdminDashboard.css";

const SECTIONS = {
    workers: "Workers",
    bookings: "Booking history",
    account: "Account management",
};

function formatBookingStatus(status) {
    if (!status) return "";
    return String(status).replaceAll("_", " ");
}

function formatCancelledBy(value) {
    if (!value) return null;
    const labels = {
        CUSTOMER: "Client",
        WORKER: "Worker",
        WORKSHOP: "Consultant",
        CONSULTANT: "Consultant",
    };
    return labels[value] ?? value;
}

function bookingMatchesQuery(booking, query) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const cancelledBy = formatCancelledBy(booking.cancelledBy);
    const haystack = [
        booking.id,
        booking.status,
        formatBookingStatus(booking.status),
        booking.customerName,
        booking.customerEmail,
        booking.customerDescription,
        booking.modelLine,
        booking.carModel,
        booking.vin,
        booking.assignedWorkerName,
        booking.assignedWorkerRole,
        cancelledBy,
        booking.cancellationReason,
        ...(booking.serviceTypes ?? []),
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
    return haystack.includes(q);
}

/**
 * Management dashboard: create/update/delete workers and view full booking history.
 * UI is gated by MANAGEMENT_ROLES; API still checks AuthSupport.requireCanManageWorkers().
 */
export default function AdminDashboard() {
    const navigate = useNavigate();
    const session = getSession();
    const menuRef = useRef(null);

    const [section, setSection] = useState("workers");
    const [menuOpen, setMenuOpen] = useState(false);
    const [showAddWorkerForm, setShowAddWorkerForm] = useState(false);
    const [showPasswordForm, setShowPasswordForm] = useState(false);
    const [expandedWorkerId, setExpandedWorkerId] = useState(null);
    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [archiveQuery, setArchiveQuery] = useState("");

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
            setBookings(
                [...res.data].sort((a, b) => Number(b.id) - Number(a.id))
            );
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

    const filteredBookings = useMemo(
        () => bookings.filter((booking) => bookingMatchesQuery(booking, archiveQuery)),
        [bookings, archiveQuery]
    );

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const selectSection = (next) => {
        setSection(next);
        setMenuOpen(false);
        setExpandedWorkerId(null);
        setExpandedBookingId(null);
        if (next !== "bookings") setArchiveQuery("");
        if (next !== "workers") {
            setShowAddWorkerForm(false);
            setFormError("");
            setFormSuccess("");
        }
        if (next !== "account") setShowPasswordForm(false);
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
            setShowAddWorkerForm(false);
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

    const renderBookingDetails = (booking) => (
        <div className="booking-detail-rows">
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Customer</span>
                    <p>{booking.customerName ?? "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Email</span>
                    <p>{booking.customerEmail ?? "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Issue</span>
                    <p>{booking.customerDescription ?? "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Vehicle</span>
                    <p>{booking.modelLine ?? booking.carModel ?? "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">VIN</span>
                    <p>{booking.vin ?? "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Services</span>
                    <p>
                        {booking.serviceTypes?.length > 0
                            ? booking.serviceTypes.join(", ")
                            : "—"}
                    </p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Preferred drop-off</span>
                    <p>
                        {booking.estimatedDropOffTime
                            ? formatCustomerDateTime(booking.estimatedDropOffTime)
                            : "—"}
                    </p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Appointment</span>
                    <p>
                        {booking.scheduledDateTime
                            ? formatCustomerDateTime(booking.scheduledDateTime)
                            : "—"}
                    </p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Assigned worker</span>
                    <p>
                        {booking.assignedWorkerName
                            ? `${booking.assignedWorkerName}${
                                booking.assignedWorkerRole
                                    ? ` (${roleLabel(booking.assignedWorkerRole)})`
                                    : ""
                            }`
                            : "—"}
                    </p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Availability notes</span>
                    <p>{booking.availabilityNotes?.trim() || "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Estimated cost</span>
                    <p>
                        {booking.estimatedCost != null
                            ? `${booking.estimatedCost} EUR`
                            : "—"}
                    </p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Final cost</span>
                    <p>
                        {booking.finalCost != null
                            ? `${booking.finalCost} EUR`
                            : "—"}
                    </p>
                </div>
            </div>
            {(booking.cancelledBy || booking.cancellationReason) && (
                <div className="booking-detail-row">
                    <div className="booking-detail-item">
                        <span className="booking-detail-label">Cancelled by</span>
                        <p>{formatCancelledBy(booking.cancelledBy) || "—"}</p>
                    </div>
                    <div className="booking-detail-item">
                        <span className="booking-detail-label">Cancellation reason</span>
                        <p>{booking.cancellationReason?.trim() || "—"}</p>
                    </div>
                </div>
            )}
        </div>
    );

    return (
        <div className="client-dashboard-shell admin-dashboard-shell">
            <div
                className="client-dashboard-bg"
                aria-hidden="true"
            />
            <div className="client-dashboard-veil" aria-hidden="true" />

            <div className="dashboard client-dashboard admin-dashboard">
                <div className="dashboard-header">
                    <div>
                        <img className="logo" src="/aston-martin-logo.png" alt="Aston Martin" />
                        <p className="dashboard-user">
                            Signed in as {session?.name}
                        </p>
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

                {section === "workers" && (
                    <section className="section-block dashboard-panel">
                        <h2>Workers</h2>

                        {!showAddWorkerForm ? (
                            <button
                                type="button"
                                className="primary-btn section-action-btn"
                                onClick={() => {
                                    setFormError("");
                                    setFormSuccess("");
                                    setShowAddWorkerForm(true);
                                }}
                            >
                                Add worker
                            </button>
                        ) : (
                            <div className="admin-add-worker-panel">
                                <div className="admin-add-worker-toolbar">
                                    <h3>Add worker</h3>
                                    <button
                                        type="button"
                                        className="logout-btn"
                                        onClick={() => {
                                            setShowAddWorkerForm(false);
                                            setFormError("");
                                            setFormSuccess("");
                                        }}
                                    >
                                        Cancel
                                    </button>
                                </div>
                                <form className="auth-form admin-worker-form" onSubmit={createWorker}>
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
                                        placeholder="Password (min. 8 characters)"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        minLength={8}
                                    />
                                    <select
                                        value={role}
                                        onChange={(e) => setRole(e.target.value)}
                                        required
                                    >
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
                            </div>
                        )}

                        {loading && <p>Loading…</p>}
                        {error && <p className="form-error">{error}</p>}

                        <div className="vehicle-list">
                            {workers.map((worker) => {
                                const expanded = expandedWorkerId === worker.id;
                                return (
                                    <div key={worker.id} className="vehicle-row">
                                        <div className="vehicle-row-top">
                                            <button
                                                type="button"
                                                className="vehicle-row-toggle"
                                                aria-expanded={expanded}
                                                onClick={() =>
                                                    setExpandedWorkerId((current) =>
                                                        current === worker.id ? null : worker.id
                                                    )
                                                }
                                            >
                                                <div className="vehicle-row-heading">
                                                    <p className="vehicle-row-title">
                                                        {worker.firstName} {worker.lastName}
                                                    </p>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="vehicle-row-meta">
                                                    {roleLabel(worker.role)} · {worker.login}
                                                </p>
                                            </button>
                                        </div>
                                        {expanded && (
                                            <div className="vehicle-row-details">
                                                <div className="booking-detail-rows">
                                                    <div className="booking-detail-row">
                                                        <div className="booking-detail-item">
                                                            <span className="booking-detail-label">Role</span>
                                                            <p>{roleLabel(worker.role)}</p>
                                                        </div>
                                                        <div className="booking-detail-item">
                                                            <span className="booking-detail-label">Login</span>
                                                            <p>{worker.login}</p>
                                                        </div>
                                                    </div>
                                                    <div className="booking-detail-row">
                                                        <div className="booking-detail-item">
                                                            <span className="booking-detail-label">Email</span>
                                                            <p>{worker.email}</p>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="worker-row-actions">
                                                    <select
                                                        value={worker.role ?? "MECHANIC"}
                                                        disabled={updatingRoleId === worker.id}
                                                        onChange={(e) =>
                                                            changeWorkerRole(worker, e.target.value)
                                                        }
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
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                )}

                {section === "bookings" && (
                    <section className="section-block dashboard-panel">
                        <h2>Booking history</h2>
                        {bookingsLoading && <p>Loading…</p>}
                        {bookingsError && <p className="form-error">{bookingsError}</p>}
                        {!bookingsLoading && bookings.length > 0 && (
                            <label className="archive-find">
                                <span className="archive-find-label">Find</span>
                                <input
                                    type="search"
                                    placeholder="Search by customer, vehicle, VIN, status…"
                                    value={archiveQuery}
                                    onChange={(e) => setArchiveQuery(e.target.value)}
                                />
                            </label>
                        )}
                        <div className="booking-list">
                            {filteredBookings.map((booking) => {
                                const expanded = expandedBookingId === booking.id;
                                const statusClass =
                                    `booking-status booking-status-${String(booking.status || "")
                                        .toLowerCase()
                                        .replaceAll("_", "-")}`;

                                return (
                                    <div key={booking.id} className="booking-row">
                                        <div className="booking-row-top">
                                            <button
                                                type="button"
                                                className="booking-row-toggle"
                                                aria-expanded={expanded}
                                                onClick={() =>
                                                    setExpandedBookingId((current) =>
                                                        current === booking.id ? null : booking.id
                                                    )
                                                }
                                            >
                                                <div className="booking-row-heading">
                                                    <span className="booking-row-id">
                                                        Booking #{booking.id}
                                                    </span>
                                                    <span className={statusClass}>
                                                        {formatBookingStatus(booking.status)}
                                                    </span>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="booking-row-title">
                                                    {booking.modelLine ?? booking.carModel}
                                                    {booking.vin ? ` · VIN ${booking.vin}` : ""}
                                                </p>
                                                <p className="booking-row-meta">
                                                    {booking.customerName}
                                                    {booking.scheduledDateTime
                                                        ? ` · Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`
                                                        : ""}
                                                    {booking.assignedWorkerName
                                                        ? ` · ${booking.assignedWorkerName}`
                                                        : ""}
                                                    {booking.status === "CANCELLED"
                                                        && formatCancelledBy(booking.cancelledBy)
                                                        ? ` · Cancelled by ${formatCancelledBy(booking.cancelledBy)}`
                                                        : ""}
                                                    {booking.status === "COMPLETED"
                                                        && booking.finalCost != null
                                                        ? ` · ${booking.finalCost} EUR`
                                                        : ""}
                                                </p>
                                            </button>
                                        </div>

                                        {expanded && (
                                            <div className="booking-row-details">
                                                {renderBookingDetails(booking)}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
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
                                onClick={() => setShowPasswordForm(true)}
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
                    </section>
                )}
            </div>
        </div>
    );
}
