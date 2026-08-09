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

/**
 * Guided one-step-at-a-time picker from GET /vehicles/catalog.
 * Model-first (how owners think), then body → engine → transmission → year → VIN.
 * A breadcrumb of choices keeps the path clear; Back undoes a mis-select.
 */
export default function VehicleConfigurator({ onVehicleAdded }) {
    const [catalog, setCatalog] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [step, setStep] = useState(0);
    const [modelQuery, setModelQuery] = useState("");

    const [modelLineId, setModelLineId] = useState("");
    const [bodyStyleId, setBodyStyleId] = useState("");
    const [engine, setEngine] = useState("");
    const [configurationId, setConfigurationId] = useState("");
    const [productionYear, setProductionYear] = useState("");
    const [vin, setVin] = useState("");
    const [vinTaken, setVinTaken] = useState(false);
    const [vinChecking, setVinChecking] = useState(false);

    const actionsRef = useRef(null);

    const scrollToContinue = () => {
        // Let selection styles paint, then bring Continue clearly into view.
        requestAnimationFrame(() => {
            actionsRef.current?.scrollIntoView({
                behavior: "smooth",
                block: "center",
            });
        });
    };

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

    const steps = useMemo(() => {
        const list = ["model", "body", "engine", "transmission"];
        if (hasYearStep) list.push("year");
        list.push("vin");
        return list;
    }, [hasYearStep]);

    const currentStepId = steps[step] ?? "model";
    const stepNumber = step + 1;
    const totalSteps = steps.length;

    // Live uniqueness check so paste/type shows a conflict before submit.
    useEffect(() => {
        const normalized = vin.trim().replace(/\s+/g, "").toUpperCase();
        if (!normalized || currentStepId !== "vin") {
            setVinTaken(false);
            setVinChecking(false);
            return undefined;
        }

        let cancelled = false;
        setVinChecking(true);
        const timer = setTimeout(() => {
            api.get("/vehicles/me/vin-available", {
                params: { vin: normalized },
                skipAuthRedirect: true,
            })
                .then((res) => {
                    if (cancelled) return;
                    const available = res.data?.available !== false;
                    setVinTaken(!available);
                    if (available) {
                        setError((prev) =>
                            prev === "This VIN is already registered to a vehicle" ? "" : prev
                        );
                    }
                })
                .catch(() => {
                    if (!cancelled) setVinTaken(false);
                })
                .finally(() => {
                    if (!cancelled) setVinChecking(false);
                });
        }, 350);

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [vin, currentStepId]);

    const filteredModels = useMemo(() => {
        const lines = catalog?.modelLines ?? [];
        const q = modelQuery.trim().toLowerCase();
        if (!q) return lines;
        return lines.filter(
            (line) =>
                line.name?.toLowerCase().includes(q)
                || line.era?.toLowerCase().includes(q)
        );
    }, [catalog, modelQuery]);

    const trail = useMemo(() => {
        const items = [];
        if (modelLine) items.push({ label: modelLine.name, stepId: "model" });
        if (bodyStyle) items.push({ label: bodyStyle.name, stepId: "body" });
        if (engine) items.push({ label: engine, stepId: "engine" });
        if (selectedConfiguration) {
            items.push({
                label: selectedConfiguration.transmission,
                stepId: "transmission",
            });
        }
        if (productionYear) {
            items.push({ label: String(productionYear), stepId: "year" });
        }
        return items;
    }, [modelLine, bodyStyle, engine, selectedConfiguration, productionYear]);

    const clearAfter = (fromStepId) => {
        if (fromStepId === "model") {
            setBodyStyleId("");
            setEngine("");
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "body") {
            setEngine("");
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "engine") {
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "transmission") {
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "year") {
            setVin("");
        }
        setError("");
        setSuccess("");
    };

    const jumpToStep = (stepId) => {
        const index = steps.indexOf(stepId);
        if (index < 0 || index >= step) return;
        clearAfter(stepId);
        setStep(index);
    };

    const canContinue = () => {
        switch (currentStepId) {
            case "model":
                return Boolean(modelLineId);
            case "body":
                return Boolean(bodyStyleId);
            case "engine":
                return Boolean(engine);
            case "transmission":
                return Boolean(configurationId);
            case "year":
                return Boolean(productionYear);
            case "vin":
                return Boolean(vin.trim()) && !vinTaken && !vinChecking && Boolean(configurationId) && (
                    !hasYearStep || Boolean(productionYear)
                );
            default:
                return false;
        }
    };

    const resolvedProductionYear = () => {
        if (productionYear) return Number(productionYear);
        const years = modelLine?.availableYears;
        if (years?.length) return Number(years[years.length - 1]);
        return null;
    };

    const goBack = () => {
        if (step === 0) return;
        if (currentStepId === "body") setBodyStyleId("");
        if (currentStepId === "engine") setEngine("");
        if (currentStepId === "transmission") setConfigurationId("");
        if (currentStepId === "year") setProductionYear("");
        if (currentStepId === "vin") setVin("");
        clearAfter(steps[step - 1]);
        setStep((s) => Math.max(0, s - 1));
    };

    const goContinue = () => {
        if (!canContinue()) return;
        if (currentStepId === "vin") {
            submitVehicle();
            return;
        }
        setStep((s) => Math.min(totalSteps - 1, s + 1));
        setError("");
        setSuccess("");
    };

    const submitVehicle = async () => {
        const year = resolvedProductionYear();
        const normalizedVin = vin.trim().replace(/\s+/g, "").toUpperCase();
        if (!configurationId || year == null || !normalizedVin) {
            setError("Select your car specification, production year, and enter the VIN.");
            return;
        }
        if (vinTaken) {
            setError("This VIN is already registered to a vehicle");
            return;
        }

        setSubmitting(true);
        setError("");
        setSuccess("");

        try {
            await api.post("/vehicles/me", {
                configurationId,
                productionYear: year,
                vin: normalizedVin,
            });
            setSuccess("Vehicle added to your account.");
            onVehicleAdded?.();
        } catch (err) {
            const message = err.response?.data?.message ?? "Failed to add vehicle.";
            if (err.response?.status === 409) {
                setVinTaken(true);
                setError("");
            } else {
                setError(message);
            }
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

    const titles = {
        model: "Choose your model",
        body: "Choose body style",
        engine: "Choose engine",
        transmission: "Choose transmission",
        year: "Choose production year",
        vin: "Enter VIN",
    };

    return (
        <div className="vehicle-configurator">
            {trail.length > 0 && (
                <div className="config-trail" aria-label="Your selections so far">
                    {trail.map((item, index) => (
                        <span key={`${item.stepId}-${item.label}`} className="config-trail-item">
                            {index > 0 && <span className="config-trail-sep">/</span>}
                            <button
                                type="button"
                                className="config-trail-btn"
                                onClick={() => jumpToStep(item.stepId)}
                            >
                                {item.label}
                            </button>
                        </span>
                    ))}
                </div>
            )}

            <div className="config-step-panel" key={currentStepId}>
                <div className="config-step-meta">
                    <p className="config-step-count">
                        Step {stepNumber} of {totalSteps}
                    </p>
                    <h3>{titles[currentStepId]}</h3>
                </div>

                {currentStepId === "model" && (
                    <>
                        <input
                            className="model-search"
                            type="search"
                            placeholder="Search models (e.g. DB12, Valhalla)"
                            value={modelQuery}
                            onChange={(e) => setModelQuery(e.target.value)}
                        />
                        <div className="option-grid">
                            {filteredModels.length === 0 && (
                                <p>No models match “{modelQuery}”.</p>
                            )}
                            {filteredModels.map((line) => (
                                <button
                                    key={line.id}
                                    type="button"
                                    className={`option-btn${modelLineId === line.id ? " selected" : ""}`}
                                    onClick={() => {
                                        setModelLineId(line.id);
                                        clearAfter("model");
                                        scrollToContinue();
                                    }}
                                >
                                    {line.name}
                                    <br />
                                    <small>{line.era}</small>
                                </button>
                            ))}
                        </div>
                    </>
                )}

                {currentStepId === "body" && modelLine && (
                    <div className="option-grid">
                        {modelLine.bodyStyles.map((style) => (
                            <button
                                key={style.id}
                                type="button"
                                className={`option-btn${bodyStyleId === style.id ? " selected" : ""}`}
                                onClick={() => {
                                    setBodyStyleId(style.id);
                                    clearAfter("body");
                                    scrollToContinue();
                                }}
                            >
                                {style.name}
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "engine" && (
                    <div className="option-grid">
                        {engineOptions.map((config) => (
                            <button
                                key={config.engine}
                                type="button"
                                className={`option-btn${engine === config.engine ? " selected" : ""}`}
                                onClick={() => {
                                    setEngine(config.engine);
                                    clearAfter("engine");
                                    scrollToContinue();
                                }}
                            >
                                {config.engine}
                                <br />
                                <small>{config.power}</small>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "transmission" && (
                    <div className="option-grid">
                        {transmissionOptions.map((config) => (
                            <button
                                key={config.id}
                                type="button"
                                className={`option-btn${configurationId === config.id ? " selected" : ""}`}
                                onClick={() => {
                                    setConfigurationId(config.id);
                                    clearAfter("transmission");
                                    scrollToContinue();
                                }}
                            >
                                {config.transmission}
                                <br />
                                <small>{config.drivetrain}</small>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "year" && modelLine && (
                    <div className="option-grid">
                        {modelLine.availableYears.map((year) => (
                            <button
                                key={year}
                                type="button"
                                className={`option-btn${productionYear === year ? " selected" : ""}`}
                                onClick={() => {
                                    setProductionYear(year);
                                    clearAfter("year");
                                    scrollToContinue();
                                }}
                            >
                                {year}
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "vin" && (
                    <div className="vin-step">
                        {selectedConfiguration && (
                            <div className="config-summary">
                                <p><strong>{selectedConfiguration.displayName}</strong></p>
                                {productionYear && <p>Production year: {productionYear}</p>}
                                <p>
                                    Engine: {selectedConfiguration.engine} ({selectedConfiguration.power})
                                </p>
                                <p>Transmission: {selectedConfiguration.transmission}</p>
                                <p>Drivetrain: {selectedConfiguration.drivetrain}</p>
                            </div>
                        )}
                        <input
                            className={`vin-field${vinTaken ? " vin-field-error" : ""}`}
                            placeholder="Enter your vehicle identification number"
                            value={vin}
                            autoCapitalize="characters"
                            spellCheck={false}
                            onChange={(e) => {
                                const next = e.target.value.replace(/\s+/g, "").toUpperCase();
                                setVin(next);
                                setVinTaken(false);
                                setError("");
                                setSuccess("");
                            }}
                        />
                        {vinChecking && vin.trim() && (
                            <p className="vin-check-status">Checking VIN…</p>
                        )}
                        {vinTaken && (
                            <p className="config-error">This VIN is already registered to a vehicle</p>
                        )}
                    </div>
                )}

                <div className="config-actions" ref={actionsRef}>
                    {step > 0 && (
                        <button
                            type="button"
                            className="config-back-btn"
                            onClick={goBack}
                            disabled={submitting}
                        >
                            Back
                        </button>
                    )}
                    <button
                        type="button"
                        className="submit-vehicle-btn"
                        disabled={submitting || !canContinue()}
                        onClick={goContinue}
                    >
                        {currentStepId === "vin"
                            ? (submitting ? "Adding vehicle…" : "Add vehicle")
                            : "Continue"}
                    </button>
                </div>

                {error && <p className="config-error">{error}</p>}
                {success && <p className="config-success">{success}</p>}
            </div>
        </div>
    );
}
