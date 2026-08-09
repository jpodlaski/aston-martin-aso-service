import { Link } from "react-router-dom";
import { WORKSHOP_ADDRESS } from "../constants/workshop";
import "./Landing.css";

/**
 * Aston Martin–inspired full-bleed landing: centered brand, client + staff links top-right,
 * hero photo only (no video carousel). Matches the official site’s first-viewport composure.
 */
export default function Landing() {
    return (
        <div className="landing">
            <div
                className="landing-hero"
                style={{ backgroundImage: "url(/hero-aston-martin.jpg)" }}
                role="img"
                aria-label="Aston Martin sports car at golden hour"
            />
            <div className="landing-veil" aria-hidden="true" />

            <header className="landing-header">
                <div className="landing-header-spacer" aria-hidden="true" />

                <Link to="/" className="logo" aria-label="Aston Martin home">
                    <img src="/aston-martin-logo.png" alt="Aston Martin" />
                </Link>

                <nav className="landing-actions" aria-label="Account">
                    <Link to="/login" className="landing-btn landing-btn-solid">
                        Login
                    </Link>
                    <Link to="/register" className="landing-btn landing-btn-ghost">
                        Register
                    </Link>
                    <span className="landing-actions-sep" aria-hidden="true">
                        |
                    </span>
                    <Link to="/employee-login" className="landing-staff-link">
                        Staff access
                    </Link>
                </nav>
            </header>

            <div className="landing-copy">
                <p className="landing-eyebrow">Authorized Service</p>
                <h1 className="landing-title">In harmony with the driver</h1>
                <p className="landing-lede">
                    <span className="landing-lede-lead">
                        Book workshop care for your Aston Martin —
                    </span>
                    <span className="landing-lede-rest">
                        from request to invoice, with the attention the marque deserves.
                    </span>
                </p>
                <p className="landing-location">
                    <span className="landing-location-label">Service location</span>
                    <span className="landing-location-address">{WORKSHOP_ADDRESS}</span>
                </p>
            </div>
        </div>
    );
}
