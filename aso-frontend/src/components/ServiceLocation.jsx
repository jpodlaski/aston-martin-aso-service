import { WORKSHOP_ADDRESS } from "../constants/workshop";
import "./ServiceLocation.css";

export default function ServiceLocation({ className = "" }) {
    return (
        <p className={`service-location ${className}`.trim()}>
            <span className="service-location-label">Service location</span>
            <span className="service-location-address">{WORKSHOP_ADDRESS}</span>
        </p>
    );
}
