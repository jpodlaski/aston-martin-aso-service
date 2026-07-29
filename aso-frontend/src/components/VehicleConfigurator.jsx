import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../services/api";
import "./VehicleConfigurator.css";

function uniqueBy(items, keyFn) {
    const seen = new Set();
    return items.filter((item) => {
        const key = keyFn(item);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

// Wait one frame so the newly revealed step is in the DOM before scrolling.
function scrollToRef(ref) {
    requestAnimationFrame(() => {
        ref.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
}

// Multi-step Aston Martin picker; posts configurationId + VIN to the API.
export default function VehicleConfigurator({ onVehicleAdded }) {
    const [catalog, setCatalog] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [modelLineId, setModelLineId] = useState("");
    const [bodyStyleId, setBodyStyleId] = useState("");
    const [engine, setEngine] = useState("");
    const [configurationId, setConfigurationId] = useState("");
    const [productionYear, setProductionYear] = useState("");
    const [vin, setVin] = useState("");

    const bodyStepRef = useRef(null);
    const engineStepRef = useRef(null);
    const transmissionStepRef = useRef(null);
    const yearStepRef = useRef(null);
    const vinStepRef = useRef(null);

    useEffect(() => {
        api.get("/vehicles/catalog")
            .then((res) => setCatalog(res.data))
            .catch(() => setError("Could not load Aston Martin catalog."))
            .finally(() => setLoading(false));
    }, []);

    const modelLine = useMemo(
        () => catalog?.modelLines?.find((line) => line.id === modelLineId) ?? null,
        [catalog, modelLineId]
    );

    const bodyStyle = useMemo(
        () => modelLine?.bodyStyles?.find((style) => style.id === bodyStyleId) ?? null,
        [modelLine, bodyStyleId]
    );

    const configurations = bodyStyle?.configurations ?? [];

    const engineOptions = useMemo(
        () => uniqueBy(configurations, (config) => config.engine),
        [configurations]
    );

    const transmissionOptions = useMemo(
        () => configurations.filter((config) => config.engine === engine),
        [configurations, engine]
    );

    const selectedConfiguration = useMemo(
        () => configurations.find((config) => config.id === configurationId) ?? null,
        [configurations, configurationId]
    );

    const hasYearStep = (modelLine?.availableYears?.length ?? 0) > 0;
    const showVinStep = hasYearStep ? Boolean(productionYear) : Boolean(configurationId);
    const vinStepNumber = hasYearStep ? 6 : 5;

    // Scroll to the next unanswered step as the user progresses through the wizard.
    useEffect(() => {
        if (!modelLineId) return;

        if (productionYear) {
            scrollToRef(vinStepRef);
            return;
        }
        if (configurationId) {
            // Skip year step when the catalog has no year list for this model.
            if (modelLine?.availableYears?.length > 0) {
                scrollToRef(yearStepRef);
            } else {
                scrollToRef(vinStepRef);
            }
            return;
        }
        if (engine) {
            scrollToRef(transmissionStepRef);
            return;
        }
        if (bodyStyleId) {
            scrollToRef(engineStepRef);
            return;
        }
        scrollToRef(bodyStepRef);
    }, [modelLineId, bodyStyleId, engine, configurationId, productionYear, modelLine]);

    const resetFromModel = (nextModelLineId) => {
        setModelLineId(nextModelLineId);
        setBodyStyleId("");
        setEngine("");
        setConfigurationId("");
        setProductionYear("");
        setVin("");
        setError("");
        setSuccess("");
    };

    const resetFromBody = (nextBodyStyleId) => {
        setBodyStyleId(nextBodyStyleId);
        setEngine("");
        setConfigurationId("");
        setProductionYear("");
        setVin("");
        setError("");
        setSuccess("");
    };

    const resetFromEngine = (nextEngine) => {
        setEngine(nextEngine);
        setConfigurationId("");
        setProductionYear("");
        setVin("");
        setError("");
        setSuccess("");
    };

    const submitVehicle = async () => {
        if (!configurationId || !productionYear || !vin.trim()) {
            setError("Select your car specification, production year, and enter the VIN.");
            return;
        }

        setSubmitting(true);
        setError("");
        setSuccess("");

        try {
            await api.post("/vehicles/me", {
                configurationId,
                productionYear: Number(productionYear),
                vin: vin.trim(),
            });
            setSuccess("Vehicle added to your account.");
            setVin("");
            setModelLineId("");
            setBodyStyleId("");
            setEngine("");
            setConfigurationId("");
            setProductionYear("");
            onVehicleAdded?.();
        } catch (err) {
            const message = err.response?.data?.message ?? "Failed to add vehicle.";
            setError(message);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return <p>Loading Aston Martin catalog…</p>;
    }

    if (!catalog) {
        return <p className="config-error">{error || "Catalog unavailable."}</p>;
    }

    return (
        <div className="vehicle-configurator">
            <div className="config-step">
                <h3>1. Model</h3>
                <div className="option-grid">
                    {catalog.modelLines.map((line) => (
                        <button
                            key={line.id}
                            type="button"
                            className={`option-btn${modelLineId === line.id ? " selected" : ""}`}
                            onClick={() => resetFromModel(line.id)}
                        >
                            {line.name}
                            <br />
                            <small>{line.era}</small>
                        </button>
                    ))}
                </div>
            </div>

            {modelLine && (
                <div className="config-step" ref={bodyStepRef}>
                    <h3>2. Body style</h3>
                    <div className="option-grid">
                        {modelLine.bodyStyles.map((style) => (
                            <button
                                key={style.id}
                                type="button"
                                className={`option-btn${bodyStyleId === style.id ? " selected" : ""}`}
                                onClick={() => resetFromBody(style.id)}
                            >
                                {style.name}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {bodyStyle && (
                <div className="config-step" ref={engineStepRef}>
                    <h3>3. Engine</h3>
                    <div className="option-grid">
                        {engineOptions.map((config) => (
                            <button
                                key={config.engine}
                                type="button"
                                className={`option-btn${engine === config.engine ? " selected" : ""}`}
                                onClick={() => resetFromEngine(config.engine)}
                            >
                                {config.engine}
                                <br />
                                <small>{config.power}</small>
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {engine && (
                <div className="config-step" ref={transmissionStepRef}>
                    <h3>4. Transmission</h3>
                    <div className="option-grid">
                        {transmissionOptions.map((config) => (
                            <button
                                key={config.id}
                                type="button"
                                className={`option-btn${configurationId === config.id ? " selected" : ""}`}
                                onClick={() => {
                                    setConfigurationId(config.id);
                                    setProductionYear("");
                                    setVin("");
                                    setError("");
                                    setSuccess("");
                                }}
                            >
                                {config.transmission}
                                <br />
                                <small>{config.drivetrain}</small>
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {configurationId && hasYearStep && (
                <div className="config-step" ref={yearStepRef}>
                    <h3>5. Production year</h3>
                    <div className="option-grid">
                        {modelLine.availableYears.map((year) => (
                            <button
                                key={year}
                                type="button"
                                className={`option-btn${productionYear === year ? " selected" : ""}`}
                                onClick={() => {
                                    setProductionYear(year);
                                    setError("");
                                    setSuccess("");
                                }}
                            >
                                {year}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {showVinStep && (
                <div className="config-step" ref={vinStepRef}>
                    <h3>{vinStepNumber}. VIN</h3>
                    <input
                        className="vin-field"
                        placeholder="Enter your vehicle identification number"
                        value={vin}
                        onChange={(e) => setVin(e.target.value)}
                    />
                </div>
            )}

            {selectedConfiguration && (
                <div className="config-summary">
                    <p><strong>{selectedConfiguration.displayName}</strong></p>
                    <p>Engine: {selectedConfiguration.engine} ({selectedConfiguration.power})</p>
                    <p>Transmission: {selectedConfiguration.transmission}</p>
                    <p>Drivetrain: {selectedConfiguration.drivetrain}</p>
                    {productionYear && <p>Production year: {productionYear}</p>}
                </div>
            )}

            <button
                type="button"
                className="submit-vehicle-btn"
                disabled={submitting || !configurationId || !productionYear || !vin.trim()}
                onClick={submitVehicle}
            >
                {submitting ? "Adding vehicle…" : "Add vehicle"}
            </button>

            {error && <p className="config-error">{error}</p>}
            {success && <p className="config-success">{success}</p>}
        </div>
    );
}
