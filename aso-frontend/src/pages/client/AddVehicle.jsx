import { useNavigate } from "react-router-dom";
import VehicleConfigurator from "../../components/VehicleConfigurator";
import { getSession } from "../../services/auth";
import "../../components/VehicleConfigurator.css";
import "../../components/DashboardLayout.css";
import "./ClientDashboard.css";
import "./AddVehicle.css";

// Wrapper around VehicleConfigurator; returns to client dashboard after success.
export default function AddVehicle() {
    const navigate = useNavigate();
    const session = getSession();

    return (
        <div className="client-dashboard-shell">
            <div
                className="client-dashboard-bg"
                aria-hidden="true"
            />
            <div className="client-dashboard-veil" aria-hidden="true" />

            <div className="dashboard client-dashboard add-vehicle-page">
                <div className="dashboard-header">
                    <div>
                        <img className="logo" src="/aston-martin-logo.png" alt="Aston Martin" />
                        <h1>Add a vehicle</h1>
                    </div>
                    <button
                        type="button"
                        className="logout-btn"
                        onClick={() => navigate("/client")}
                    >
                        Back to dashboard
                    </button>
                </div>

                {session?.token ? (
                    <VehicleConfigurator onVehicleAdded={() => navigate("/client")} />
                ) : (
                    <p className="form-error">You must be signed in to add a vehicle.</p>
                )}
            </div>
        </div>
    );
}
