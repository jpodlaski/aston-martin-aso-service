import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChangePasswordForm from "../../components/ChangePasswordForm";
import { api } from "../../services/api";
import { isConsultant, isTechnician } from "../../constants/roles";
import { clearSession, getSession } from "../../services/auth";
import {
    formatCustomerDateTime,
    hasCustomerAvailability,
    toDatetimeLocalValue,
} from "../../utils/dateTime";
import "../../components/DashboardLayout.css";
import "../client/ClientDashboard.css";
import "./EmployeeDashboard.css";

function sectionLabels(consultant) {
    return {
        available: consultant ? "Incoming requests" : "Available jobs",
        assignments: consultant ? "Workshop" : "My assignments",
        archive: "Archive",
        account: "Account management",
    };
}

function formatStatus(status) {
    if (!status) return "";
    return String(status).replaceAll("_", " ");
}

function bookingVin(booking) {
    return booking.vehicle?.vin ?? booking.vin ?? null;
}

function assignedWorkerName(booking) {
    const worker = booking.assignedWorker;
    if (!worker) return null;
    const name = [worker.firstName, worker.lastName].filter(Boolean).join(" ").trim();
    return name || worker.login || null;
}

function cancelledByLabel(cancelledBy) {
    if (cancelledBy === "CUSTOMER") return "Client";
    if (cancelledBy === "WORKER") return "Worker";
    if (cancelledBy === "WORKSHOP" || cancelledBy === "CONSULTANT") return "Consultant";
    return null;
}

function isArchiveStatus(status) {
    return status === "COMPLETED" || status === "CANCELLED";
}

function bookingMatchesQuery(booking, query) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const vin = bookingVin(booking);
    const worker = assignedWorkerName(booking);
    const cancelledBy = cancelledByLabel(booking.cancelledBy);
    const haystack = [
        booking.id,
        booking.status,
        formatStatus(booking.status),
        booking.customerName,
        booking.carModel,
        vin,
        worker,
        cancelledBy,
        booking.customerDescription,
        booking.cancellationReason,
        ...(booking.serviceTypes ?? []),
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
    return haystack.includes(q);
}

/**
 * Workshop dashboard: consultants accept/schedule requests; technicians claim and complete work.
 */
export default function EmployeeDashboard() {
    const navigate = useNavigate();
    const session = getSession();
    const menuRef = useRef(null);
    const consultant = isConsultant(session?.role);
    const technician = isTechnician(session?.role);
    const sections = sectionLabels(consultant);

    const [section, setSection] = useState("available");
    const [menuOpen, setMenuOpen] = useState(false);
    const [showPasswordForm, setShowPasswordForm] = useState(false);

    const [available, setAvailable] = useState([]);
    const [awaitingWorkshop, setAwaitingWorkshop] = useState([]);
    const [myBookings, setMyBookings] = useState([]);
    const [consultantArchive, setConsultantArchive] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [actionError, setActionError] = useState("");

    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [claimForms, setClaimForms] = useState({});
    const [rejectForms, setRejectForms] = useState({});
    const [acceptForms, setAcceptForms] = useState({});
    const [capacityByBookingId, setCapacityByBookingId] = useState({});
    const [completeForms, setCompleteForms] = useState({});
    const [cancelForms, setCancelForms] = useState({});
    const [workPlanForms, setWorkPlanForms] = useState({});
    const [workPlanMessage, setWorkPlanMessage] = useState({});
    const [archiveQuery, setArchiveQuery] = useState("");

    const assigned = useMemo(
        () => myBookings.filter((booking) => booking.status === "IN_PROGRESS"),
        [myBookings]
    );

    const assignmentList = consultant ? awaitingWorkshop : assigned;

    const archive = useMemo(() => {
        const list = consultant
            ? [...consultantArchive]
            : [...myBookings].filter((booking) => isArchiveStatus(booking.status));
        return list
            .filter((booking) => bookingMatchesQuery(booking, archiveQuery))
            .sort((a, b) => Number(b.id) - Number(a.id));
    }, [consultant, consultantArchive, myBookings, archiveQuery]);

    const loadBookings = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            if (isConsultant(session.role)) {
                const [availableRes, awaitingRes, archiveRes] = await Promise.all([
                    api.get("/bookings/available"),
                    api.get("/workers/me/awaiting-workshop"),
                    api.get("/workers/me/consultant-archive"),
                ]);
                setAvailable(availableRes.data);
                setAwaitingWorkshop(awaitingRes.data);
                setConsultantArchive(archiveRes.data);
                setMyBookings([]);
            } else {
                const [availableRes, assignedRes] = await Promise.all([
                    api.get("/bookings/available"),
                    api.get("/workers/me/bookings"),
                ]);
                setAvailable(availableRes.data);
                setMyBookings(assignedRes.data);
                setAwaitingWorkshop([]);
                setConsultantArchive([]);
            }
        } catch {
            setError("Could not load bookings.");
        } finally {
            setLoading(false);
        }
    }, [session?.token, session?.role]);

    useEffect(() => {
        loadBookings();
    }, [loadBookings]);

    useEffect(() => {
        setAcceptForms((prev) => {
            const next = { ...prev };
            for (const booking of available) {
                if (
                    booking.status === "SCHEDULED"
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
    }, [available]);

    // Live mechanic capacity at the appointment time the consultant is about to confirm.
    useEffect(() => {
        if (!consultant) return undefined;

        const controllers = [];
        const timers = [];

        for (const booking of available) {
            if (booking.status !== "SCHEDULED") continue;
            const localValue = acceptForms[booking.id]?.scheduledDateTime;
            if (!localValue || localValue.length < 16) {
                setCapacityByBookingId((prev) => {
                    if (!prev[booking.id]) return prev;
                    const next = { ...prev };
                    delete next[booking.id];
                    return next;
                });
                continue;
            }

            const at = `${localValue}:00`;
            const controller = new AbortController();
            controllers.push(controller);
            const timer = setTimeout(() => {
                api.get("/workers/me/workshop-capacity", {
                    params: { at },
                    signal: controller.signal,
                    skipAuthRedirect: true,
                })
                    .then((res) => {
                        setCapacityByBookingId((prev) => ({
                            ...prev,
                            [booking.id]: res.data,
                        }));
                    })
                    .catch((err) => {
                        if (err.code === "ERR_CANCELED" || err.name === "CanceledError") return;
                        setCapacityByBookingId((prev) => ({
                            ...prev,
                            [booking.id]: { available: false, error: true },
                        }));
                    });
            }, 250);
            timers.push(timer);
        }

        return () => {
            timers.forEach(clearTimeout);
            controllers.forEach((c) => c.abort());
        };
    }, [consultant, available, acceptForms]);

    useEffect(() => {
        setWorkPlanForms((prev) => {
            const next = { ...prev };
            for (const booking of assigned) {
                if (booking.status !== "IN_PROGRESS") continue;
                if (next[booking.id]?.touched) continue;
                next[booking.id] = {
                    ...next[booking.id],
                    serviceTypes: (booking.serviceTypes ?? []).join(", "),
                    estimatedCost:
                        booking.estimatedCost != null ? String(booking.estimatedCost) : "",
                };
            }
            return next;
        });
    }, [assigned]);

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

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const selectSection = (next) => {
        setSection(next);
        setMenuOpen(false);
        setExpandedBookingId(null);
        setActionError("");
        if (next !== "account") setShowPasswordForm(false);
        if (next !== "archive") setArchiveQuery("");
    };

    const updateMap = (setter, id, field, value) => {
        setter((prev) => ({
            ...prev,
            [id]: { ...prev[id], [field]: value },
        }));
    };

    const updateWorkPlanField = (id, field, value) => {
        setWorkPlanForms((prev) => ({
            ...prev,
            [id]: { ...prev[id], [field]: value, touched: true },
        }));
    };

    const updateWorkPlan = async (bookingId) => {
        setActionError("");
        setWorkPlanMessage((prev) => ({ ...prev, [bookingId]: "" }));
        const form = workPlanForms[bookingId] ?? {};
        const serviceTypes = (form.serviceTypes ?? "")
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);
        if (serviceTypes.length === 0) {
            setWorkPlanMessage((prev) => ({
                ...prev,
                [bookingId]: "Enter at least one service for work planned.",
            }));
            return;
        }
        const costValue = form.estimatedCost?.toString().trim();
        const estimatedCost = costValue ? Number(costValue) : null;
        try {
            const res = await api.post(`/bookings/${bookingId}/work-plan`, {
                serviceTypes,
                estimatedCost: Number.isFinite(estimatedCost) ? estimatedCost : undefined,
            });
            const updated = res.data;
            setMyBookings((prev) =>
                prev.map((booking) => (booking.id === bookingId ? updated : booking))
            );
            setWorkPlanForms((prev) => ({
                ...prev,
                [bookingId]: {
                    serviceTypes: (updated.serviceTypes ?? []).join(", "),
                    estimatedCost:
                        updated.estimatedCost != null ? String(updated.estimatedCost) : "",
                    touched: false,
                },
            }));
            setWorkPlanMessage((prev) => ({
                ...prev,
                [bookingId]: "Work planned updated.",
            }));
        } catch (err) {
            const message = err.response?.data?.message ?? "Could not update work planned.";
            setWorkPlanMessage((prev) => ({ ...prev, [bookingId]: message }));
            setActionError(message);
        }
    };

    const rejectBooking = async (bookingId) => {
        setActionError("");
        const reason = rejectForms[bookingId]?.reason?.trim();
        if (!reason) {
            setActionError("A reason is required to reject a booking.");
            return;
        }
        try {
            await api.post(`/bookings/${bookingId}/reject`, { reason });
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
            await api.post(`/bookings/${bookingId}/cancel`, { reason });
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

    const acceptBooking = async (bookingId) => {
        setActionError("");
        const form = acceptForms[bookingId] ?? {};
        const capacity = capacityByBookingId[bookingId];
        if (capacity && capacity.available === false && !capacity.error) {
            setActionError(
                "No mechanic free at that time. Call the client to agree another appointment."
            );
            return;
        }
        try {
            await api.post(`/bookings/${bookingId}/accept`, {
                scheduledDateTime: form.scheduledDateTime
                    ? `${form.scheduledDateTime}:00`
                    : undefined,
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not accept booking.");
        }
    };

    const completeBooking = async (bookingId) => {
        setActionError("");
        const form = completeForms[bookingId] ?? {};
        try {
            await api.post(`/bookings/${bookingId}/complete`, {
                finalCost: Number(form.finalCost),
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not complete booking.");
        }
    };

    const renderBookingDetails = (booking) => (
        <div className="booking-detail-rows">
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Customer</span>
                    <p>{booking.customerName || "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">VIN</span>
                    <p>{bookingVin(booking) || "—"}</p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Issue</span>
                    <p>{booking.customerDescription || "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Work planned</span>
                    <p>
                        {booking.serviceTypes?.length
                            ? booking.serviceTypes.join(", ")
                            : "—"}
                    </p>
                </div>
            </div>
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Estimated drop-off</span>
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
            <div className="booking-detail-row">
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Technician</span>
                    <p>{assignedWorkerName(booking) || "—"}</p>
                </div>
                <div className="booking-detail-item">
                    <span className="booking-detail-label">Availability notes</span>
                    <p>{booking.availabilityNotes || "—"}</p>
                </div>
            </div>
            {booking.status === "CANCELLED" && (
                <div className="booking-detail-row">
                    <div className="booking-detail-item">
                        <span className="booking-detail-label">Cancelled by</span>
                        <p>{cancelledByLabel(booking.cancelledBy) || "—"}</p>
                    </div>
                    <div className="booking-detail-item">
                        <span className="booking-detail-label">Cancellation reason</span>
                        <p>{booking.cancellationReason?.trim() || "—"}</p>
                    </div>
                </div>
            )}
            {!hasCustomerAvailability(booking) && (
                <p className="availability-muted">No preferred date provided by customer.</p>
            )}
        </div>
    );

    return (
        <div className="client-dashboard-shell employee-dashboard-shell">
            <div
                className="client-dashboard-bg"
                aria-hidden="true"
            />
            <div className="client-dashboard-veil" aria-hidden="true" />

            <div className="dashboard client-dashboard employee-dashboard">
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
                                    {Object.entries(sections).map(([id, label]) => (
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

                {loading && <p>Loading…</p>}
                {error && <p className="form-error">{error}</p>}
                {actionError && <p className="form-error">{actionError}</p>}

                {section === "available" && (
                    <section className="section-block dashboard-panel">
                        <h2>{sections.available}</h2>
                        <div className="booking-list">
                            {available.map((booking) => {
                                const expanded = expandedBookingId === `available-${booking.id}`;
                                const vin = bookingVin(booking);
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
                                                        current === `available-${booking.id}`
                                                            ? null
                                                            : `available-${booking.id}`
                                                    )
                                                }
                                            >
                                                <div className="booking-row-heading">
                                                    <span className="booking-row-id">
                                                        Booking #{booking.id}
                                                    </span>
                                                    <span className={statusClass}>
                                                        {formatStatus(booking.status)}
                                                    </span>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="booking-row-title">
                                                    {booking.carModel}
                                                    {vin ? ` · VIN ${vin}` : ""}
                                                </p>
                                                <p className="booking-row-meta">
                                                    {booking.customerName}
                                                    {booking.estimatedDropOffTime
                                                        ? ` · Drop-off ${formatCustomerDateTime(booking.estimatedDropOffTime)}`
                                                        : ""}
                                                    {booking.scheduledDateTime
                                                        ? ` · Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`
                                                        : ""}
                                                </p>
                                            </button>
                                        </div>

                                        {expanded && (
                                            <div className="booking-row-details">
                                                {renderBookingDetails(booking)}

                                                <div className="worker-actions-panel">
                                                    {consultant && (
                                                        <>
                                                            <label className="worker-field">
                                                                <span className="booking-detail-label">
                                                                    Appointment
                                                                </span>
                                                                <input
                                                                    type="datetime-local"
                                                                    value={
                                                                        acceptForms[booking.id]
                                                                            ?.scheduledDateTime ?? ""
                                                                    }
                                                                    onChange={(e) =>
                                                                        updateMap(
                                                                            setAcceptForms,
                                                                            booking.id,
                                                                            "scheduledDateTime",
                                                                            e.target.value
                                                                        )
                                                                    }
                                                                />
                                                            </label>
                                                            {(() => {
                                                                const capacity =
                                                                    capacityByBookingId[booking.id];
                                                                if (!capacity) {
                                                                    return (
                                                                        <p className="capacity-status capacity-status-pending">
                                                                            Checking mechanic availability…
                                                                        </p>
                                                                    );
                                                                }
                                                                if (capacity.error) {
                                                                    return (
                                                                        <p className="capacity-status capacity-status-full">
                                                                            Could not check capacity. Try again.
                                                                        </p>
                                                                    );
                                                                }
                                                                if (capacity.available) {
                                                                    return (
                                                                        <p className="capacity-status capacity-status-ok">
                                                                            Mechanic free at this time —
                                                                            {" "}
                                                                            {capacity.remainingSlots}
                                                                            {" "}
                                                                            of
                                                                            {" "}
                                                                            {capacity.technicianCount}
                                                                            {" "}
                                                                            available
                                                                            ({capacity.bookedCount} still on
                                                                            earlier open jobs).
                                                                        </p>
                                                                    );
                                                                }
                                                                return (
                                                                    <p className="capacity-status capacity-status-full">
                                                                        No mechanic free at this time
                                                                        ({capacity.bookedCount}
                                                                        /
                                                                        {capacity.technicianCount}
                                                                        {" "}
                                                                        busy on open jobs). Call the client
                                                                        to discuss another appointment.
                                                                    </p>
                                                                );
                                                            })()}
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="primary-btn"
                                                                    disabled={
                                                                        !acceptForms[booking.id]
                                                                            ?.scheduledDateTime
                                                                        || capacityByBookingId[booking.id]
                                                                            ?.available !== true
                                                                    }
                                                                    onClick={() =>
                                                                        acceptBooking(booking.id)
                                                                    }
                                                                >
                                                                    Accept booking
                                                                </button>
                                                            </div>

                                                            <label className="worker-field">
                                                                <span className="booking-detail-label">
                                                                    Rejection reason
                                                                </span>
                                                                <input
                                                                    placeholder="Required to reject"
                                                                    value={rejectForms[booking.id]?.reason ?? ""}
                                                                    onChange={(e) =>
                                                                        updateMap(
                                                                            setRejectForms,
                                                                            booking.id,
                                                                            "reason",
                                                                            e.target.value
                                                                        )
                                                                    }
                                                                />
                                                            </label>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="danger-btn"
                                                                    onClick={() =>
                                                                        rejectBooking(booking.id)
                                                                    }
                                                                >
                                                                    Reject
                                                                </button>
                                                            </div>
                                                        </>
                                                    )}

                                                    {technician && (
                                                        <>
                                                            <div className="worker-form-grid">
                                                                <label className="worker-field">
                                                                    <span className="booking-detail-label">
                                                                        Estimated cost (EUR)
                                                                    </span>
                                                                    <input
                                                                        type="number"
                                                                        min="0"
                                                                        step="0.01"
                                                                        placeholder="Optional"
                                                                        value={
                                                                            claimForms[booking.id]
                                                                                ?.estimatedCost ?? ""
                                                                        }
                                                                        onChange={(e) =>
                                                                            updateMap(
                                                                                setClaimForms,
                                                                                booking.id,
                                                                                "estimatedCost",
                                                                                e.target.value
                                                                            )
                                                                        }
                                                                    />
                                                                </label>
                                                                <label className="worker-field">
                                                                    <span className="booking-detail-label">
                                                                        Services
                                                                    </span>
                                                                    <input
                                                                        placeholder="e.g. car check"
                                                                        value={
                                                                            claimForms[booking.id]
                                                                                ?.serviceTypes ?? ""
                                                                        }
                                                                        onChange={(e) =>
                                                                            updateMap(
                                                                                setClaimForms,
                                                                                booking.id,
                                                                                "serviceTypes",
                                                                                e.target.value
                                                                            )
                                                                        }
                                                                    />
                                                                </label>
                                                            </div>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="primary-btn"
                                                                    onClick={() =>
                                                                        claimBooking(booking.id)
                                                                    }
                                                                >
                                                                    Claim
                                                                </button>
                                                            </div>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                )}

                {section === "assignments" && (
                    <section className="section-block dashboard-panel">
                        <h2>{sections.assignments}</h2>
                        <div className="booking-list">
                            {assignmentList.map((booking) => {
                                const expanded = expandedBookingId === `assigned-${booking.id}`;
                                const vin = bookingVin(booking);
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
                                                        current === `assigned-${booking.id}`
                                                            ? null
                                                            : `assigned-${booking.id}`
                                                    )
                                                }
                                            >
                                                <div className="booking-row-heading">
                                                    <span className="booking-row-id">
                                                        Booking #{booking.id}
                                                    </span>
                                                    <span className={statusClass}>
                                                        {formatStatus(booking.status)}
                                                    </span>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="booking-row-title">
                                                    {booking.carModel}
                                                    {vin ? ` · VIN ${vin}` : ""}
                                                </p>
                                                <p className="booking-row-meta">
                                                    {booking.customerName}
                                                    {booking.scheduledDateTime
                                                        ? ` · Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`
                                                        : booking.estimatedDropOffTime
                                                            ? ` · Preferred ${formatCustomerDateTime(booking.estimatedDropOffTime)}`
                                                            : ""}
                                                    {consultant && assignedWorkerName(booking)
                                                        ? ` · ${assignedWorkerName(booking)}`
                                                        : ""}
                                                </p>
                                            </button>
                                        </div>

                                        {expanded && (
                                            <div className="booking-row-details">
                                                {renderBookingDetails(booking)}

                                                <div className="worker-actions-panel">
                                                    {consultant && booking.status === "READY_FOR_WORK" && (
                                                        <>
                                                            <label className="worker-field">
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
                                                                        updateMap(
                                                                            setCancelForms,
                                                                            booking.id,
                                                                            "reason",
                                                                            e.target.value
                                                                        )
                                                                    }
                                                                />
                                                            </label>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="danger-btn"
                                                                    onClick={() =>
                                                                        cancelBooking(booking.id)
                                                                    }
                                                                >
                                                                    Cancel booking
                                                                </button>
                                                            </div>
                                                        </>
                                                    )}

                                                    {technician && booking.status === "IN_PROGRESS" && (
                                                        <>
                                                            <div className="worker-form-grid">
                                                                <label className="worker-field">
                                                                    <span className="booking-detail-label">
                                                                        Work planned
                                                                    </span>
                                                                    <input
                                                                        placeholder="e.g. brake replace"
                                                                        value={
                                                                            workPlanForms[booking.id]
                                                                                ?.serviceTypes
                                                                            ?? (booking.serviceTypes ?? []).join(", ")
                                                                        }
                                                                        onChange={(e) =>
                                                                            updateWorkPlanField(
                                                                                booking.id,
                                                                                "serviceTypes",
                                                                                e.target.value
                                                                            )
                                                                        }
                                                                    />
                                                                </label>
                                                                <label className="worker-field">
                                                                    <span className="booking-detail-label">
                                                                        Estimated cost (EUR)
                                                                    </span>
                                                                    <input
                                                                        type="number"
                                                                        min="0"
                                                                        step="0.01"
                                                                        value={
                                                                            workPlanForms[booking.id]
                                                                                ?.estimatedCost
                                                                            ?? (booking.estimatedCost != null
                                                                                ? String(booking.estimatedCost)
                                                                                : "")
                                                                        }
                                                                        onChange={(e) =>
                                                                            updateWorkPlanField(
                                                                                booking.id,
                                                                                "estimatedCost",
                                                                                e.target.value
                                                                            )
                                                                        }
                                                                    />
                                                                </label>
                                                            </div>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="primary-btn"
                                                                    onClick={() =>
                                                                        updateWorkPlan(booking.id)
                                                                    }
                                                                >
                                                                    Update work planned
                                                                </button>
                                                            </div>
                                                            {workPlanMessage[booking.id] && (
                                                                <p
                                                                    className={
                                                                        workPlanMessage[booking.id]
                                                                            === "Work planned updated."
                                                                            ? "capacity-status capacity-status-ok"
                                                                            : "capacity-status capacity-status-full"
                                                                    }
                                                                >
                                                                    {workPlanMessage[booking.id]}
                                                                </p>
                                                            )}

                                                            <label className="worker-field">
                                                                <span className="booking-detail-label">
                                                                    Final cost (EUR)
                                                                </span>
                                                                <input
                                                                    type="number"
                                                                    min="0"
                                                                    step="0.01"
                                                                    placeholder="Required to complete"
                                                                    value={
                                                                        completeForms[booking.id]
                                                                            ?.finalCost ?? ""
                                                                    }
                                                                    onChange={(e) =>
                                                                        updateMap(
                                                                            setCompleteForms,
                                                                            booking.id,
                                                                            "finalCost",
                                                                            e.target.value
                                                                        )
                                                                    }
                                                                />
                                                            </label>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="primary-btn"
                                                                    onClick={() =>
                                                                        completeBooking(booking.id)
                                                                    }
                                                                >
                                                                    Complete
                                                                </button>
                                                            </div>

                                                            <label className="worker-field">
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
                                                                        updateMap(
                                                                            setCancelForms,
                                                                            booking.id,
                                                                            "reason",
                                                                            e.target.value
                                                                        )
                                                                    }
                                                                />
                                                            </label>
                                                            <div className="worker-action-row">
                                                                <button
                                                                    type="button"
                                                                    className="danger-btn"
                                                                    onClick={() =>
                                                                        cancelBooking(booking.id)
                                                                    }
                                                                >
                                                                    Cancel booking
                                                                </button>
                                                            </div>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                )}

                {section === "archive" && (
                    <section className="section-block dashboard-panel">
                        <label className="archive-find">
                            <span className="archive-find-label">Find</span>
                            <input
                                type="search"
                                placeholder="Search by customer, VIN, model, status…"
                                value={archiveQuery}
                                onChange={(e) => setArchiveQuery(e.target.value)}
                            />
                        </label>
                        <div className="booking-list">
                            {archive.map((booking) => {
                                const expanded = expandedBookingId === `archive-${booking.id}`;
                                const vin = bookingVin(booking);
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
                                                        current === `archive-${booking.id}`
                                                            ? null
                                                            : `archive-${booking.id}`
                                                    )
                                                }
                                            >
                                                <div className="booking-row-heading">
                                                    <span className="booking-row-id">
                                                        Booking #{booking.id}
                                                    </span>
                                                    <span className={statusClass}>
                                                        {formatStatus(booking.status)}
                                                    </span>
                                                    <span className="booking-row-chevron" aria-hidden="true">
                                                        {expanded ? "▾" : "▸"}
                                                    </span>
                                                </div>
                                                <p className="booking-row-title">
                                                    {booking.carModel}
                                                    {vin ? ` · VIN ${vin}` : ""}
                                                </p>
                                                <p className="booking-row-meta">
                                                    {booking.customerName}
                                                    {booking.scheduledDateTime
                                                        ? ` · Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`
                                                        : ""}
                                                    {assignedWorkerName(booking)
                                                        ? ` · ${assignedWorkerName(booking)}`
                                                        : ""}
                                                    {booking.status === "CANCELLED"
                                                        && cancelledByLabel(booking.cancelledBy)
                                                        ? ` · Cancelled by ${cancelledByLabel(booking.cancelledBy)}`
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
