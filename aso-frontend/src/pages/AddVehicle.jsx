import { useNavigate } from "react-router-dom";
import VehicleConfigurator from "../components/VehicleConfigurator";
import { getSession } from "../services/auth";
import "../components/VehicleConfigurator.css";
import "../components/DashboardLayout.css";

// Wrapper around VehicleConfigurator; returns to client dashboard after success.
export default function AddVehicle() {
    const navigate = useNavigate();
    const customerId = getSession()?.id;

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <div>
                    <h1>Add a vehicle</h1>
                    <p className="dashboard-user">Select your Aston Martin specification.</p>
                </div>
                <button type="button" className="logout-btn" onClick={() => navigate("/client")}>
                    Back to dashboard
                </button>
            </div>

            {customerId ? (
                <VehicleConfigurator
                    customerId={customerId}
                    onVehicleAdded={() => navigate("/client")}
                />
            ) : (
                <p className="form-error">You must be signed in to add a vehicle.</p>
            )}
        </div>
    );
}
