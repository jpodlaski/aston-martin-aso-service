import { useState } from "react";
import { api } from "../services/api";

// Shared self-service password change for client, worker, and admin dashboards.
export default function ChangePasswordForm({ embedded = false }) {
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);

    // Show mismatch as soon as the user has typed something in confirm.
    const passwordsMismatch =
        confirmPassword.length > 0 && newPassword !== confirmPassword;
    const canSubmit =
        !loading
        && currentPassword.length > 0
        && newPassword.length >= 8
        && confirmPassword.length > 0
        && !passwordsMismatch;

    const clearFeedback = () => {
        if (error) setError("");
        if (success) setSuccess("");
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");
        setSuccess("");

        if (newPassword !== confirmPassword) {
            setError("New passwords do not match.");
            return;
        }

        setLoading(true);
        try {
            await api.post("/auth/change-password", {
                currentPassword,
                newPassword,
            });
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
            setSuccess("Password updated.");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not change password.");
        } finally {
            setLoading(false);
        }
    };

    const form = (
        <>
            <p>Enter your current password and choose a new one (at least 8 characters).</p>
            <form
                className="auth-form"
                style={{ maxWidth: "100%", margin: "1rem 0 0" }}
                onSubmit={handleSubmit}
            >
                <input
                    type="password"
                    placeholder="Current password"
                    value={currentPassword}
                    onChange={(e) => {
                        clearFeedback();
                        setCurrentPassword(e.target.value);
                    }}
                    autoComplete="current-password"
                    required
                />
                <input
                    type="password"
                    placeholder="New password"
                    value={newPassword}
                    onChange={(e) => {
                        clearFeedback();
                        setNewPassword(e.target.value);
                    }}
                    autoComplete="new-password"
                    minLength={8}
                    required
                />
                <input
                    type="password"
                    placeholder="Confirm new password"
                    value={confirmPassword}
                    onChange={(e) => {
                        clearFeedback();
                        setConfirmPassword(e.target.value);
                    }}
                    autoComplete="new-password"
                    minLength={8}
                    required
                    aria-invalid={passwordsMismatch}
                />
                {passwordsMismatch && (
                    <p className="form-error">Passwords do not match.</p>
                )}
                <button type="submit" disabled={!canSubmit}>
                    {loading ? "Updating…" : "Update password"}
                </button>
                {error && <p className="form-error">{error}</p>}
                {success && <p className="form-success">{success}</p>}
            </form>
        </>
    );

    if (embedded) {
        return form;
    }

    return (
        <section className="section-block">
            <h2>Change password</h2>
            {form}
        </section>
    );
}
